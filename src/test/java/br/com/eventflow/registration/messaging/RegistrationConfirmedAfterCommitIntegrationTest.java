package br.com.eventflow.registration.messaging;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.payment.PaymentService;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.shared.config.RabbitMqConfig;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationConfirmedAfterCommitIntegrationTest {

    @Autowired
    private PaymentService paymentService;

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
    void shouldPublishRegistrationConfirmedMessageAfterPaymentCommit() {
        amqpAdmin.purgeQueue(
                RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                false
        );

        String suffix =
                UUID.randomUUID().toString();

        User organizer =
                new User(
                        "Messaging Organizer",
                        "messaging-organizer-" + suffix + "@example.com",
                        "encoded-password",
                        UserRole.ORGANIZER
                );

        User participant =
                new User(
                        "Messaging Participant",
                        "messaging-participant-" + suffix + "@example.com",
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
                        "Messaging Integration Event",
                        "Messaging integration test",
                        "Petrópolis",
                        OffsetDateTime.now()
                                .plusDays(7),
                        OffsetDateTime.now()
                                .plusDays(7)
                                .plusHours(8),
                        100,
                        new BigDecimal("75.00")
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
                                .plusMinutes(15)
                );

        registration =
                registrationRepository
                        .saveAndFlush(registration);

        Long registrationId =
                registration.getRegistrationId();

        Long participantId =
                participant.getUserId();

        paymentService.processPayment(
                registrationId,
                participantId
        );

        Message receivedMessage =
                rabbitTemplate.receive(
                        RabbitMqConfig.REGISTRATION_EVENTS_QUEUE,
                        5_000
                );

        assertNotNull(receivedMessage);

        String payload =
                new String(
                        receivedMessage.getBody(),
                        StandardCharsets.UTF_8
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
                        "\"eventType\":\"registration.confirmed\""
                )
        );
    }
}