package br.com.eventflow.payment;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.payment.enums.PaymentStatus;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
class PaymentConcurrencyIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldAllowOnlyOneConcurrentPaymentForSameRegistration()
            throws Exception {

        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "Payment Concurrency Organizer",
                "payment-concurrency-organizer-"
                        + suffix
                        + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Payment Concurrency Participant",
                "payment-concurrency-participant-"
                        + suffix
                        + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        organizer =
                userRepository.saveAndFlush(organizer);

        participant =
                userRepository.saveAndFlush(participant);

        Event event = new Event(
                organizer,
                "Payment Concurrency Event",
                "Concurrent payment test",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                new BigDecimal("75.00")
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
                        OffsetDateTime.now()
                                .plusMinutes(15)
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
            Future<String> firstResult =
                    executor.submit(() -> {
                        readyLatch.countDown();

                        startLatch.await();

                        return tryProcessPayment(
                                registrationId,
                                participantId
                        );
                    });

            Future<String> secondResult =
                    executor.submit(() -> {
                        readyLatch.countDown();

                        startLatch.await();

                        return tryProcessPayment(
                                registrationId,
                                participantId
                        );
                    });

            readyLatch.await();

            startLatch.countDown();

            List<String> results =
                    List.of(
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

            assertEquals(
                    1L,
                    successCount
            );

            assertEquals(
                    1L,
                    conflictCount
            );

            Registration persistedRegistration =
                    registrationRepository
                            .findById(registrationId)
                            .orElseThrow();

            Payment persistedPayment =
                    paymentRepository
                            .findByRegistration_RegistrationId(
                                    registrationId
                            )
                            .orElseThrow();

            assertEquals(
                    RegistrationStatus.CONFIRMED,
                    persistedRegistration.getStatus()
            );

            assertEquals(
                    PaymentStatus.APPROVED,
                    persistedPayment.getStatus()
            );

            assertEquals(
                    registrationId,
                    persistedPayment
                            .getRegistration()
                            .getRegistrationId()
            );

            assertEquals(
                    new BigDecimal("75.00"),
                    persistedPayment.getAmount()
            );

        } finally {
            executor.shutdownNow();
        }
    }

    private String tryProcessPayment(
            Long registrationId,
            Long participantId
    ) {
        try {
            paymentService.processPayment(
                    registrationId,
                    participantId
            );

            return "SUCCESS";

        } catch (ConflictException exception) {
            return "CONFLICT";
        }
    }
}