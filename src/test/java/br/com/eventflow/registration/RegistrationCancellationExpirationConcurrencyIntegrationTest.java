package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationCancellationExpirationConcurrencyIntegrationTest {

    @Autowired
    private RegistrationCancellationService cancellationService;

    @Autowired
    private RegistrationExpirationService expirationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldKeepConsistentStateWhenCancellationAndExpirationRunConcurrently()
            throws Exception {

        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "Expiration Organizer",
                "cancel-expiration-organizer-" + suffix + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Expiration Participant",
                "cancel-expiration-participant-" + suffix + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        organizer =
                userRepository.saveAndFlush(organizer);

        participant =
                userRepository.saveAndFlush(participant);

        Event event = new Event(
                organizer,
                "Cancellation Expiration Concurrency Event",
                "Concurrency test",
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

        event =
                eventRepository.saveAndFlush(event);

        Registration registration =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now().minusMinutes(1)
                );

        registration =
                registrationRepository
                        .saveAndFlush(registration);

        Long registrationId =
                registration.getRegistrationId();

        Long participantId =
                participant.getUserId();

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<String> cancellationResult =
                    executor.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        try {
                            cancellationService.cancelRegistration(
                                    registrationId,
                                    participantId
                            );

                            return "CANCELLATION_SUCCESS";

                        } catch (ConflictException exception) {
                            return "CANCELLATION_CONFLICT";
                        }
                    });

            Future<String> expirationResult =
                    executor.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        expirationService
                                .expirePendingRegistrations();

                        return "EXPIRATION_COMPLETED";
                    });

            readyLatch.await();
            startLatch.countDown();

            String cancellationOutcome =
                    cancellationResult.get();

            String expirationOutcome =
                    expirationResult.get();

            Registration persistedRegistration =
                    registrationRepository
                            .findById(registrationId)
                            .orElseThrow();

            assertTrue(
                    persistedRegistration.getStatus()
                            == RegistrationStatus.CANCELLED
                            || persistedRegistration.getStatus()
                            == RegistrationStatus.EXPIRED
            );

            if (persistedRegistration.getStatus()
                    == RegistrationStatus.CANCELLED) {

                assertTrue(
                        cancellationOutcome.equals(
                                "CANCELLATION_SUCCESS"
                        )
                );
            }

            if (persistedRegistration.getStatus()
                    == RegistrationStatus.EXPIRED) {

                assertTrue(
                        cancellationOutcome.equals(
                                "CANCELLATION_CONFLICT"
                        )
                );
            }

            assertTrue(
                    expirationOutcome.equals(
                            "EXPIRATION_COMPLETED"
                    )
            );

        } finally {
            executor.shutdownNow();
        }
    }
}