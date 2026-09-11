package br.com.eventflow.registration.messaging;

import java.time.OffsetDateTime;

public record RegistrationMessage(
        Long registrationId,
        Long eventId,
        Long participantId,
        String eventType,
        OffsetDateTime occurredAt
) {
}
