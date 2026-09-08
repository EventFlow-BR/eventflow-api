package br.com.eventflow.payment.dto;

import br.com.eventflow.payment.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PaymentResponse(
        Long id,
        Long registrationId,
        BigDecimal amount,
        PaymentStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
