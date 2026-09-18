package br.com.eventflow.registration.messaging;

import br.com.eventflow.shared.config.RabbitMqConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("local")
@TestPropertySource(
        properties =
                "app.rabbitmq.registration-consumer.enabled=true"
)
class RegistrationEventConsumerIntegrationTest {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    @MockitoBean
    private RegistrationMessageHandler registrationMessageHandler;


    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                false
        );

        processedMessageRepository.deleteAll();

        clearInvocations(
                registrationMessageHandler
        );
    }

    @Test
    void shouldConsumeRegistrationMessageFromRabbitMq() {
        Long registrationId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        Long eventId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        Long participantId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        OffsetDateTime occurredAt =
                OffsetDateTime.now();

        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        registrationId,
                        eventId,
                        participantId,
                        "registration.confirmed",
                        occurredAt
                );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                "registration.confirmed",
                message
        );

        verify(
                registrationMessageHandler,
                timeout(5_000)
        ).handle(
                argThat(consumedMessage ->
                        consumedMessage.registrationId()
                                .equals(registrationId)
                                && consumedMessage.eventId()
                                .equals(eventId)
                                && consumedMessage.participantId()
                                .equals(participantId)
                                && consumedMessage.eventType()
                                .equals("registration.confirmed")
                                && consumedMessage.occurredAt()
                                .isEqual(occurredAt)
                )
        );
    }

    @Test
    void shouldConsumeRegistrationCancelledMessageFromRabbitMq() {
        Long registrationId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        Long eventId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        Long participantId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        OffsetDateTime occurredAt =
                OffsetDateTime.now();

        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        registrationId,
                        eventId,
                        participantId,
                        "registration.cancelled",
                        occurredAt
                );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                "registration.cancelled",
                message
        );

        verify(
                registrationMessageHandler,
                timeout(5_000)
        ).handle(
                argThat(consumedMessage ->
                        consumedMessage.registrationId()
                                .equals(registrationId)
                                && consumedMessage.eventId()
                                .equals(eventId)
                                && consumedMessage.participantId()
                                .equals(participantId)
                                && consumedMessage.eventType()
                                .equals("registration.cancelled")
                                && consumedMessage.occurredAt()
                                .isEqual(occurredAt)
                )
        );
    }

    @Test
    void shouldConsumeRegistrationExpiredMessageFromRabbitMq() {
        Long registrationId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        Long eventId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        Long participantId =
                Math.abs(
                        UUID.randomUUID()
                                .getMostSignificantBits()
                );

        OffsetDateTime occurredAt =
                OffsetDateTime.now();

        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        registrationId,
                        eventId,
                        participantId,
                        "registration.expired",
                        occurredAt
                );

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                "registration.expired",
                message
        );

        verify(
                registrationMessageHandler,
                timeout(5_000)
        ).handle(
                argThat(consumedMessage ->
                        consumedMessage.registrationId()
                                .equals(registrationId)
                                && consumedMessage.eventId()
                                .equals(eventId)
                                && consumedMessage.participantId()
                                .equals(participantId)
                                && consumedMessage.eventType()
                                .equals("registration.expired")
                                && consumedMessage.occurredAt()
                                .isEqual(occurredAt)
                )
        );
    }

    @Test
    void shouldProcessDuplicateRabbitMqMessageOnlyOnce() {
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

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.EVENTS_EXCHANGE,
                "registration.confirmed",
                message
        );

        verify(
                registrationMessageHandler,
                after(3_000).times(1)
        ).handle(
                argThat(consumedMessage ->
                        consumedMessage.messageId()
                                .equals(messageId)
                )
        );
    }
}