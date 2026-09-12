package br.com.eventflow.payment;

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
    private final RegistrationRepository registrationRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaymentService(
            PaymentRepository paymentRepository,
            RegistrationRepository registrationRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.paymentRepository = paymentRepository;
        this.registrationRepository = registrationRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public PaymentResponse processPayment(
            Long registrationId,
            Long participantId
    ) {
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

        if (paymentRepository
                .existsByRegistration_RegistrationId(registrationId)) {

            throw new ConflictException(
                    "Payment already exists for this registration"
            );
        }

        Payment payment =
                new Payment(
                        registration,
                        registration
                                .getEvent()
                                .getPrice()
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
