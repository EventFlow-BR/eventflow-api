package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RegistrationEventPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher =
                new RegistrationEventPublisher(
                        rabbitTemplate
                );
    }

    @Test
    void shouldPublishRegistrationMessageUsingExpectedExchangeAndRoutingKey() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        publisher.publish(
                message,
                "registration.test"
        );

        verify(rabbitTemplate)
                .convertAndSend(
                        RabbitMqConfig.EVENTS_EXCHANGE,
                        "registration.test",
                        message
                );
    }
}
