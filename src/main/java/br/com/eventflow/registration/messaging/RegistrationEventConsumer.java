package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private final RegistrationMessageProcessor registrationMessageProcessor;

    public RegistrationEventConsumer(
            RegistrationMessageProcessor registrationMessageProcessor
    ) {
        this.registrationMessageProcessor =
                registrationMessageProcessor;
    }

    private static final Logger logger =
            LoggerFactory.getLogger(
                    RegistrationEventConsumer.class
            );

    @RabbitListener(
            queues = RabbitMqConfig.REGISTRATION_EVENTS_QUEUE
    )
    public void consume(
            RegistrationMessage message
    ) {
        try {
            registrationMessageProcessor.process(message);

        } catch (RuntimeException exception) {

            logger.error(
                    "Failed to process registration message: messageId={}, registrationId={}, eventType={}",
                    message.messageId(),
                    message.registrationId(),
                    message.eventType(),
                    exception
            );

            throw exception;
        }
    }
}