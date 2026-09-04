package br.com.eventflow.event;

import br.com.eventflow.event.dto.CreateEventRequest;
import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.shared.exception.BadRequestException;
import br.com.eventflow.shared.exception.ForbiddenException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;

    public EventService(
            EventRepository eventRepository,
            UserRepository userRepository
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public EventResponse createEvent(
            Long organizerId,
            CreateEventRequest request
    ) {
        User organizer = userRepository.findById(organizerId)
                .orElseThrow(() ->
                        new UnauthorizedException(
                                "Authentication is no longer valid"
                        )
                );

        if (organizer.getRole() != UserRole.ORGANIZER) {
            throw new ForbiddenException(
                    "Only organizers can create events"
            );
        }

        if (!request.startDate().isBefore(request.endDate())) {
            throw new BadRequestException(
                    "Event start must be before end date"
            );
        }

        Event event = new Event(
                organizer,
                request.name().trim(),
                request.description().trim(),
                request.location().trim(),
                request.startDate(),
                request.endDate(),
                request.capacity(),
                request.price()
        );

        Event savedEvent =  eventRepository.save(event);

        return toResponse(savedEvent);
    }

    private EventResponse toResponse(Event event) {
        return new EventResponse(
                event.getEventId(),
                event.getOrganizer().getUserId(),
                event.getName(),
                event.getDescription(),
                event.getLocation(),
                event.getStartDate(),
                event.getEndDate(),
                event.getCapacity(),
                event.getPrice(),
                event.getStatus(),
                event.getPublishedAt(),
                event.getCreatedAt(),
                event.getUpdatedAt()
        );
    }
}
