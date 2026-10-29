package br.com.eventflow.event;

import br.com.eventflow.payment.Payment;
import br.com.eventflow.payment.PaymentRepository;
import br.com.eventflow.payment.enums.PaymentStatus;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.NotFoundException;
import br.com.eventflow.testinfra.AbstractIntegrationTest;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class EventCancellationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private EventService eventService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldCancelPublishedEventWithoutActiveRegistrations() {
        Event event = publishedEvent();

        var response = eventService.cancelEvent(
                event.getEventId(), event.getOrganizer().getUserId()
        );

        assertEquals(EventStatus.CANCELLED, response.status());
        assertEquals(EventStatus.CANCELLED,
                eventRepository.findById(event.getEventId()).orElseThrow().getStatus());
    }

    @ParameterizedTest
    @EnumSource(value = RegistrationStatus.class, names = {"PENDING", "CONFIRMED"})
    void shouldRejectCancellationWithoutChangingActiveRegistrationOrPayment(RegistrationStatus status) {
        Event event = publishedEvent();
        Registration registration = new Registration(
                event, user(UserRole.PARTICIPANT), OffsetDateTime.now().plusMinutes(15)
        );
        if (status == RegistrationStatus.CONFIRMED) {
            registration.confirm();
        }
        registration = registrationRepository.saveAndFlush(registration);
        if (status == RegistrationStatus.CONFIRMED) {
            paymentRepository.saveAndFlush(new Payment(registration, event.getPrice()));
        }

        ConflictException exception = assertThrows(ConflictException.class,
                () -> eventService.cancelEvent(event.getEventId(), event.getOrganizer().getUserId()));

        assertEquals("Event with active registrations cannot be cancelled", exception.getMessage());
        assertEquals(EventStatus.PUBLISHED,
                eventRepository.findById(event.getEventId()).orElseThrow().getStatus());
        assertEquals(status, registrationRepository.findById(
                registration.getRegistrationId()).orElseThrow().getStatus());
        if (status == RegistrationStatus.CONFIRMED) {
            assertEquals(PaymentStatus.APPROVED, paymentRepository
                    .findByRegistration_RegistrationId(registration.getRegistrationId())
                    .orElseThrow().getStatus());
        } else {
            assertFalse(paymentRepository.existsByRegistration_RegistrationId(
                    registration.getRegistrationId()));
        }
    }

    @Test
    void shouldIgnoreExpiredPendingReservationWithoutCancellingIt() {
        Event event = publishedEvent();
        Registration expired = registrationRepository.saveAndFlush(new Registration(
                event, user(UserRole.PARTICIPANT), OffsetDateTime.now().minusMinutes(1)
        ));

        var response = eventService.cancelEvent(
                event.getEventId(), event.getOrganizer().getUserId()
        );

        assertEquals(EventStatus.CANCELLED, response.status());
        assertEquals(RegistrationStatus.PENDING,
                registrationRepository.findById(expired.getRegistrationId()).orElseThrow().getStatus());
        assertFalse(paymentRepository.existsByRegistration_RegistrationId(expired.getRegistrationId()));
    }

    @Test
    void shouldHideEventWhenAnotherOrganizerTriesToCancel() {
        Event event = publishedEvent();
        User otherOrganizer = user(UserRole.ORGANIZER);

        assertThrows(NotFoundException.class, () -> eventService.cancelEvent(
                event.getEventId(), otherOrganizer.getUserId()
        ));
        assertEquals(EventStatus.PUBLISHED, event.getStatus());
    }

    private Event publishedEvent() {
        Event event = new Event(
                user(UserRole.ORGANIZER), "Cancellation", "Description", "Petrópolis",
                OffsetDateTime.now().plusDays(7), OffsetDateTime.now().plusDays(8),
                10, new BigDecimal("50.00")
        );
        event.publish(OffsetDateTime.now());
        return eventRepository.saveAndFlush(event);
    }

    private User user(UserRole role) {
        return userRepository.saveAndFlush(new User(
                "Cancellation", UUID.randomUUID() + "@example.com", "encoded-password", role
        ));
    }
}
