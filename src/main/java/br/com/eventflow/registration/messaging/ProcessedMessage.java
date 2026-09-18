package br.com.eventflow.registration.messaging;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    @Column(
            name = "message_id",
            nullable = false,
            updatable = false
    )
    private UUID messageId;

    @Column(
            name = "processed_at",
            nullable = false,
            updatable = false
    )
    private OffsetDateTime processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(
            UUID messageId,
            OffsetDateTime processedAt
    ) {
        this.messageId = messageId;
        this.processedAt = processedAt;
    }

    public UUID getMessageId() {
        return messageId;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }
}