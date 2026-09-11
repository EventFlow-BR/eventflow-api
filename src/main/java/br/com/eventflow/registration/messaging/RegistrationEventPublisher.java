package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RegistrationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public RegistrationEventPublisher(
            RabbitTemplate rabbitTemplate
    ) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(
            RegistrationMessage message,
            String routingKey
    ) {
        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                routingKey,
                message
        );
    }
}
