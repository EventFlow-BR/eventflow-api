package br.com.eventflow.registration.messaging;

import br.com.eventflow.registration.event.RegistrationConfirmedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationConfirmedEventListener {

    private static final String REGISTRATION_CONFIRMED_ROUTING_KEY =
            "registration.confirmed";

    private final RegistrationEventPublisher registrationEventPublisher;

    public RegistrationConfirmedEventListener(
            RegistrationEventPublisher registrationEventPublisher
    ) {
        this.registrationEventPublisher = registrationEventPublisher;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            RegistrationConfirmedEvent event
    ) {
        RegistrationMessage message =
                new RegistrationMessage(
                        event.registrationId(),
                        event.eventId(),
                        event.participantId(),
                        "registration.confirmed",
                        event.occurredAt()
                );

        registrationEventPublisher.publish(
                message,
                REGISTRATION_CONFIRMED_ROUTING_KEY
        );
    }
}
