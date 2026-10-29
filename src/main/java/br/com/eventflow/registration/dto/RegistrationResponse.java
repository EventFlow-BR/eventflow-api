package br.com.eventflow.registration.dto;

import br.com.eventflow.registration.enums.RegistrationStatus;

import java.time.OffsetDateTime;

public record RegistrationResponse(
        Long id,
        Long eventId,
        Long participantId,
        RegistrationStatus status,
        OffsetDateTime reservationExpiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
