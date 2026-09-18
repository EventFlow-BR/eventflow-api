package br.com.eventflow.registration.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistrationMessageProcessorTest {

    @Mock
    private ProcessedMessageRepository processedMessageRepository;

    @Mock
    private RegistrationMessageHandler registrationMessageHandler;

    private RegistrationMessageProcessor processor;

    @BeforeEach
    void setUp() {
        processor =
                new RegistrationMessageProcessor(
                        processedMessageRepository,
                        registrationMessageHandler
                );
    }

    @Test
    void shouldProcessMessageWhenMessageIdIsNew() {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage message =
                message(messageId);

        when(processedMessageRepository.tryInsert(
                eq(messageId),
                any(OffsetDateTime.class)
        ))
                .thenReturn(1);

        processor.process(message);

        verify(registrationMessageHandler)
                .handle(message);
    }

    @Test
    void shouldIgnoreMessageWhenMessageIdWasAlreadyProcessed() {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage message =
                message(messageId);

        when(processedMessageRepository.tryInsert(
                eq(messageId),
                any(OffsetDateTime.class)
        ))
                .thenReturn(0);

        processor.process(message);

        verifyNoInteractions(
                registrationMessageHandler
        );
    }

    @Test
    void shouldPropagateExceptionWhenMessageHandlingFails() {
        UUID messageId =
                UUID.randomUUID();

        RegistrationMessage message =
                message(messageId);

        when(processedMessageRepository.tryInsert(
                eq(messageId),
                any(OffsetDateTime.class)
        ))
                .thenReturn(1);

        doThrow(
                new RuntimeException(
                        "Processing failure"
                )
        )
                .when(registrationMessageHandler)
                .handle(message);

        assertThrows(
                RuntimeException.class,
                () -> processor.process(message)
        );

        verify(registrationMessageHandler)
                .handle(message);
    }

    private RegistrationMessage message(
            UUID messageId
    ) {
        return new RegistrationMessage(
                messageId,
                100L,
                200L,
                20L,
                "registration.confirmed",
                OffsetDateTime.now()
        );
    }
}