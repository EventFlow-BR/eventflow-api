package br.com.eventflow.event;

import br.com.eventflow.event.dto.EventResponse;

final class EventResponseMapper {

    private EventResponseMapper() {
    }

    static EventResponse toResponse(Event event) {
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