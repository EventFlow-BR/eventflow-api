package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(
        properties = "spring.rabbitmq.listener.simple.auto-startup=false"
)
class RegistrationEventPublisherIntegrationTest {

    @Autowired
    private RegistrationEventPublisher publisher;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Test
    void shouldPublishRegistrationMessageToRabbitMq() {
        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                false
        );

        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage message =
                new RegistrationMessage(
                        messageId,
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        publisher.publish(
                message,
                "registration.confirmed"
        );

        Message receivedMessage =
                rabbitTemplate.receive(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                        5_000
                );

        assertNotNull(receivedMessage);

        String payload =
                new String(
                        receivedMessage.getBody(),
                        StandardCharsets.UTF_8
                );

        assertTrue(
                payload.contains(
                        "\"messageId\":\""
                                + messageId
                                + "\""
                )
        );

        assertTrue(
                payload.contains(
                        "\"registrationId\":100"
                )
        );

        assertTrue(
                payload.contains(
                        "\"eventId\":200"
                )
        );

        assertTrue(
                payload.contains(
                        "\"participantId\":20"
                )
        );

        assertTrue(
                payload.contains(
                        "\"eventType\":\"registration.confirmed\""
                )
        );
    }
}