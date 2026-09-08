package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.event.EventStatus;
import br.com.eventflow.registration.dto.RegistrationResponse;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.ForbiddenException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private EventRepository eventRepository;

    @Mock
    private UserRepository userRepository;

    private RegistrationService registrationService;

    @BeforeEach
    void setUp() {
        registrationService =
                new RegistrationService(
                        registrationRepository,
                        eventRepository,
                        userRepository,
                        15
                );
    }

    @Test
    void shouldCreatePendingReservationForParticipant() {
        User participant = participant(20L);
        Event event = publishedFutureEvent(10L, 100);

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(participant));

        when(registrationRepository
                .findFirstByParticipant_UserIdAndEvent_EventIdAndStatusIn(
                        eq(20L),
                        eq(100L),
                        anyList()
                ))
                .thenReturn(Optional.empty());

        when(registrationRepository.countOccupyingCapacity(
                eq(100L),
                eq(RegistrationStatus.CONFIRMED),
                eq(RegistrationStatus.PENDING),
                any(OffsetDateTime.class)
        )).thenReturn(99L);

        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        RegistrationResponse response =
                registrationService.createReservation(
                        100L,
                        20L
                );

        assertEquals(
                RegistrationStatus.PENDING,
                response.status()
        );

        assertEquals(100L, response.eventId());
        assertEquals(20L, response.participantId());
        assertNotNull(response.reservationExpiresAt());

        verify(eventRepository)
                .findByIdForUpdate(100L);

        verify(registrationRepository)
                .save(any(Registration.class));
    }

    @Test
    void shouldReturnNotFoundWhenEventDoesNotExist() {
        when(eventRepository.findByIdForUpdate(999L))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> registrationService.createReservation(
                        999L,
                        20L
                )
        );

        verifyNoInteractions(userRepository);
        verifyNoInteractions(registrationRepository);
    }

    @Test
    void shouldRejectRegistrationForDraftEvent() {
        Event event = draftFutureEvent(10L, 100);

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> registrationService.createReservation(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Event is not available for registration",
                exception.getMessage()
        );

        verifyNoInteractions(userRepository);
    }

    @Test
    void shouldRejectRegistrationForStartedEvent() {
        Event event = startedPublishedEvent(10L, 100);

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> registrationService.createReservation(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Event has already started",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectWhenAuthenticatedParticipantNoLongerExists() {
        Event event = publishedFutureEvent(10L, 100);

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        when(userRepository.findById(20L))
                .thenReturn(Optional.empty());

        assertThrows(
                UnauthorizedException.class,
                () -> registrationService.createReservation(
                        100L,
                        20L
                )
        );
    }

    @Test
    void shouldRejectOrganizerCreatingRegistration() {
        Event event = publishedFutureEvent(10L, 100);
        User organizer = organizer(20L);

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(organizer));

        assertThrows(
                ForbiddenException.class,
                () -> registrationService.createReservation(
                        100L,
                        20L
                )
        );
    }

    @Test
    void shouldRejectConfirmedDuplicateRegistration() {
        User participant = participant(20L);
        Event event = publishedFutureEvent(10L, 100);

        Registration existing =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now().plusMinutes(10)
                );

        setRegistrationStatus(
                existing,
                RegistrationStatus.CONFIRMED
        );

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(participant));

        when(registrationRepository
                .findFirstByParticipant_UserIdAndEvent_EventIdAndStatusIn(
                        eq(20L),
                        eq(100L),
                        anyList()
                ))
                .thenReturn(Optional.of(existing));

        assertThrows(
                ConflictException.class,
                () -> registrationService.createReservation(
                        100L,
                        20L
                )
        );

        verify(registrationRepository, never())
                .save(any());
    }

    @Test
    void shouldReplaceExpiredPendingReservation() {
        User participant = participant(20L);
        Event event = publishedFutureEvent(10L, 100);

        Registration expired =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now().minusMinutes(1)
                );

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(participant));

        when(registrationRepository
                .findFirstByParticipant_UserIdAndEvent_EventIdAndStatusIn(
                        eq(20L),
                        eq(100L),
                        anyList()
                ))
                .thenReturn(Optional.of(expired));

        when(registrationRepository.countOccupyingCapacity(
                eq(100L),
                eq(RegistrationStatus.CONFIRMED),
                eq(RegistrationStatus.PENDING),
                any(OffsetDateTime.class)
        )).thenReturn(50L);

        when(registrationRepository.save(any(Registration.class)))
                .thenAnswer(invocation ->
                        invocation.getArgument(0)
                );

        RegistrationResponse response =
                registrationService.createReservation(
                        100L,
                        20L
                );

        assertEquals(
                RegistrationStatus.CANCELLED,
                expired.getStatus()
        );

        assertEquals(
                RegistrationStatus.PENDING,
                response.status()
        );

        verify(registrationRepository).flush();
        verify(registrationRepository)
                .save(any(Registration.class));
    }

    @Test
    void shouldRejectReservationWhenEventIsFull() {
        User participant = participant(20L);
        Event event = publishedFutureEvent(10L, 100);

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(participant));

        when(registrationRepository
                .findFirstByParticipant_UserIdAndEvent_EventIdAndStatusIn(
                        eq(20L),
                        eq(100L),
                        anyList()
                ))
                .thenReturn(Optional.empty());

        when(registrationRepository.countOccupyingCapacity(
                eq(100L),
                eq(RegistrationStatus.CONFIRMED),
                eq(RegistrationStatus.PENDING),
                any(OffsetDateTime.class)
        )).thenReturn(100L);

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> registrationService.createReservation(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Event has no available capacity",
                exception.getMessage()
        );

        verify(registrationRepository, never())
                .save(any());
    }

    @Test
    void shouldRejectActivePendingDuplicateRegistration() {
        User participant = participant(20L);
        Event event = publishedFutureEvent(10L, 100);

        Registration existing =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now().plusMinutes(10)
                );

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        when(userRepository.findById(20L))
                .thenReturn(Optional.of(participant));

        when(registrationRepository
                .findFirstByParticipant_UserIdAndEvent_EventIdAndStatusIn(
                        eq(20L),
                        eq(100L),
                        anyList()
                ))
                .thenReturn(Optional.of(existing));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> registrationService.createReservation(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Participant already has an active registration",
                exception.getMessage()
        );

        verify(registrationRepository, never())
                .save(any());

        verify(registrationRepository, never())
                .flush();
    }

    @Test
    void shouldRejectRegistrationForCancelledEvent() {
        Event event = publishedFutureEvent(10L, 100);

        setEventStatus(
                event,
                EventStatus.CANCELLED
        );

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> registrationService.createReservation(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Event is not available for registration",
                exception.getMessage()
        );
    }

    @Test
    void shouldRejectRegistrationForFinishedEvent() {
        Event event = publishedFutureEvent(10L, 100);

        setEventStatus(
                event,
                EventStatus.FINISHED
        );

        when(eventRepository.findByIdForUpdate(100L))
                .thenReturn(Optional.of(event));

        ConflictException exception =
                assertThrows(
                        ConflictException.class,
                        () -> registrationService.createReservation(
                                100L,
                                20L
                        )
                );

        assertEquals(
                "Event is not available for registration",
                exception.getMessage()
        );
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

    private Event publishedFutureEvent(
            Long organizerId,
            int capacity
    ) {
        Event event = new Event(
                organizer(organizerId),
                "Java Conference",
                "Description",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                capacity,
                new BigDecimal("50.00")
        );

        setEventId(event, 100L);

        event.publish(
                OffsetDateTime.now().minusDays(1)
        );

        return event;
    }

    private Event draftFutureEvent(
            Long organizerId,
            int capacity
    ) {
        return new Event(
                organizer(organizerId),
                "Java Conference",
                "Description",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(7).plusHours(8),
                capacity,
                new BigDecimal("50.00")
        );
    }

    private Event startedPublishedEvent(
            Long organizerId,
            int capacity
    ) {
        Event event = new Event(
                organizer(organizerId),
                "Java Conference",
                "Description",
                "Petrópolis",
                OffsetDateTime.now().minusHours(1),
                OffsetDateTime.now().plusHours(5),
                capacity,
                new BigDecimal("50.00")
        );

        event.publish(
                OffsetDateTime.now().minusDays(1)
        );

        return event;
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

    private void setEventId(
            Event event,
            Long eventId
    ) {
        try {
            var field =
                    Event.class.getDeclaredField("eventId");

            field.setAccessible(true);
            field.set(event, eventId);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }

    private void setEventStatus(
            Event event,
            EventStatus status
    ) {
        try {
            var field =
                    Event.class.getDeclaredField("status");

            field.setAccessible(true);
            field.set(event, status);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
