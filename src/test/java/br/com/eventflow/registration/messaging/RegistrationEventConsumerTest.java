package br.com.eventflow.registration.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationEventConsumerTest {

    @Mock
    private RegistrationMessageHandler registrationMessageHandler;

    private RegistrationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer =
                new RegistrationEventConsumer(
                        registrationMessageHandler
                );
    }

    @Test
    void shouldDelegateConsumedMessageToHandler() {
        RegistrationMessage message =
                new RegistrationMessage(
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        consumer.consume(message);

        verify(registrationMessageHandler)
                .handle(message);
    }

    @Test
    void shouldPropagateExceptionWhenMessageHandlingFails() {
        RegistrationMessage message =
                new RegistrationMessage(
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
                .when(registrationMessageHandler)
                .handle(message);

        assertThrows(
                RuntimeException.class,
                () -> consumer.consume(message)
        );
    }
}