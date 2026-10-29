package br.com.eventflow.registration.event;

import java.time.OffsetDateTime;

public record RegistrationCancelledEvent(
        Long registrationId,
        Long eventId,
        Long participantId,
        OffsetDateTime occurredAt
) {
}
