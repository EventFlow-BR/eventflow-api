package br.com.eventflow.registration.messaging;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class RegistrationMessageHandlerTest {

    private RegistrationMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler =
                new RegistrationMessageHandler();
    }

    @Test
    void shouldHandleRegistrationConfirmedMessage() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.confirmed",
                        OffsetDateTime.now()
                );

        assertDoesNotThrow(
                () -> handler.handle(message)
        );
    }

    @Test
    void shouldHandleRegistrationCancelledMessage() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.cancelled",
                        OffsetDateTime.now()
                );

        assertDoesNotThrow(
                () -> handler.handle(message)
        );
    }

    @Test
    void shouldHandleRegistrationExpiredMessage() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.expired",
                        OffsetDateTime.now()
                );

        assertDoesNotThrow(
                () -> handler.handle(message)
        );
    }

    @Test
    void shouldSafelyHandleUnsupportedRegistrationMessageType() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        "registration.unknown",
                        OffsetDateTime.now()
                );

        assertDoesNotThrow(
                () -> handler.handle(message)
        );
    }

    @Test
    void shouldSafelyHandleNullEventType() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        null,
                        OffsetDateTime.now()
                );

        assertDoesNotThrow(
                () -> handler.handle(message)
        );
    }

    @Test
    void shouldSafelyHandleBlankEventType() {
        RegistrationMessage message =
                new RegistrationMessage(
                        UUID.randomUUID(),
                        100L,
                        200L,
                        20L,
                        " ",
                        OffsetDateTime.now()
                );

        assertDoesNotThrow(
                () -> handler.handle(message)
        );
    }
}