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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationConcurrencyIntegrationTest {

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldAllowOnlyOneParticipantToReserveLastAvailablePlace()
            throws Exception {

        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "Concurrency Organizer",
                "organizer-" + suffix + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participantOne = new User(
                "Participant One",
                "participant-one-" + suffix + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        User participantTwo = new User(
                "Participant Two",
                "participant-two-" + suffix + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        organizer = userRepository.saveAndFlush(organizer);
        participantOne =
                userRepository.saveAndFlush(participantOne);
        participantTwo =
                userRepository.saveAndFlush(participantTwo);

        Event event = new Event(
                organizer,
                "Concurrency Test Event",
                "Event with only one available place",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                1,
                new BigDecimal("50.00")
        );

        event.publish(
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS)
        );

        event = eventRepository.saveAndFlush(event);

        Long eventId = event.getEventId();
        Long participantOneId =
                participantOne.getUserId();
        Long participantTwoId =
                participantTwo.getUserId();

        CountDownLatch readyLatch =
                new CountDownLatch(2);

        CountDownLatch startLatch =
                new CountDownLatch(1);

        ExecutorService executor =
                Executors.newFixedThreadPool(2);

        try {
            Future<String> firstResult =
                    executor.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return tryCreateReservation(
                                eventId,
                                participantOneId
                        );
                    });

            Future<String> secondResult =
                    executor.submit(() -> {
                        readyLatch.countDown();
                        startLatch.await();

                        return tryCreateReservation(
                                eventId,
                                participantTwoId
                        );
                    });

            readyLatch.await();

            startLatch.countDown();

            List<String> results = List.of(
                    firstResult.get(),
                    secondResult.get()
            );

            long successCount =
                    results.stream()
                            .filter("SUCCESS"::equals)
                            .count();

            long conflictCount =
                    results.stream()
                            .filter("CONFLICT"::equals)
                            .count();

            assertEquals(1, successCount);
            assertEquals(1, conflictCount);

            long occupiedPlaces =
                    registrationRepository
                            .countOccupyingCapacity(
                                    eventId,
                                    RegistrationStatus.CONFIRMED,
                                    RegistrationStatus.PENDING,
                                    OffsetDateTime.now()
                                            .truncatedTo(
                                                    ChronoUnit.MICROS
                                            )
                            );

            assertEquals(1L, occupiedPlaces);

        } finally {
            executor.shutdownNow();
        }
    }

    private String tryCreateReservation(
            Long eventId,
            Long participantId
    ) {
        try {
            registrationService.createReservation(
                    eventId,
                    participantId
            );

            return "SUCCESS";

        } catch (ConflictException exception) {
            return "CONFLICT";
        }
    }
}