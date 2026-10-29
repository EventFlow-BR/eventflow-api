package br.com.eventflow.registration;

public record RegistrationCreatedEvent(
        Long eventId,
        Long participantId
) {
}
