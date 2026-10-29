package br.com.eventflow.payment;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.event.EventStatus;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.config.RabbitMqConfig;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PaymentEligibilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PaymentService paymentService;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AmqpAdmin amqpAdmin;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @BeforeEach
    void clearQueue() {
        amqpAdmin.purgeQueue(RabbitMqConfig.REGISTRATION_EVENTS_QUEUE, false);
    }

    @ParameterizedTest
    @EnumSource(value = EventStatus.class, names = {"DRAFT", "CANCELLED", "FINISHED"})
    void shouldRejectNonPublishedEventWithoutPersistingOrPublishingPayment(EventStatus status) {
        Registration registration = reservation(false);
        jdbcTemplate.update("UPDATE events SET status = ? WHERE event_id = ?",
                status.name(), registration.getEvent().getEventId());

        assertThrows(ConflictException.class, () -> paymentService.processPayment(
                registration.getRegistrationId(), registration.getParticipant().getUserId()
        ));

        assertNoConfirmation(registration.getRegistrationId());
    }

    @Test
    void shouldRejectStartedEventWithoutPersistingOrPublishingPayment() {
        Registration registration = reservation(true);

        assertThrows(ConflictException.class, () -> paymentService.processPayment(
                registration.getRegistrationId(), registration.getParticipant().getUserId()
        ));

        assertNoConfirmation(registration.getRegistrationId());
    }

    @Test
    void shouldRollbackPaymentAndConfirmationWithoutPublishingMessage() {
        Registration registration = reservation(false);

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            paymentService.processPayment(
                    registration.getRegistrationId(), registration.getParticipant().getUserId()
            );
            status.setRollbackOnly();
        });

        assertNoConfirmation(registration.getRegistrationId());
    }

    private void assertNoConfirmation(Long registrationId) {
        assertFalse(paymentRepository.existsByRegistration_RegistrationId(registrationId));
        assertEquals(RegistrationStatus.PENDING,
                registrationRepository.findById(registrationId).orElseThrow().getStatus());
        assertNull(rabbitTemplate.receive(RabbitMqConfig.REGISTRATION_EVENTS_QUEUE, 1_000));
    }

    private Registration reservation(boolean started) {
        User organizer = user(UserRole.ORGANIZER);
        User participant = user(UserRole.PARTICIPANT);
        Event event = new Event(
                organizer, "Payment eligibility", "Description", "Petrópolis",
                started ? OffsetDateTime.now().minusDays(1) : OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(8), 100, new BigDecimal("50.00")
        );
        event.publish(OffsetDateTime.now().minusDays(2));
        event = eventRepository.saveAndFlush(event);
        return registrationRepository.saveAndFlush(new Registration(
                event, participant, OffsetDateTime.now().plusMinutes(15)
        ));
    }

    private User user(UserRole role) {
        return userRepository.saveAndFlush(new User(
                "Payment eligibility", UUID.randomUUID() + "@example.com", "encoded-password", role
        ));
    }
}
