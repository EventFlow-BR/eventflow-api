package br.com.eventflow.event.dto;

import br.com.eventflow.event.EventStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record EventResponse(
        Long id,
        Long organizerId,
        String name,
        String description,
        String location,
        OffsetDateTime startDate,
        OffsetDateTime endDate,
        Integer capacity,
        BigDecimal price,
        EventStatus status,
        OffsetDateTime publishedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
