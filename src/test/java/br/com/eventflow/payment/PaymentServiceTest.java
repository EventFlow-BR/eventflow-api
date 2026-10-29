package br.com.eventflow.payment;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.event.EventStatus;
import br.com.eventflow.payment.dto.PaymentResponse;
import br.com.eventflow.payment.enums.PaymentStatus;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.registration.event.RegistrationConfirmedEvent;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentService =
                new PaymentService(
                        paymentRepository,
                        eventRepository,
                        registrationRepository,
                        applicationEventPublisher
                );
    }

    @Test
    void shouldApprovePaymentAndConfirmRegistration() {
        User participant =
                participant(20L);

        Event event =
                publishedEventWithPrice(
                        10L,
                        new BigDecimal("50.00")
                );

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant,
                        OffsetDateTime.now()
                                .plusMinutes(10)
                );

        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        when(paymentRepository
                .existsByRegistration_RegistrationId(100L))
                .thenReturn(false);

        when(paymentRepository
                .save(any(Payment.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        PaymentResponse response =
                paymentService.processPayment(
                        100L,
                        20L
                );

        ArgumentCaptor<RegistrationConfirmedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        RegistrationConfirmedEvent.class
                );

        verify(applicationEventPublisher)
                .publishEvent(
                        eventCaptor.capture()
                );

        RegistrationConfirmedEvent publishedEvent =
                eventCaptor.getValue();

        assertEquals(
                registration.getRegistrationId(),
                publishedEvent.registrationId()
        );

        assertEquals(
                registration.getEvent().getEventId(),
                publishedEvent.eventId()
        );

        assertEquals(
                registration.getParticipant().getUserId(),
                publishedEvent.participantId()
        );

        assertNotNull(
                publishedEvent.occurredAt()
        );

        assertEquals(
                PaymentStatus.APPROVED,
                response.status()
        );

        assertEquals(
                RegistrationStatus.CONFIRMED,
                registration.getStatus()
        );

        assertEquals(
                new BigDecimal("50.00"),
                response.amount()
        );

        verify(registrationRepository)
                .findByIdForUpdate(100L);

        var locks = inOrder(eventRepository, registrationRepository);
        locks.verify(eventRepository).findByRegistrationIdForUpdate(100L);
        locks.verify(registrationRepository).findByIdForUpdate(100L);

        verify(paymentRepository)
                .save(any(Payment.class));

        verify(registrationRepository)
                .flush();

        verify(paymentRepository)
                .flush();
    }

    @Test
    void shouldReturnNotFoundWhenRegistrationDoesNotExist() {
        when(eventRepository
                .findByRegistrationIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> paymentService.processPayment(
                        999L,
                        20L
                )
        );

        verifyNoInteractions(applicationEventPublisher);

        verify(eventRepository)
                .findByRegistrationIdForUpdate(999L);
        verifyNoInteractions(registrationRepository);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldReturnNotFoundWhenRegistrationBelongsToAnotherParticipant() {
        User owner = participant(20L);

        Event event =
                publishedEventWithPrice(
                        10L,
                        new BigDecimal("50.00")
                );

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        owner,
                        OffsetDateTime.now()
                                .plusMinutes(10)
                );

        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        assertThrows(
                NotFoundException.class,
                () -> paymentService.processPayment(
                        100L,
                        30L
                )
        );

        verifyNoInteractions(applicationEventPublisher);

        verify(registrationRepository)
                .findByIdForUpdate(100L);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectConfirmedRegistration() {
        User participant = participant(20L);

        Event event =
                publishedEventWithPrice(
                        10L,
                        new BigDecimal("50.00")
                );

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant,
                        OffsetDateTime.now()
                                .plusMinutes(10)
                );

        setRegistrationStatus(
                registration,
                RegistrationStatus.CONFIRMED
        );

        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> paymentService.processPayment(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Registration cannot be paid in its current status",
                exception.getMessage()
        );

        verifyNoInteractions(applicationEventPublisher);

        verify(registrationRepository)
                .findByIdForUpdate(100L);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectCancelledRegistration() {
        User participant = participant(20L);

        Event event =
                publishedEventWithPrice(
                        10L,
                        new BigDecimal("50.00")
                );

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant,
                        OffsetDateTime.now()
                                .plusMinutes(10)
                );

        setRegistrationStatus(
                registration,
                RegistrationStatus.CANCELLED
        );

        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> paymentService.processPayment(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Registration cannot be paid in its current status",
                exception.getMessage()
        );

        verifyNoInteractions(applicationEventPublisher);

        verify(registrationRepository)
                .findByIdForUpdate(100L);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectExpiredPendingRegistration() {
        User participant = participant(20L);

        Event event =
                publishedEventWithPrice(
                        10L,
                        new BigDecimal("50.00")
                );

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> paymentService.processPayment(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Registration reservation has expired",
                exception.getMessage()
        );

        verifyNoInteractions(applicationEventPublisher);

        verify(registrationRepository)
                .findByIdForUpdate(100L);

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectWhenPaymentAlreadyExists() {
        User participant = participant(20L);

        Event event =
                publishedEventWithPrice(
                        10L,
                        new BigDecimal("50.00")
                );

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant,
                        OffsetDateTime.now()
                                .plusMinutes(10)
                );

        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        when(paymentRepository
                .existsByRegistration_RegistrationId(100L))
                .thenReturn(true);

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> paymentService.processPayment(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Payment already exists for this registration",
                exception.getMessage()
        );

        verify(registrationRepository)
                .findByIdForUpdate(100L);

        verify(paymentRepository, never())
                .save(any(Payment.class));

        verifyNoInteractions(applicationEventPublisher);

        assertEquals(
                RegistrationStatus.PENDING,
                registration.getStatus()
        );
    }

    @Test
    void shouldRejectExpiredRegistration() {
        User participant = participant(20L);

        Event event =
                publishedEventWithPrice(
                        10L,
                        new BigDecimal("50.00")
                );

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant,
                        OffsetDateTime.now()
                                .plusMinutes(10)
                );

        setRegistrationStatus(
                registration,
                RegistrationStatus.EXPIRED
        );

        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> paymentService.processPayment(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Registration cannot be paid in its current status",
                exception.getMessage()
        );

        verifyNoInteractions(applicationEventPublisher);

        verifyNoInteractions(paymentRepository);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"DRAFT", "CANCELLED", "FINISHED"})
    void shouldRejectPaymentWhenEventIsNotPublished(EventStatus status) {
        Event event = publishedEventWithPrice(10L, new BigDecimal("50.00"));
        ReflectionTestUtils.setField(event, "status", status);
        Registration registration = pendingRegistration(
                100L, event, participant(20L), OffsetDateTime.now().plusMinutes(10)
        );
        stubLockedRegistration(registration);

        ConflictException exception = assertThrows(
                ConflictException.class, () -> paymentService.processPayment(100L, 20L)
        );

        assertEquals("Event is not available for payment", exception.getMessage());
        assertEquals(RegistrationStatus.PENDING, registration.getStatus());
        verifyNoInteractions(paymentRepository, applicationEventPublisher);
    }

    @Test
    void shouldRejectPaymentAfterEventStarted() {
        Event event = new Event(
                organizer(10L), "Started event", "Description", "Petrópolis",
                OffsetDateTime.now().minusDays(1), OffsetDateTime.now().plusDays(1),
                100, new BigDecimal("50.00")
        );
        event.publish(OffsetDateTime.now().minusDays(2));
        Registration registration = pendingRegistration(
                100L, event, participant(20L), OffsetDateTime.now().plusMinutes(10)
        );
        stubLockedRegistration(registration);

        ConflictException exception = assertThrows(
                ConflictException.class, () -> paymentService.processPayment(100L, 20L)
        );

        assertEquals("Event has already started", exception.getMessage());
        assertEquals(RegistrationStatus.PENDING, registration.getStatus());
        verifyNoInteractions(paymentRepository, applicationEventPublisher);
    }

    private void stubLockedRegistration(Registration registration) {
        when(eventRepository.findByRegistrationIdForUpdate(100L))
                .thenReturn(Optional.of(registration.getEvent()));
        when(registrationRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));
    }

    private User participant(Long id) {
        User user = new User(
                "Participant",
                "participant@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserId(user, id);

        return user;
    }

    private Event publishedEventWithPrice(
            Long organizerId,
            BigDecimal price
    ) {
        User organizer = organizer(organizerId);

        Event event = new Event(
                organizer,
                "Java Conference",
                "Description",
                "Petrópolis",
                OffsetDateTime.now()
                        .plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                price
        );

        event.publish(
                OffsetDateTime.now()
                        .minusDays(1)
        );

        setEventId(
                event,
                200L
        );

        return event;
    }

    private Registration pendingRegistration(
            Long registrationId,
            Event event,
            User participant,
            OffsetDateTime reservationExpiresAt
    ) {
        Registration registration =
                new Registration(
                        event,
                        participant,
                        reservationExpiresAt
                );

        setRegistrationId(
                registration,
                registrationId
        );

        return registration;
    }

    private User organizer(Long id) {
        User user = new User(
                "Organizer",
                "organizer@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        setUserId(
                user,
                id
        );

        return user;
    }

    private void setUserId(
            User user,
            Long id
    ) {
        try {
            var field =
                    User.class
                            .getDeclaredField("userId");

            field.setAccessible(true);
            field.set(user, id);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setEventId(
            Event event,
            Long id
    ) {
        try {
            var field =
                    Event.class
                            .getDeclaredField("eventId");

            field.setAccessible(true);
            field.set(event, id);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setRegistrationId(
            Registration registration,
            Long id
    ) {
        try {
            var field =
                    Registration.class
                            .getDeclaredField(
                                    "registrationId"
                            );

            field.setAccessible(true);
            field.set(
                    registration,
                    id
            );

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setRegistrationStatus(
            Registration registration,
            RegistrationStatus status
    ) {
        try {
            var field =
                    Registration.class
                            .getDeclaredField("status");

            field.setAccessible(true);
            field.set(
                    registration,
                    status
            );

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
