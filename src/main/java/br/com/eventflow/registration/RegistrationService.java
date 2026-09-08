package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.event.EventStatus;
import br.com.eventflow.registration.dto.RegistrationResponse;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.ForbiddenException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RegistrationService {

    private final RegistrationRepository registrationRepository;
    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final long reservationTimeoutMinutes;

    public RegistrationService(
            RegistrationRepository registrationRepository,
            EventRepository eventRepository,
            UserRepository userRepository,
            @Value("${app.registration.reservation-timeout-minutes}")
            long reservationTimeoutMinutes
    ) {
        this.registrationRepository = registrationRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.reservationTimeoutMinutes = reservationTimeoutMinutes;
    }

    @Transactional
    public RegistrationResponse createReservation(
            Long eventId,
            Long participantId
    ) {
        Event event = eventRepository
                .findByIdForUpdate(eventId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Event not found"
                        )
                );

        OffsetDateTime now =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);

        validateEventAvailability(
                event,
                now
        );

        User participant = userRepository
                .findById(participantId)
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Authentication is no longer valid"
                        )
                );

        if (participant.getRole()
                != UserRole.PARTICIPANT) {

            throw new ForbiddenException(
                    "Only participants can register for events"
            );
        }

        handleExistingRegistration(
                participantId,
                eventId,
                now
        );

        long occupiedPlaces =
                registrationRepository
                        .countOccupyingCapacity(
                                eventId,
                                RegistrationStatus.CONFIRMED,
                                RegistrationStatus.PENDING,
                                now
                        );

        if (occupiedPlaces >= event.getCapacity()) {
            throw new ConflictException(
                    "Event has no available capacity"
            );
        }

        OffsetDateTime reservationExpiresAt =
                now.plusMinutes(
                        reservationTimeoutMinutes
                );

        Registration registration =
                new Registration(
                        event,
                        participant,
                        reservationExpiresAt
                );

        Registration savedRegistration =
                registrationRepository.save(
                        registration
                );

        return toResponse(savedRegistration);
    }

    private void validateEventAvailability(
            Event event,
            OffsetDateTime now
    ) {
        if (event.getStatus()
                != EventStatus.PUBLISHED) {

            throw new ConflictException(
                    "Event is not available for registration"
            );
        }

        if (!now.isBefore(event.getStartDate())) {
            throw new ConflictException(
                    "Event has already started"
            );
        }
    }

    private void handleExistingRegistration(
            Long participantId,
            Long eventId,
            OffsetDateTime now
    ) {
        registrationRepository
                .findFirstByParticipant_UserIdAndEvent_EventIdAndStatusIn(
                        participantId,
                        eventId,
                        List.of(
                                RegistrationStatus.PENDING,
                                RegistrationStatus.CONFIRMED
                        )
                )
                .ifPresent(registration -> {

                    if (registration.getStatus()
                            == RegistrationStatus.CONFIRMED) {

                        throw new ConflictException(
                                "Participant already has an active registration"
                        );
                    }

                    if (registration
                            .getReservationExpiresAt()
                            .isAfter(now)) {

                        throw new ConflictException(
                                "Participant already has an active registration"
                        );
                    }

                    registration.cancel();

                    registrationRepository.flush();
                });
    }

    private RegistrationResponse toResponse(
            Registration registration
    ) {
        return new RegistrationResponse(
                registration.getRegistrationId(),
                registration.getEvent().getEventId(),
                registration.getParticipant().getUserId(),
                registration.getStatus(),
                registration.getReservationExpiresAt(),
                registration.getCreatedAt(),
                registration.getUpdatedAt()
        );
    }
}