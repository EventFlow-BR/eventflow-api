package br.com.eventflow.registration;

import br.com.eventflow.registration.enums.RegistrationStatus;
import br.com.eventflow.registration.event.RegistrationExpiredEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RegistrationExpirationService {
    private final RegistrationRepository registrationRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    public RegistrationExpirationService(
            RegistrationRepository registrationRepository,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.registrationRepository = registrationRepository;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Transactional
    public int expirePendingRegistrations() {
        OffsetDateTime now =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);

        List<Registration> expiredRegistrations =
                registrationRepository
                        .findExpiredPendingRegistrations(
                                RegistrationStatus.PENDING,
                                now
                        );

        for (Registration registration : expiredRegistrations) {
            registration.expire();

            applicationEventPublisher.publishEvent(
                    new RegistrationExpiredEvent(
                            registration.getRegistrationId(),
                            registration.getEvent().getEventId(),
                            registration.getParticipant().getUserId(),
                            now
                    )
            );
        }

        return expiredRegistrations.size();
    }
}
