package br.com.eventflow.registration.messaging;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface ProcessedMessageRepository
        extends JpaRepository<ProcessedMessage, UUID> {

    @Modifying
    @Query(
            value = """
                    INSERT INTO processed_messages (
                        message_id,
                        processed_at
                    )
                    VALUES (
                        :messageId,
                        :processedAt
                    )
                    ON CONFLICT (message_id) DO NOTHING
                    """,
            nativeQuery = true
    )
    int tryInsert(
            @Param("messageId") UUID messageId,
            @Param("processedAt") OffsetDateTime processedAt
    );
}