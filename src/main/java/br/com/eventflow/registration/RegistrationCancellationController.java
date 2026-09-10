package br.com.eventflow.registration;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations/{registrationId}/cancel")
public class RegistrationCancellationController {

    private final RegistrationCancellationService cancellationService;

    public RegistrationCancellationController(
            RegistrationCancellationService cancellationService
    ) {
        this.cancellationService = cancellationService;
    }

    @PostMapping
    public ResponseEntity<Void> cancelRegistration(
            @PathVariable("registrationId") Long registrationId,
            Authentication authentication
    ) {
        Long participantId =
                (Long) authentication.getPrincipal();

        cancellationService.cancelRegistration(
                registrationId,
                participantId
        );

        return ResponseEntity.noContent().build();
    }
}