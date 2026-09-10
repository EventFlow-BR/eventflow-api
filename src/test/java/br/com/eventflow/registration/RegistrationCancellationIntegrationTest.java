package br.com.eventflow.registration;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.payment.Payment;
import br.com.eventflow.payment.PaymentRepository;
import br.com.eventflow.payment.enums.PaymentStatus;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("local")
class RegistrationCancellationIntegrationTest {

    @Autowired
    private RegistrationCancellationService cancellationService;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCancelConfirmedRegistrationAndPersistRefund() {
        String suffix = UUID.randomUUID().toString();

        User organizer = new User(
                "Cancellation Organizer",
                "cancellation-organizer-" + suffix + "@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        User participant = new User(
                "Cancellation Participant",
                "cancellation-participant-" + suffix + "@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        organizer = userRepository.saveAndFlush(organizer);
        participant = userRepository.saveAndFlush(participant);

        Event event = new Event(
                organizer,
                "Cancellation Integration Event",
                "Cancellation integration test",
                "Petrópolis",
                OffsetDateTime.now().plusDays(7),
                OffsetDateTime.now()
                        .plusDays(7)
                        .plusHours(8),
                100,
                new BigDecimal("80.00")
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

        registration.confirm();

        registration =
                registrationRepository
                        .saveAndFlush(registration);

        Payment payment =
                new Payment(
                        registration,
                        new BigDecimal("80.00")
                );

        payment =
                paymentRepository
                        .saveAndFlush(payment);

        Long registrationId =
                registration.getRegistrationId();

        Long paymentId =
                payment.getPaymentId();

        Long participantId =
                participant.getUserId();

        cancellationService.cancelRegistration(
                registrationId,
                participantId
        );

        Registration persistedRegistration =
                registrationRepository
                        .findById(registrationId)
                        .orElseThrow();

        Payment persistedPayment =
                paymentRepository
                        .findById(paymentId)
                        .orElseThrow();

        assertEquals(
                RegistrationStatus.CANCELLED,
                persistedRegistration.getStatus()
        );

        assertEquals(
                PaymentStatus.REFUNDED,
                persistedPayment.getStatus()
        );

        assertEquals(
                new BigDecimal("80.00"),
                persistedPayment.getAmount()
        );
    }
}