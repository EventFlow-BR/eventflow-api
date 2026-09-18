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

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@TestPropertySource(
        properties =
                "app.rabbitmq.registration-consumer.enabled=true"
)
class RegistrationMessageRetryIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @MockitoBean
    private RegistrationMessageHandler registrationMessageHandler;

    @Test
    void shouldRetryRegistrationMessageAndSucceedBeforeRetriesAreExhausted() {
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
                        "Transient failure 1"
                )
        )
                .doThrow(
                        new RuntimeException(
                                "Transient failure 2"
                        )
                )
                .doNothing()
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

        verify(
                registrationMessageHandler,
                timeout(5_000).times(3)
        ).handle(
                argThat(consumedMessage ->
                        consumedMessage
                                .registrationId()
                                .equals(registrationId)
                )
        );

        Message deadLetterMessage =
                rabbitTemplate.receive(
                        RabbitMqConfig.REGISTRATION_EVENTS_DLQ,
                        1_000
                );

        assertNull(
                deadLetterMessage
        );
    }
}