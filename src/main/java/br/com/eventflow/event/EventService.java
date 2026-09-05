package br.com.eventflow.event;

import br.com.eventflow.event.dto.CreateEventRequest;
import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.shared.exception.BadRequestException;
import br.com.eventflow.shared.exception.ForbiddenException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;

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

    @Transactional(readOnly = true)
    public List<EventResponse> listVisibleEvents(
            Long userId,
            UserRole role
    ) {
        List<Event> events;

        if (role == UserRole.ORGANIZER) {
            events = eventRepository
                    .findAllByOrganizer_UserId(userId);
        } else {
            events = eventRepository
                    .findAllByStatus(EventStatus.PUBLISHED);
        }

        return events.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getVisibleEvent(
            Long eventId,
            Long userId,
            UserRole role
    ) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() ->
                        new NotFoundException("Event not found")
                );

        if (event.getStatus() == EventStatus.PUBLISHED) {
            return toResponse(event);
        }

        boolean ownsEvent =
                role == UserRole.ORGANIZER
                        && event.getOrganizer()
                        .getUserId()
                        .equals(userId);

        if (!ownsEvent) {
            throw new NotFoundException("Event not found");
        }

        return toResponse(event);
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
