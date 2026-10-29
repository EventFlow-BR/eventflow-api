package br.com.eventflow.registration.messaging;

import br.com.eventflow.registration.event.RegistrationExpiredEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.UUID;

@Component
public class RegistrationExpiredEventListener {

    private static final String REGISTRATION_EXPIRED_ROUTING_KEY =
            "registration.expired";

    private final RegistrationEventPublisher registrationEventPublisher;

    public RegistrationExpiredEventListener(
            RegistrationEventPublisher registrationEventPublisher
    ) {
        this.registrationEventPublisher = registrationEventPublisher;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            RegistrationExpiredEvent event
    ) {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        event.registrationId(),
                        event.eventId(),
                        event.participantId(),
                        "registration.expired",
                        event.occurredAt()
                );

        registrationEventPublisher.publish(
                message,
                REGISTRATION_EXPIRED_ROUTING_KEY
        );
    }
}
