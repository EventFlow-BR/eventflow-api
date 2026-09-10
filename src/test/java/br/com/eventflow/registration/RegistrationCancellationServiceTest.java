package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.payment.Payment;
import br.com.eventflow.payment.PaymentRepository;
import br.com.eventflow.payment.enums.PaymentStatus;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationCancellationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private PaymentRepository paymentRepository;

    private RegistrationCancellationService cancellationService;

    @BeforeEach
    void setUp() {
        cancellationService =
                new RegistrationCancellationService(
                        registrationRepository,
                        paymentRepository
                );
    }

    @Test
    void shouldCancelPendingRegistrationWithoutRefund() {
        User participant = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant
                );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        cancellationService.cancelRegistration(
                100L,
                20L
        );

        assertEquals(
                RegistrationStatus.CANCELLED,
                registration.getStatus()
        );

        verifyNoInteractions(paymentRepository);

        verify(registrationRepository)
                .flush();
    }

    @Test
    void shouldCancelConfirmedRegistrationAndRefundApprovedPayment() {
        User participant = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant
                );

        registration.confirm();

        Payment payment =
                new Payment(
                        registration,
                        new BigDecimal("50.00")
                );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        when(paymentRepository
                .findByRegistration_RegistrationId(100L))
                .thenReturn(Optional.of(payment));

        cancellationService.cancelRegistration(
                100L,
                20L
        );

        assertEquals(
                RegistrationStatus.CANCELLED,
                registration.getStatus()
        );

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );

        verify(paymentRepository)
                .flush();

        verify(registrationRepository)
                .flush();
    }

    @Test
    void shouldReturnNotFoundWhenRegistrationBelongsToAnotherParticipant() {
        User owner = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        owner
                );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        assertThrows(
                NotFoundException.class,
                () -> cancellationService
                        .cancelRegistration(
                                100L,
                                30L
                        )
        );

        assertEquals(
                RegistrationStatus.PENDING,
                registration.getStatus()
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldReturnNotFoundWhenRegistrationDoesNotExist() {
        when(registrationRepository
                .findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> cancellationService
                        .cancelRegistration(
                                999L,
                                20L
                        )
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectAlreadyCancelledRegistration() {
        User participant = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant
                );

        setRegistrationStatus(
                registration,
                RegistrationStatus.CANCELLED
        );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> cancellationService
                                .cancelRegistration(
                                        100L,
                                        20L
                                )
                );

        assertEquals(
                "Registration cannot be cancelled in its current status",
                exception.getMessage()
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectExpiredRegistration() {
        User participant = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant
                );

        setRegistrationStatus(
                registration,
                RegistrationStatus.EXPIRED
        );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        assertThrows(
                ConflictException.class,
                () -> cancellationService
                        .cancelRegistration(
                                100L,
                                20L
                        )
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectCancellationAfterEventHasStarted() {
        User participant = participant(20L);
        Event event = startedEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant
                );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> cancellationService
                                .cancelRegistration(
                                        100L,
                                        20L
                                )
                );

        assertEquals(
                "Registration cannot be cancelled after the event has started",
                exception.getMessage()
        );

        assertEquals(
                RegistrationStatus.PENDING,
                registration.getStatus()
        );

        verifyNoInteractions(paymentRepository);
    }

    @Test
    void shouldRejectConfirmedRegistrationWithoutPayment() {
        User participant = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant
                );

        registration.confirm();

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        when(paymentRepository
                .findByRegistration_RegistrationId(100L))
                .thenReturn(Optional.empty());

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> cancellationService
                                .cancelRegistration(
                                        100L,
                                        20L
                                )
                );

        assertEquals(
                "Approved payment not found for confirmed registration",
                exception.getMessage()
        );

        assertEquals(
                RegistrationStatus.CONFIRMED,
                registration.getStatus()
        );
    }

    @Test
    void shouldRejectRefundWhenPaymentIsNotApproved() {
        User participant = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                pendingRegistration(
                        100L,
                        event,
                        participant
                );

        registration.confirm();

        Payment payment =
                new Payment(
                        registration,
                        new BigDecimal("50.00")
                );

        setPaymentStatus(
                payment,
                PaymentStatus.REFUNDED
        );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        when(paymentRepository
                .findByRegistration_RegistrationId(100L))
                .thenReturn(Optional.of(payment));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> cancellationService
                                .cancelRegistration(
                                        100L,
                                        20L
                                )
                );

        assertEquals(
                "Payment cannot be refunded in its current status",
                exception.getMessage()
        );

        assertEquals(
                RegistrationStatus.CONFIRMED,
                registration.getStatus()
        );

        assertEquals(
                PaymentStatus.REFUNDED,
                payment.getStatus()
        );
    }

    @Test
    void shouldRejectCancellationWhenPendingReservationHasExpired() {
        User participant = participant(20L);
        Event event = futureEvent(10L);

        Registration registration =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        setRegistrationId(
                registration,
                100L
        );

        when(registrationRepository
                .findByIdForUpdate(100L))
                .thenReturn(Optional.of(registration));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> cancellationService
                                .cancelRegistration(
                                        100L,
                                        20L
                                )
                );

        assertEquals(
                "Registration reservation has expired",
                exception.getMessage()
        );

        assertEquals(
                RegistrationStatus.PENDING,
                registration.getStatus()
        );

        verifyNoInteractions(paymentRepository);
    }


    private void validateCancellation(
            Registration registration,
            OffsetDateTime now
    ) {
        if (!now.isBefore(
                registration
                        .getEvent()
                        .getStartDate()
        )) {

            throw new ConflictException(
                    "Registration cannot be cancelled after the event has started"
            );
        }

        if (registration.getStatus() == RegistrationStatus.PENDING
                && !registration
                .getReservationExpiresAt()
                .isAfter(now)) {

            throw new ConflictException(
                    "Registration reservation has expired"
            );
        }

        if (registration.getStatus()
                != RegistrationStatus.PENDING
                && registration.getStatus()
                != RegistrationStatus.CONFIRMED) {

            throw new ConflictException(
                    "Registration cannot be cancelled in its current status"
            );
        }
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

    private User organizer(Long id) {
        User user = new User(
                "Organizer",
                "organizer@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        setUserId(user, id);

        return user;
    }

    private Event futureEvent(Long organizerId) {
        return new Event(
                organizer(organizerId),
                "Java Conference",
                "Description",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                new BigDecimal("50.00")
        );
    }

    private Event startedEvent(Long organizerId) {
        return new Event(
                organizer(organizerId),
                "Started Event",
                "Description",
                "Petrópolis",
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(5),
                100,
                new BigDecimal("50.00")
        );
    }

    private Registration pendingRegistration(
            Long registrationId,
            Event event,
            User participant
    ) {
        Registration registration =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .plusMinutes(15)
                );

        setRegistrationId(
                registration,
                registrationId
        );

        return registration;
    }

    private void setUserId(
            User user,
            Long id
    ) {
        try {
            var field =
                    User.class.getDeclaredField("userId");

            field.setAccessible(true);
            field.set(user, id);

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
                            .getDeclaredField("registrationId");

            field.setAccessible(true);
            field.set(registration, id);

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
            field.set(registration, status);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setPaymentStatus(
            Payment payment,
            PaymentStatus status
    ) {
        try {
            var field =
                    Payment.class
                            .getDeclaredField("status");

            field.setAccessible(true);
            field.set(payment, status);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}