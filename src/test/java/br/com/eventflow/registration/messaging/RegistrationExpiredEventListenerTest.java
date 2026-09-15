package br.com.eventflow.registration.messaging;

import br.com.eventflow.registration.event.RegistrationExpiredEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationExpiredEventListenerTest {

    @Mock
    private RegistrationEventPublisher registrationEventPublisher;

    private RegistrationExpiredEventListener listener;

    @BeforeEach
    void setUp() {
        listener =
                new RegistrationExpiredEventListener(
                        registrationEventPublisher
                );
    }

    @Test
    void shouldPublishRegistrationExpiredMessage() {
        OffsetDateTime occurredAt =
                OffsetDateTime.now();

        RegistrationExpiredEvent event =
                new RegistrationExpiredEvent(
                        100L,
                        200L,
                        20L,
                        occurredAt
                );

        listener.handle(event);

        ArgumentCaptor<RegistrationMessage> messageCaptor =
                ArgumentCaptor.forClass(
                        RegistrationMessage.class
                );

        verify(registrationEventPublisher)
                .publish(
                        messageCaptor.capture(),
                        eq("registration.expired")
                );

        RegistrationMessage message =
                messageCaptor.getValue();

        assertEquals(
                100L,
                message.registrationId()
        );

        assertEquals(
                200L,
                message.eventId()
        );

        assertEquals(
                20L,
                message.participantId()
        );

        assertEquals(
                "registration.expired",
                message.eventType()
        );

        assertEquals(
                occurredAt,
                message.occurredAt()
        );
    }
}