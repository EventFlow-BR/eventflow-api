package br.com.eventflow.payment;

import br.com.eventflow.event.Event;
import br.com.eventflow.event.EventRepository;
import br.com.eventflow.event.EventStatus;
import br.com.eventflow.payment.dto.PaymentResponse;
import br.com.eventflow.registration.Registration;
import br.com.eventflow.registration.RegistrationRepository;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.registration.event.RegistrationConfirmedEvent;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.NotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EventRepository eventRepository;
    private final RegistrationRepository registrationRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            EventRepository eventRepository,
            RegistrationRepository registrationRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.eventRepository = eventRepository;
        this.registrationRepository = registrationRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public PaymentResponse processPayment(
            Long registrationId,
            Long participantId
    ) {
        // Match reservation's event lock before acquiring the registration lock.
        // Load only the event here so registration state is read after both locks.
        Event event = eventRepository.findByRegistrationIdForUpdate(registrationId)
                .orElseThrow(() -> new NotFoundException("Registration not found"));

        Registration registration =
                registrationRepository
                        .findByIdForUpdate(registrationId)
                        .orElseThrow(() ->
                                new NotFoundException(
                                        "Registration not found"
                                )
                        );
        if (!registration
                .getParticipant()
                .getUserId()
                .equals(participantId)) {

            throw new NotFoundException(
                    "Registration not found"
            );
        }

        OffsetDateTime now =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);

        validateRegistrationForPayment(
                registration,
                now
        );

        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new ConflictException("Event is not available for payment");
        }

        if (!event.getStartDate().isAfter(now)) {
            throw new ConflictException("Event has already started");
        }

        if (paymentRepository
                .existsByRegistration_RegistrationId(registrationId)) {

            throw new ConflictException(
                    "Payment already exists for this registration"
            );
        }

        Payment payment =
                new Payment(
                        registration,
                        event.getPrice()
                );

        registration.confirm();

        applicationEventPublisher.publishEvent(
                new RegistrationConfirmedEvent(
                        registration.getRegistrationId(),
                        registration.getEvent().getEventId(),
                        registration.getParticipant().getUserId(),
                        now
                )
        );

        Payment savedPayment =
                paymentRepository.save(payment);

        registrationRepository.flush();
        paymentRepository.flush();

        return toResponse(savedPayment);
    }

    private void validateRegistrationForPayment(
            Registration registration,
            OffsetDateTime now
    ) {
        if (registration.getStatus()
                != RegistrationStatus.PENDING) {

            throw new ConflictException(
                    "Registration cannot be paid in its current status"
            );
        }

        if (!registration
                .getReservationExpiresAt()
                .isAfter(now)) {

            throw new ConflictException(
                    "Registration reservation has expired"
            );
        }
    }

    private PaymentResponse toResponse(
            Payment payment
    ) {
        return new PaymentResponse(
                payment.getPaymentId(),
                payment.getRegistration()
                        .getRegistrationId(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }
}
