package br.com.eventflow.registration;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RegistrationExpirationScheduler {

    private final RegistrationExpirationService expirationService;

    public RegistrationExpirationScheduler(
            RegistrationExpirationService registrationExpirationService
    ) {
        this.expirationService = registrationExpirationService;
    }

    @Scheduled(
            fixedDelayString =
                    "${app.registration.expiration-check-interval-ms}"
    )
    public void expirePendingRegistration() {
        expirationService
                .expirePendingRegistrations();
    }
}
