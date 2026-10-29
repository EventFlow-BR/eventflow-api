package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@TestPropertySource(
        properties =
                "app.rabbitmq.registration-consumer.enabled=true"
)
class RegistrationMessageDeadLetterIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private RegistrationMessageHandler registrationMessageHandler;

    @Test
    void shouldSendRegistrationMessageToDeadLetterQueueAfterRetriesAreExhausted() {
        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                false
        );

        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_DLQ,
                false
        );

        Long registrationId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        registrationId,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        doThrow(
                new RuntimeException(
                        "Simulated permanent processing failure"
                )
        )
                .when(registrationMessageHandler)
                .handle(
                        argThat(consumedMessage ->
                                consumedMessage
                                        .registrationId()
                                        .equals(registrationId)
                        )
                );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                "registration.confirmed",
                message
        );

        Message deadLetterMessage =
                rabbitTemplate.receive(
                        RabbitMqConfig.REGISTRATION_EVENTS_DLQ,
                        7_000
                );

        assertNotNull(
                deadLetterMessage,
                "Expected message was not routed to the dead-letter queue"
        );

        String payload =
                new String(
                        deadLetterMessage.getBody(),
                        StandardCharsets.UTF_8
                );

        assertTrue(
                payload.contains(
                        "\"registrationId\":"
                                + registrationId
                )
        );

        assertTrue(
                payload.contains(
                        "\"eventType\":\"registration.confirmed\""
                )
        );
    }
}