package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "app.rabbitmq.registration-consumer.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class RegistrationEventConsumer {

    private final RegistrationMessageHandler registrationMessageHandler;

    public RegistrationEventConsumer(
            RegistrationMessageHandler registrationMessageHandler
    ) {
        this.registrationMessageHandler =
                registrationMessageHandler;
    }

    @RabbitListener(
            queues = RabbitMqConfig.REGISTRATION_EVENTS_QUEUE
    )
    public void consume(
            RegistrationMessage message
    ) {
        registrationMessageHandler.handle(message);
    }
}