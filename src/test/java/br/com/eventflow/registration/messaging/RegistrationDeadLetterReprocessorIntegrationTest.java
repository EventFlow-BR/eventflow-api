package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationDeadLetterReprocessorIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private RegistrationDeadLetterReprocessor reprocessor;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private org.springframework.amqp.core.AmqpAdmin amqpAdmin;

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
    void shouldReprocessDeadLetterMessageAndPreserveMessageId() {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage originalMessage =
                new RegistrationMessage(
                        messageId,
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.DEAD_LETTER_EXCHANGE,
                RabbitMqConfig.REGISTRATION_DEAD_LETTER_ROUTING_KEY,
                originalMessage
        );

        boolean reprocessed =
                reprocessor.reprocessNext();

        assertTrue(reprocessed);

        Object payload =
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                        5_000
                );

        assertNotNull(payload);

        RegistrationMessage reprocessedMessage =
                (RegistrationMessage) payload;

        assertEquals(
                originalMessage.messageId(),
                reprocessedMessage.messageId()
        );

        assertEquals(
                originalMessage.registrationId(),
                reprocessedMessage.registrationId()
        );

        assertEquals(
                originalMessage.eventId(),
                reprocessedMessage.eventId()
        );

        assertEquals(
                originalMessage.participantId(),
                reprocessedMessage.participantId()
        );

        assertEquals(
                originalMessage.eventType(),
                reprocessedMessage.eventType()
        );

        assertTrue(
                originalMessage.occurredAt()
                        .isEqual(
                                reprocessedMessage.occurredAt()
                        )
        );
    }

    @Test
    void shouldReturnFalseWhenDeadLetterQueueIsEmpty() {
        boolean reprocessed =
                reprocessor.reprocessNext();

        assertFalse(reprocessed);

        Object message =
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE
                );

        assertNull(message);
    }

    @Test
    void shouldKeepMessageInDeadLetterQueueWhenReprocessingFails() {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage invalidMessage =
                new RegistrationMessage(
                        messageId,
                        100L,
                        200L,
                        20L,
                        "registration.unknown",
                        OffsetDateTime.now()
                );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.DEAD_LETTER_EXCHANGE,
                RabbitMqConfig.REGISTRATION_DEAD_LETTER_ROUTING_KEY,
                invalidMessage
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> reprocessor.reprocessNext()
        );

        Object payload =
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_DLQ,
                        5_000
                );

        assertNotNull(payload);

        RegistrationMessage returnedMessage =
                (RegistrationMessage) payload;

        assertEquals(
                messageId,
                returnedMessage.messageId()
        );

        assertEquals(
                "registration.unknown",
                returnedMessage.eventType()
        );
    }
}