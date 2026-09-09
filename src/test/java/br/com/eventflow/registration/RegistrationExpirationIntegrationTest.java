package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.registration.dto.RegistrationResponse;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationExpirationIntegrationTest {

    @Autowired
    private RegistrationExpirationService expirationService;

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldExpireOnlyPendingRegistrationsPastExpirationTime() {
        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "Expiration Organizer",
                "expiration-organizer-" + suffix + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Expiration Participant",
                "expiration-participant-" + suffix + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        organizer = userRepository.saveAndFlush(organizer);
        participant = userRepository.saveAndFlush(participant);

        Event event = new Event(
                organizer,
                "Expiration Integration Event",
                "Expiration integration test",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                new BigDecimal("50.00")
        );

        event.publish(
                OffsetDateTime.now().minusDays(1)
        );

        event = eventRepository.saveAndFlush(event);

        Registration expiredPending =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        Registration validPending =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .plusMinutes(10)
                );

        Registration confirmed =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        confirmed.confirm();

        Registration cancelled =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        setRegistrationStatus(
                cancelled,
                RegistrationStatus.CANCELLED
        );

        Registration alreadyExpired =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(5)
                );

        setRegistrationStatus(
                alreadyExpired,
                RegistrationStatus.EXPIRED
        );

        expiredPending =
                registrationRepository
                        .saveAndFlush(expiredPending);

        validPending =
                registrationRepository
                        .saveAndFlush(validPending);

        confirmed =
                registrationRepository
                        .saveAndFlush(confirmed);

        cancelled =
                registrationRepository
                        .saveAndFlush(cancelled);

        alreadyExpired =
                registrationRepository
                        .saveAndFlush(alreadyExpired);

        Long expiredPendingId =
                expiredPending.getRegistrationId();

        Long validPendingId =
                validPending.getRegistrationId();

        Long confirmedId =
                confirmed.getRegistrationId();

        Long cancelledId =
                cancelled.getRegistrationId();

        OffsetDateTime previousUpdatedAt =
                expiredPending.getUpdatedAt();

        int expiredCount =
                expirationService
                        .expirePendingRegistrations();

        assertEquals(
                1,
                expiredCount
        );

        long occupiedPlaces =
                registrationRepository
                        .countOccupyingCapacity(
                                event.getEventId(),
                                RegistrationStatus.CONFIRMED,
                                RegistrationStatus.PENDING,
                                OffsetDateTime.now()
                        );

        assertEquals(
                2L,
                occupiedPlaces
        );

        Registration persistedExpired =
                registrationRepository
                        .findById(expiredPendingId)
                        .orElseThrow();

        Registration persistedValid =
                registrationRepository
                        .findById(validPendingId)
                        .orElseThrow();

        Registration persistedConfirmed =
                registrationRepository
                        .findById(confirmedId)
                        .orElseThrow();

        Registration persistedCancelled =
                registrationRepository
                        .findById(cancelledId)
                        .orElseThrow();

        assertEquals(
                RegistrationStatus.EXPIRED,
                persistedExpired.getStatus()
        );

        assertEquals(
                RegistrationStatus.PENDING,
                persistedValid.getStatus()
        );

        assertEquals(
                RegistrationStatus.CONFIRMED,
                persistedConfirmed.getStatus()
        );

        assertEquals(
                RegistrationStatus.CANCELLED,
                persistedCancelled.getStatus()
        );

        assertTrue(
                persistedExpired
                        .getUpdatedAt()
                        .isAfter(previousUpdatedAt)
        );
    }

    @Test
    void shouldAllowNewReservationAfterPreviousRegistrationExpires() {
        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "New Reservation Organizer",
                "new-reservation-organizer-" + suffix + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "New Reservation Participant",
                "new-reservation-participant-" + suffix + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        organizer = userRepository.saveAndFlush(organizer);
        participant = userRepository.saveAndFlush(participant);

        Event event = new Event(
                organizer,
                "New Reservation Event",
                "Allows reservation after expiration",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                new BigDecimal("50.00")
        );

        event.publish(
                OffsetDateTime.now().minusDays(1)
        );

        event = eventRepository.saveAndFlush(event);

        Registration expiredPending =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        expiredPending =
                registrationRepository
                        .saveAndFlush(expiredPending);

        Long expiredRegistrationId =
                expiredPending.getRegistrationId();

        int expiredCount =
                expirationService
                        .expirePendingRegistrations();

        assertEquals(
                1,
                expiredCount
        );

        Registration persistedExpired =
                registrationRepository
                        .findById(expiredRegistrationId)
                        .orElseThrow();

        assertEquals(
                RegistrationStatus.EXPIRED,
                persistedExpired.getStatus()
        );

        RegistrationResponse newReservation =
                registrationService
                        .createReservation(
                                event.getEventId(),
                                participant.getUserId()
                        );

        assertEquals(
                RegistrationStatus.PENDING,
                newReservation.status()
        );

        assertEquals(
                event.getEventId(),
                newReservation.eventId()
        );

        assertEquals(
                participant.getUserId(),
                newReservation.participantId()
        );
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