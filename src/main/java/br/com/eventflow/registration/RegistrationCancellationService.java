package br.com.eventflow.registration;

import br.com.eventflow.payment.Payment;
import br.com.eventflow.payment.PaymentRepository;
import br.com.eventflow.payment.enums.PaymentStatus;
import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class RegistrationCancellationService {

    private final RegistrationRepository registrationRepository;
    private final PaymentRepository paymentRepository;

    public RegistrationCancellationService(
            RegistrationRepository registrationRepository,
            PaymentRepository paymentRepository
    ) {
        this.registrationRepository = registrationRepository;
        this.paymentRepository = paymentRepository;
    }

    @Transactional
    public void cancelRegistration(
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

        validateOwnership(
                registration,
                participantId
        );

        OffsetDateTime now =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);

        validateCancellation(
                registration,
                now
        );

        if (registration.getStatus()
                == RegistrationStatus.CONFIRMED) {

            refundPayment(registrationId);
        }

        registration.cancel();

        registrationRepository.flush();
    }

    private void validateOwnership(
            Registration registration,
            Long participantId
    ) {
        if (!registration
                .getParticipant()
                .getUserId()
                .equals(participantId)) {

            throw new NotFoundException(
                    "Registration not found"
            );
        }
    }

    private void validateCancellation(
            Registration registration,
            OffsetDateTime now
    ) {
        if (!now.isBefore(
                registration
                        .getEvent()
                        .getStartDate()
        )) {

            throw new ConflictException(
                    "Registration cannot be cancelled after the event has started"
            );
        }

        if (registration.getStatus() == RegistrationStatus.PENDING
                && !registration
                .getReservationExpiresAt()
                .isAfter(now)) {

            throw new ConflictException(
                    "Registration reservation has expired"
            );
        }

        if (registration.getStatus()
                != RegistrationStatus.PENDING
                && registration.getStatus()
                != RegistrationStatus.CONFIRMED) {

            throw new ConflictException(
                    "Registration cannot be cancelled in its current status"
            );
        }
    }

    private void refundPayment(
            Long registrationId
    ) {
        Payment payment =
                paymentRepository
                        .findByRegistration_RegistrationId(
                                registrationId
                        )
                        .orElseThrow(() ->
                                new ConflictException(
                                        "Approved payment not found for confirmed registration"
                                )
                        );

        if (payment.getStatus()
                != PaymentStatus.APPROVED) {

            throw new ConflictException(
                    "Payment cannot be refunded in its current status"
            );
        }

        payment.refund();

        paymentRepository.flush();
    }
}