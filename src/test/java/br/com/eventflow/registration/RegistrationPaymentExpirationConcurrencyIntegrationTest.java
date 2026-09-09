package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.payment.PaymentRepository;
import br.com.eventflow.payment.PaymentService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationPaymentExpirationConcurrencyIntegrationTest {

    @Autowired
    private RegistrationExpirationService expirationService;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldNotConfirmRegistrationThatIsBeingExpired()
            throws Exception {

        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "Concurrency Organizer",
                "expiration-payment-organizer-"
                        + suffix
                        + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Concurrency Participant",
                "expiration-payment-participant-"
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
                "Expiration Payment Concurrency Event",
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
                        OffsetDateTime.now()
                                .minusMinutes(1)
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
            Future<String> expirationResult =
                    executor.submit(() -> {
                        readyLatch.countDown();

                        startLatch.await();

                        expirationService
                                .expirePendingRegistrations();

                        return "EXPIRATION_FINISHED";
                    });

            Future<String> paymentResult =
                    executor.submit(() -> {
                        readyLatch.countDown();

                        startLatch.await();

                        try {
                            paymentService.processPayment(
                                    registrationId,
                                    participantId
                            );

                            return "PAYMENT_SUCCESS";

                        } catch (ConflictException exception) {
                            return "PAYMENT_CONFLICT";
                        }
                    });

            readyLatch.await();

            startLatch.countDown();

            expirationResult.get();

            String paymentOutcome =
                    paymentResult.get();

            Registration persistedRegistration =
                    registrationRepository
                            .findById(registrationId)
                            .orElseThrow();

            assertEquals(
                    RegistrationStatus.EXPIRED,
                    persistedRegistration.getStatus()
            );

            assertEquals(
                    "PAYMENT_CONFLICT",
                    paymentOutcome
            );

            assertFalse(
                    paymentRepository
                            .existsByRegistration_RegistrationId(
                                    registrationId
                            )
            );

        } finally {
            executor.shutdownNow();
        }
    }
}