package br.com.eventflow.event;

import br.com.eventflow.event.dto.CreateEventRequest;
import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.event.dto.UpdateEventRequest;
import br.com.eventflow.shared.exception.BadRequestException;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.ForbiddenException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class EventService {

    private final EventRepository eventRepository;
    private final UserRepository userRepository;
    private final EventCachedReadService eventCachedReadService;

    public EventService(
            EventRepository eventRepository,
            UserRepository userRepository,
            EventCachedReadService eventCachedReadService
    ) {
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.eventCachedReadService = eventCachedReadService;
    }

    @Transactional
    public EventResponse createEvent(
            Long organizerId,
            CreateEventRequest request
    ) {
        User organizer =
                userRepository.findById(organizerId)
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

        if (!request.startDate()
                .isBefore(request.endDate())) {

            throw new BadRequestException(
                    "Event start must be before end date"
            );
        }

        Event event =
                new Event(
                        organizer,
                        request.name().trim(),
                        request.description().trim(),
                        request.location().trim(),
                        request.startDate(),
                        request.endDate(),
                        request.capacity(),
                        request.price()
                );

        Event savedEvent =
                eventRepository.save(event);

        return EventResponseMapper.toResponse(
                savedEvent
        );
    }

    @Transactional(readOnly = true)
    public List<EventResponse> listVisibleEvents(
            Long userId,
            UserRole role
    ) {
        List<Event> events;

        if (role == UserRole.ORGANIZER) {
            events =
                    eventRepository
                            .findAllByOrganizer_UserId(
                                    userId
                            );
        } else {
            events =
                    eventRepository
                            .findAllByStatus(
                                    EventStatus.PUBLISHED
                            );
        }

        return events.stream()
                .map(EventResponseMapper::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EventResponse getVisibleEvent(
            Long eventId,
            Long userId,
            UserRole role
    ) {
        EventResponse event =
                eventCachedReadService.getById(
                        eventId
                );

        if (event.status()
                == EventStatus.PUBLISHED) {

            return event;
        }

        boolean ownsEvent =
                role == UserRole.ORGANIZER
                        && event.organizerId()
                        .equals(userId);

        if (!ownsEvent) {
            throw new NotFoundException(
                    "Event not found"
            );
        }

        return event;
    }

    @Transactional
    @CacheEvict(
            cacheNames = "events",
            key = "#eventId"
    )
    public EventResponse updateEvent(
            Long eventId,
            Long organizerId,
            UpdateEventRequest request
    ) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Event not found"
                                )
                        );

        if (!event.getOrganizer()
                .getUserId()
                .equals(organizerId)) {

            throw new NotFoundException(
                    "Event not found"
            );
        }

        if (!request.startDate()
                .isBefore(request.endDate())) {

            throw new BadRequestException(
                    "Event start must be before end date"
            );
        }

        event.updateDetails(
                request.name().trim(),
                request.description().trim(),
                request.location().trim(),
                request.startDate(),
                request.endDate(),
                request.capacity(),
                request.price()
        );

        eventRepository.flush();

        return EventResponseMapper.toResponse(
                event
        );
    }

    @Transactional
    @CacheEvict(
            cacheNames = "events",
            key = "#eventId"
    )
    public EventResponse publishEvent(
            Long eventId,
            Long organizerId
    ) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Event not found"
                                )
                        );

        if (!event.getOrganizer()
                .getUserId()
                .equals(organizerId)) {

            throw new NotFoundException(
                    "Event not found"
            );
        }

        if (event.getStatus()
                != EventStatus.DRAFT) {

            throw new ConflictException(
                    "Only draft events can be published"
            );
        }

        event.publish(
                OffsetDateTime.now()
                        .truncatedTo(
                                ChronoUnit.MICROS
                        )
        );

        eventRepository.flush();

        return EventResponseMapper.toResponse(
                event
        );
    }

    @Transactional
    @CacheEvict(
            cacheNames = "events",
            key = "#eventId"
    )
    public EventResponse cancelEvent(
            Long eventId,
            Long organizerId
    ) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Event not found"
                                )
                        );

        if (!event.getOrganizer()
                .getUserId()
                .equals(organizerId)) {

            throw new NotFoundException(
                    "Event not found"
            );
        }

        if (event.getStatus()
                == EventStatus.CANCELLED
                || event.getStatus()
                == EventStatus.FINISHED) {

            throw new ConflictException(
                    "Event cannot be cancelled in its current status"
            );
        }

        OffsetDateTime now =
                OffsetDateTime.now()
                        .truncatedTo(
                                ChronoUnit.MICROS
                        );

        if (!now.isBefore(
                event.getStartDate()
        )) {
            throw new ConflictException(
                    "Event cannot be cancelled after it has started"
            );
        }

        event.cancel();

        eventRepository.flush();

        return EventResponseMapper.toResponse(
                event
        );
    }
}