package br.com.eventflow.registration.event;

import java.time.OffsetDateTime;

public record RegistrationExpiredEvent(
        Long registrationId,
        Long eventId,
        Long participantId,
        OffsetDateTime occurredAt
) {
}
