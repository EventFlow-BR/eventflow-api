package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.registration.event.RegistrationExpiredEvent;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationExpirationServiceTest {

    @Mock
    private RegistrationRepository registrationRepository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private RegistrationExpirationService expirationService;

    @BeforeEach
    void setUp() {
        expirationService =
                new RegistrationExpirationService(
                        registrationRepository,
                        applicationEventPublisher
                );
    }

    @Test
    void shouldExpirePendingRegistrationsPastExpirationTime() {
        Registration first =
                expiredPendingRegistration(
                        100L,
                        200L,
                        20L
                );

        Registration second =
                expiredPendingRegistration(
                        101L,
                        201L,
                        21L
                );

        when(registrationRepository
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                ))
                .thenReturn(
                        List.of(first, second)
                );

        int expiredCount =
                expirationService
                        .expirePendingRegistrations();

        assertEquals(
                2,
                expiredCount
        );

        assertEquals(
                RegistrationStatus.EXPIRED,
                first.getStatus()
        );

        assertEquals(
                RegistrationStatus.EXPIRED,
                second.getStatus()
        );

        verify(registrationRepository)
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                );

        ArgumentCaptor<RegistrationExpiredEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        RegistrationExpiredEvent.class
                );

        verify(
                applicationEventPublisher,
                times(2)
        ).publishEvent(
                eventCaptor.capture()
        );

        List<RegistrationExpiredEvent> publishedEvents =
                eventCaptor.getAllValues();

        assertEquals(
                2,
                publishedEvents.size()
        );

        assertEquals(
                first.getRegistrationId(),
                publishedEvents.get(0).registrationId()
        );

        assertEquals(
                first.getEvent().getEventId(),
                publishedEvents.get(0).eventId()
        );

        assertEquals(
                first.getParticipant().getUserId(),
                publishedEvents.get(0).participantId()
        );

        assertNotNull(
                publishedEvents.get(0).occurredAt()
        );

        assertEquals(
                second.getRegistrationId(),
                publishedEvents.get(1).registrationId()
        );

        assertEquals(
                second.getEvent().getEventId(),
                publishedEvents.get(1).eventId()
        );

        assertEquals(
                second.getParticipant().getUserId(),
                publishedEvents.get(1).participantId()
        );

        assertNotNull(
                publishedEvents.get(1).occurredAt()
        );
    }

    @Test
    void shouldDoNothingWhenThereAreNoExpiredPendingRegistrations() {
        when(registrationRepository
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                ))
                .thenReturn(List.of());

        int expiredCount =
                expirationService
                        .expirePendingRegistrations();

        assertEquals(
                0,
                expiredCount
        );

        verify(registrationRepository)
                .findExpiredPendingRegistrations(
                        eq(RegistrationStatus.PENDING),
                        any(OffsetDateTime.class)
                );

        verifyNoInteractions(
                applicationEventPublisher
        );
    }

    private Registration expiredPendingRegistration(
            Long registrationId,
            Long eventId,
            Long participantId
    ) {
        User organizer = new User(
                "Organizer",
                "organizer-" + eventId + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Participant",
                "participant-" + participantId + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserId(
                participant,
                participantId
        );

        Event event = new Event(
                organizer,
                "Event",
                "Description",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                new BigDecimal("50.00")
        );

        setEventId(
                event,
                eventId
        );

        Registration registration =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
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
                            .getDeclaredField("registrationId");

            field.setAccessible(true);
            field.set(registration, id);

        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}