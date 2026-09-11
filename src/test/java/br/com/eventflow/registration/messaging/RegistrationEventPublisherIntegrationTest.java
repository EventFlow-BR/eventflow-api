package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
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

        RegistrationMessage message =
                new RegistrationMessage(
                        100L,
                        50L,
                        20L,
                        "registration.test",
                        OffsetDateTime.now()
                );

        publisher.publish(
                message,
                "registration.test"
        );

        Message receivedMessage =
                rabbitTemplate.receive(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                        5_000
                );

        String payload =
                new String(
                        receivedMessage.getBody(),
                        StandardCharsets.UTF_8
                );

        assertTrue(
                payload.contains(
                        "\"registrationId\":100"
                )
        );

        assertTrue(
                payload.contains(
                        "\"eventId\":50"
                )
        );

        assertTrue(
                payload.contains(
                        "\"participantId\":20"
                )
        );

        assertTrue(
                payload.contains(
                        "\"eventType\":\"registration.test\""
                )
        );
    }
}