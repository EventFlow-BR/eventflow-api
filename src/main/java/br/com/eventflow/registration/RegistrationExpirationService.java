package br.com.eventflow.registration;

import br.com.eventflow.registration.enums.RegistrationStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class RegistrationExpirationService {
    private final RegistrationRepository registrationRepository;

    public RegistrationExpirationService(
            RegistrationRepository registrationRepository
    ) {
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public int expirePendingRegistrations() {
        OffsetDateTime now =
                OffsetDateTime.now()
                        .truncatedTo(ChronoUnit.MICROS);

        List<Registration> expiredRegistration =
                registrationRepository
                        .findExpiredPendingRegistrations(
                                RegistrationStatus.PENDING,
                                now
                        );
        expiredRegistration.forEach(
                Registration::expire
        );

        return expiredRegistration.size();
    }
}
