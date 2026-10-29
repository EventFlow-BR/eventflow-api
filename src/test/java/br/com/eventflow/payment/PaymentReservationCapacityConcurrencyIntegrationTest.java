package br.com.eventflow.payment;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.registration.RegistrationService;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class PaymentReservationCapacityConcurrencyIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private RegistrationService registrationService;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldNotReuseCapacityWhileValidPaymentIsWaitingToCommit() throws Exception {
        User organizer = user(UserRole.ORGANIZER);
        User payer = user(UserRole.PARTICIPANT);
        User competitor = user(UserRole.PARTICIPANT);
        Event event = new Event(
                organizer, "Last place", "Payment crossing expiration", "Petrópolis",
                OffsetDateTime.now().plusDays(7), OffsetDateTime.now().plusDays(8),
                1, new BigDecimal("50.00")
        );
        event.publish(OffsetDateTime.now());
        event = eventRepository.saveAndFlush(event);
        Long eventId = event.getEventId();
        OffsetDateTime expiresAt = OffsetDateTime.now().plusSeconds(10);
        Registration registration = registrationRepository.saveAndFlush(
                new Registration(event, payer, expiresAt)
        );
        Long registrationId = registration.getRegistrationId();

        CountDownLatch paymentProcessed = new CountDownLatch(1);
        CountDownLatch allowPaymentCommit = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var payment = executor.submit(() -> {
                new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                    paymentService.processPayment(registrationId, payer.getUserId());
                    paymentProcessed.countDown();
                    try {
                        assertTrue(allowPaymentCommit.await(30, TimeUnit.SECONDS));
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(exception);
                    }
                });
            });
            assertTrue(paymentProcessed.await(15, TimeUnit.SECONDS),
                    "Payment must be validated before its reservation expires");

            // Wait for the actual deadline, not an assumed database/worker execution speed.
            long waitMillis = Duration.between(
                    OffsetDateTime.now(), expiresAt.plusSeconds(1)
            ).toMillis();
            if (waitMillis > 0) {
                Thread.sleep(waitMillis);
            }

            var newReservation = executor.submit(() -> {
                try {
                    registrationService.createReservation(eventId, competitor.getUserId());
                    return "SUCCESS";
                } catch (ConflictException exception) {
                    return "CONFLICT";
                }
            });

            String outcome = null;
            try {
                outcome = newReservation.get(1, TimeUnit.SECONDS);
            } catch (TimeoutException expectedWhileEventIsLocked) {
                // The competing reservation can continue only after the payment commits.
            } finally {
                allowPaymentCommit.countDown();
            }
            payment.get(10, TimeUnit.SECONDS);
            if (outcome == null) {
                outcome = newReservation.get(10, TimeUnit.SECONDS);
            }

            assertEquals("CONFLICT", outcome);
            assertEquals(1, registrationRepository.countOccupyingCapacity(
                    eventId, RegistrationStatus.CONFIRMED,
                    RegistrationStatus.PENDING, OffsetDateTime.now()
            ));
            assertEquals(RegistrationStatus.CONFIRMED,
                    registrationRepository.findById(registrationId).orElseThrow().getStatus());
        } finally {
            allowPaymentCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private User user(UserRole role) {
        return userRepository.saveAndFlush(new User(
                "Capacity test", UUID.randomUUID() + "@example.com", "encoded-password", role
        ));
    }
}
