package br.com.eventflow.registration.messaging;

import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.event.RegistrationCancelledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class RegistrationCancelledEventListener {

    private static final String REGISTRATION_CANCELLED_ROUTING_KEY =
            "registration.cancelled";

    private final RegistrationEventPublisher registrationEventPublisher;

    public RegistrationCancelledEventListener(
            RegistrationEventPublisher registrationEventPublisher
    ) {
        this.registrationEventPublisher = registrationEventPublisher;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void handle(
            RegistrationCancelledEvent event
    ) {
        RegistrationMessage message =
                new RegistrationMessage(
                        event.registrationId(),
                        event.eventId(),
                        event.participantId(),
                        "registration.cancelled",
                        event.occurredAt()
                );

        registrationEventPublisher.publish(
                message,
                REGISTRATION_CANCELLED_ROUTING_KEY
        );
    }
}
