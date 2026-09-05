package br.com.eventflow.event.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record UpdateEventRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @NotBlank
        String description,

        @NotBlank
        @Size(max = 255)
        String location,

        @NotNull
        OffsetDateTime startDate,

        @NotNull
        OffsetDateTime endDate,

        @NotNull
        @Positive
        Integer capacity,

        @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 8, fraction = 2)
        BigDecimal price
) {
}
