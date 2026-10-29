package br.com.eventflow.event;

import br.com.eventflow.event.dto.EventResponse;
import br.com.eventflow.shared.exception.NotFoundException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventCachedReadService {

    private final EventRepository eventRepository;

    public EventCachedReadService(
            EventRepository eventRepository
    ) {
        this.eventRepository = eventRepository;
    }

    @Cacheable(
            cacheNames = "events",
            key = "#eventId"
    )
    @Transactional(readOnly = true)
    public EventResponse getById(Long eventId) {
        Event event =
                eventRepository.findById(eventId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Event not found"
                                )
                        );

        return EventResponseMapper.toResponse(event);
    }
}