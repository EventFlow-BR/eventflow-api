package br.com.eventflow;

import br.com.eventflow.shared.config.RabbitMqConfig;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.rabbitmq.RabbitMQContainer;

import java.time.OffsetDateTime;
import java.util.UUID;

import br.com.eventflow.registration.messaging.RegistrationMessage;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMqTestcontainerIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private RabbitMQContainer rabbitMqContainer;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                false
        );

        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_DLQ,
                false
        );
    }

    @Test
    void shouldStartRabbitMqContainer() {
        assertTrue(
                rabbitMqContainer.isRunning()
        );
    }

    @Test
    void shouldCreateRegistrationQueue() {
        var queueProperties =
                amqpAdmin.getQueueProperties(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE
                );

        assertNotNull(queueProperties);
    }

    @Test
    void shouldRouteRegistrationMessageThroughTestcontainerRabbitMq() {
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

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                "registration.confirmed",
                message
        );

        Object payload =
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                        5_000
                );

        assertNotNull(payload);

        RegistrationMessage receivedMessage =
                (RegistrationMessage) payload;

        assertEquals(
                messageId,
                receivedMessage.messageId()
        );

        assertEquals(
                "registration.confirmed",
                receivedMessage.eventType()
        );
    }
}