package br.com.eventflow.registration.messaging;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RegistrationMessage(
        UUID messageId,
        Long registrationId,
        Long eventId,
        Long participantId,
        String eventType,
        OffsetDateTime occurredAt
) {
}
