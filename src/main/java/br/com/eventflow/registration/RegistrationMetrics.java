package br.com.eventflow.registration;

import br.com.eventflow.registration.event.RegistrationCancelledEvent;
import br.com.eventflow.registration.event.RegistrationConfirmedEvent;
import br.com.eventflow.registration.event.RegistrationExpiredEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationMetrics {

    private final Counter createdCounter;
    private final Counter confirmedCounter;
    private final Counter cancelledCounter;
    private final Counter expiredCounter;

    public RegistrationMetrics(
            MeterRegistry meterRegistry
    ) {
        this.createdCounter =
                Counter.builder(
                                "eventflow.registration.reserved"
                        )
                        .description(
                                "Number of successfully reserved registrations"
                        )
                        .register(meterRegistry);

        this.confirmedCounter =
                Counter.builder(
                                "eventflow.registration.confirmed"
                        )
                        .description(
                                "Number of successfully confirmed registrations"
                        )
                        .register(meterRegistry);

        this.cancelledCounter =
                Counter.builder(
                                "eventflow.registration.cancelled"
                        )
                        .description(
                                "Number of successfully cancelled registrations"
                        )
                        .register(meterRegistry);

        this.expiredCounter =
                Counter.builder(
                                "eventflow.registration.expired"
                        )
                        .description(
                                "Number of successfully expired registrations"
                        )
                        .register(meterRegistry);
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onRegistrationCreated(
            RegistrationCreatedEvent event
    ) {
        createdCounter.increment();
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onRegistrationConfirmed(
            RegistrationConfirmedEvent event
    ) {
        confirmedCounter.increment();
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onRegistrationCancelled(
            RegistrationCancelledEvent event
    ) {
        cancelledCounter.increment();
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onRegistrationExpired(
            RegistrationExpiredEvent event
    ) {
        expiredCounter.increment();
    }
}