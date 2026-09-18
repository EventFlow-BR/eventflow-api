package br.com.eventflow.registration.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationEventConsumerTest {

    @Mock
    private RegistrationMessageProcessor registrationMessageProcessor;

    private RegistrationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer =
                new RegistrationEventConsumer(
                        registrationMessageProcessor
                );
    }

    @Test
    void shouldDelegateConsumedMessageToProcessor() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        consumer.consume(message);

        verify(registrationMessageProcessor)
                .process(message);
    }

    @Test
    void shouldPropagateExceptionWhenProcessingFails() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        doThrow(
                new RuntimeException(
                        "Processing failure"
                )
        )
                .when(registrationMessageProcessor)
                .process(message);

        assertThrows(
                RuntimeException.class,
                () -> consumer.consume(message)
        );
    }
}