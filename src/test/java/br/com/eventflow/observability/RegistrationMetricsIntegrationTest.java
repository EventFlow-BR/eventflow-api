package br.com.eventflow.observability;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.payment.PaymentService;
import br.com.eventflow.registration.*;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationMetricsIntegrationTest
        extends AbstractIntegrationTest {

    private static final String RESERVED_METRIC =
            "eventflow.registration.reserved";

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private RegistrationCancellationService cancellationService;

    @Autowired
    private RegistrationExpirationService expirationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    @Qualifier("transactionManager")
    private PlatformTransactionManager transactionManager;

    @Test
    void shouldIncrementReservedMetricAfterSuccessfulCommit() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        Counter counter =
                meterRegistry.get(
                        RESERVED_METRIC
                ).counter();

        double before =
                counter.count();

        registrationService.createReservation(
                event.getEventId(),
                participant.getUserId()
        );

        double after =
                counter.count();

        assertEquals(
                before + 1,
                after
        );
    }

    @Test
    void shouldNotIncrementReservedMetricWhenTransactionRollsBack() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        Counter counter =
                meterRegistry.get(
                        RESERVED_METRIC
                ).counter();

        double before =
                counter.count();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        assertThrows(
                ForcedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(
                        status -> {
                            registrationService.createReservation(
                                    event.getEventId(),
                                    participant.getUserId()
                            );

                            throw new ForcedRollbackException();
                        }
                )
        );

        double after =
                counter.count();

        assertEquals(
                before,
                after
        );
    }

    @Test
    void shouldIncrementConfirmedMetricAfterSuccessfulCommit() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        var registration =
                registrationService.createReservation(
                        event.getEventId(),
                        participant.getUserId()
                );

        Counter counter =
                meterRegistry.get(
                        "eventflow.registration.confirmed"
                ).counter();

        double before =
                counter.count();

        paymentService.processPayment(
                registration.id(),
                participant.getUserId()
        );

        double after =
                counter.count();

        assertEquals(
                before + 1,
                after
        );
    }

    @Test
    void shouldNotIncrementConfirmedMetricWhenTransactionRollsBack() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        var registration =
                registrationService.createReservation(
                        event.getEventId(),
                        participant.getUserId()
                );

        Counter counter =
                meterRegistry.get(
                        "eventflow.registration.confirmed"
                ).counter();

        double before =
                counter.count();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        assertThrows(
                ForcedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(
                        status -> {
                            paymentService.processPayment(
                                    registration.id(),
                                    participant.getUserId()
                            );

                            throw new ForcedRollbackException();
                        }
                )
        );

        double after =
                counter.count();

        assertEquals(
                before,
                after
        );
    }

    @Test
    void shouldIncrementCancelledMetricAfterSuccessfulCommit() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        var registration =
                registrationService.createReservation(
                        event.getEventId(),
                        participant.getUserId()
                );

        Counter counter =
                meterRegistry.get(
                        "eventflow.registration.cancelled"
                ).counter();

        double before =
                counter.count();

        cancellationService.cancelRegistration(
                registration.id(),
                participant.getUserId()
        );

        double after =
                counter.count();

        assertEquals(
                before + 1,
                after
        );
    }

    @Test
    void shouldNotIncrementCancelledMetricWhenTransactionRollsBack() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        var registration =
                registrationService.createReservation(
                        event.getEventId(),
                        participant.getUserId()
                );

        Counter counter =
                meterRegistry.get(
                        "eventflow.registration.cancelled"
                ).counter();

        double before =
                counter.count();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        assertThrows(
                ForcedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(
                        status -> {
                            cancellationService.cancelRegistration(
                                    registration.id(),
                                    participant.getUserId()
                            );

                            throw new ForcedRollbackException();
                        }
                )
        );

        assertEquals(
                before,
                counter.count()
        );
    }

    @Test
    void shouldIncrementExpiredMetricAfterSuccessfulCommit() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        createExpiredPendingRegistration(
                participant,
                event
        );

        Counter counter =
                meterRegistry.get(
                        "eventflow.registration.expired"
                ).counter();

        double before =
                counter.count();

        int expiredCount =
                expirationService
                        .expirePendingRegistrations();

        double after =
                counter.count();

        assertTrue(
                expiredCount >= 1
        );

        assertEquals(
                before + expiredCount,
                after
        );
    }

    @Test
    void shouldNotIncrementExpiredMetricWhenTransactionRollsBack() {
        User participant =
                createParticipant();

        Event event =
                createPublishedEvent();

        createExpiredPendingRegistration(
                participant,
                event
        );

        Counter counter =
                meterRegistry.get(
                        "eventflow.registration.expired"
                ).counter();

        double before =
                counter.count();

        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        assertThrows(
                ForcedRollbackException.class,
                () -> transactionTemplate.executeWithoutResult(
                        status -> {
                            expirationService
                                    .expirePendingRegistrations();

                            throw new ForcedRollbackException();
                        }
                )
        );

        assertEquals(
                before,
                counter.count()
        );
    }

    private User createParticipant() {
        return userRepository.save(
                new User(
                        "Participant",
                        uniqueEmail("participant"),
                        "encoded-password",
                        UserRole.PARTICIPANT
                )
        );
    }

    private Event createPublishedEvent() {
        User organizer =
                userRepository.save(
                        new User(
                                "Organizer",
                                uniqueEmail("organizer"),
                                "encoded-password",
                                UserRole.ORGANIZER
                        )
                );

        Event event =
                new Event(
                        organizer,
                        "Observability Event",
                        "Event used for metrics integration testing",
                        "Petrópolis",
                        OffsetDateTime.now()
                                .plusDays(7),
                        OffsetDateTime.now()
                                .plusDays(7)
                                .plusHours(8),
                        100,
                        new BigDecimal("50.00")
                );

        event.publish(
                OffsetDateTime.now()
        );

        return eventRepository.save(
                event
        );
    }

    private String uniqueEmail(
            String prefix
    ) {
        return prefix
                + "-"
                + UUID.randomUUID()
                + "@example.com";
    }

    private static class ForcedRollbackException
            extends RuntimeException {
    }

    private Registration createExpiredPendingRegistration(
            User participant,
            Event event
    ) {
        Registration registration =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        return registrationRepository
                .saveAndFlush(registration);
    }
}