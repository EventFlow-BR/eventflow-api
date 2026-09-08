package br.com.eventflow.registration;

import br.com.eventflow.registration.dto.RegistrationResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events/{eventId}/registrations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(
            RegistrationService registrationService
    ) {
        this.registrationService = registrationService;
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> createReservation(
            @PathVariable Long eventId,
            Authentication authentication
    ) {
        Long participantId =
                (Long) authentication.getPrincipal();

        RegistrationResponse response =
                registrationService.createReservation(
                        eventId,
                        participantId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
