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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationDeadLetterReprocessorTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private RegistrationDeadLetterReprocessor reprocessor;

    @BeforeEach
    void setUp() {
        reprocessor =
                new RegistrationDeadLetterReprocessor(
                        rabbitTemplate
                );
    }

    @Test
    void shouldReprocessRegistrationMessageFromDeadLetterQueue() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        when(
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_DLQ
                )
        )
                .thenReturn(message);

        boolean reprocessed =
                reprocessor.reprocessNext();

        assertTrue(reprocessed);

        verify(rabbitTemplate)
                .convertAndSend(
                        RabbitMqConfig.EVENTS_EXCHANGE,
                        "registration.confirmed",
                        message
                );
    }

    @Test
    void shouldReturnFalseWhenDeadLetterQueueIsEmpty() {
        when(
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_DLQ
                )
        )
                .thenReturn(null);

        boolean reprocessed =
                reprocessor.reprocessNext();

        assertFalse(reprocessed);

        verify(
                rabbitTemplate,
                never()
        ).convertAndSend(
                eq(RabbitMqConfig.EVENTS_EXCHANGE),
                anyString(),
                any(RegistrationMessage.class)
        );
    }

    @Test
    void shouldRejectUnsupportedRegistrationEventType() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.unknown",
                        OffsetDateTime.now()
                );

        when(
                rabbitTemplate.receiveAndConvert(
                        RabbitMqConfig.REGISTRATION_EVENTS_DLQ
                )
        )
                .thenReturn(message);

        assertThrows(
                IllegalArgumentException.class,
                () -> reprocessor.reprocessNext()
        );

        verify(
                rabbitTemplate,
                never()
        ).convertAndSend(
                eq(RabbitMqConfig.EVENTS_EXCHANGE),
                anyString(),
                any(RegistrationMessage.class)
        );
    }
}