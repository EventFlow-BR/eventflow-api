package br.com.eventflow.registration.messaging;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationExpirationService;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.shared.config.RabbitMqConfig;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


class RegistrationExpiredAfterCommitIntegrationTest
        extends AbstractIntegrationTest {

    @Autowired
    private RegistrationExpirationService expirationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Test
    void shouldPublishRegistrationExpiredMessageAfterExpirationCommit() {
        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                false
        );

        String suffix =
                UUID.randomUUID().toString();

        User organizer =
                new User(
                        "Expiration Messaging Organizer",
                        "expire-msg-organizer-" + suffix + "@example.com",
                        "encoded-password",
                        UserRole.ORGANIZER
                );

        User participant =
                new User(
                        "Expiration Messaging Participant",
                        "expire-msg-participant-" + suffix + "@example.com",
                        "encoded-password",
                        UserRole.PARTICIPANT
                );

        organizer =
                userRepository.saveAndFlush(organizer);

        participant =
                userRepository.saveAndFlush(participant);

        Event event =
                new Event(
                        organizer,
                        "Expiration Messaging Event",
                        "Expiration messaging integration test",
                        "Petrópolis",
                        OffsetDateTime.now()
                                .plusDays(7),
                        OffsetDateTime.now()
                                .plusDays(7)
                                .plusHours(8),
                        100,
                        new BigDecimal("50.00")
                );

        event.publish(
                OffsetDateTime.now()
                        .minusDays(1)
        );

        event =
                eventRepository.saveAndFlush(event);

        Registration registration =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now()
                                .minusMinutes(1)
                );

        registration =
                registrationRepository
                        .saveAndFlush(registration);

        Long registrationId =
                registration.getRegistrationId();

        Long participantId =
                participant.getUserId();

        expirationService
                .expirePendingRegistrations();

        String payload = null;

        long deadline =
                System.currentTimeMillis() + 5_000;

        while (System.currentTimeMillis() < deadline) {

            Message receivedMessage =
                    rabbitTemplate.receive(
                            RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                            500
                    );

            if (receivedMessage == null) {
                continue;
            }

            String receivedPayload =
                    new String(
                            receivedMessage.getBody(),
                            StandardCharsets.UTF_8
                    );

            if (receivedPayload.contains(
                    "\"registrationId\":"
                            + registrationId
            )) {
                payload = receivedPayload;
                break;
            }
        }

        assertNotNull(
                payload,
                "Expected registration.expired message was not received"
        );

        assertTrue(
                payload.contains(
                        "\"registrationId\":"
                                + registrationId
                )
        );

        assertTrue(
                payload.contains(
                        "\"eventId\":"
                                + event.getEventId()
                )
        );

        assertTrue(
                payload.contains(
                        "\"participantId\":"
                                + participantId
                )
        );

        assertTrue(
                payload.contains(
                        "\"eventType\":\"registration.expired\""
                )
        );
    }
}