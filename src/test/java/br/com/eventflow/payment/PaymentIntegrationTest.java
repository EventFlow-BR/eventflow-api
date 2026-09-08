package br.com.eventflow.payment;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.payment.dto.PaymentResponse;
import br.com.eventflow.payment.enums.PaymentStatus;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
class PaymentIntegrationTest {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldPersistApprovedPaymentAndConfirmedRegistration() {
        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "Payment Organizer",
                "payment-organizer-" + suffix + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Payment Participant",
                "payment-participant-" + suffix + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        organizer = userRepository.saveAndFlush(organizer);
        participant = userRepository.saveAndFlush(participant);

        Event event = new Event(
                organizer,
                "Payment Integration Event",
                "Integration test event",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now().plusDays(7).plusHours(8),
                100,
                new BigDecimal("75.00")
        );

        event.publish(
                OffsetDateTime.now().minusDays(1)
        );

        event = eventRepository.saveAndFlush(event);

        Registration registration =
                new Registration(
                        event,
                        participant,
                        OffsetDateTime.now().plusMinutes(15)
                );

        registration =
                registrationRepository.saveAndFlush(
                        registration
                );

        Long registrationId =
                registration.getRegistrationId();

        Long participantId =
                participant.getUserId();

        PaymentResponse response =
                paymentService.processPayment(
                        registrationId,
                        participantId
                );

        assertEquals(
                PaymentStatus.APPROVED,
                response.status()
        );

        assertEquals(
                new BigDecimal("75.00"),
                response.amount()
        );

        Payment persistedPayment =
                paymentRepository
                        .findById(response.id())
                        .orElseThrow();

        Registration persistedRegistration =
                registrationRepository
                        .findById(registrationId)
                        .orElseThrow();

        assertEquals(
                PaymentStatus.APPROVED,
                persistedPayment.getStatus()
        );

        assertEquals(
                new BigDecimal("75.00"),
                persistedPayment.getAmount()
        );

        assertEquals(
                registrationId,
                persistedPayment
                        .getRegistration()
                        .getRegistrationId()
        );

        assertEquals(
                RegistrationStatus.CONFIRMED,
                persistedRegistration.getStatus()
        );
    }
}