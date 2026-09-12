package br.com.eventflow.registration.messaging;

import br.com.eventflow.registration.event.RegistrationConfirmedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistrationConfirmedEventListenerTest {

    @Mock
    private RegistrationEventPublisher registrationEventPublisher;

    private RegistrationConfirmedEventListener listener;

    @BeforeEach
    void setUp() {
        listener =
                new RegistrationConfirmedEventListener(
                        registrationEventPublisher
                );
    }


    @Test
    void shouldPublishRegistrationConfirmedMessage() {
        OffsetDateTime occurredAt =
                OffsetDateTime.now();

        RegistrationConfirmedEvent event =
                new RegistrationConfirmedEvent(
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
                        org.mockito.ArgumentMatchers.eq(
                                "registration.confirmed"
                        )
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
                "registration.confirmed",
                message.eventType()
        );

        assertEquals(
                occurredAt,
                message.occurredAt()
        );
    }
}