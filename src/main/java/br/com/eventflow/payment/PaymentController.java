package br.com.eventflow.payment;

import br.com.eventflow.payment.dto.PaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/registrations/{registrationId}/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(
            PaymentService paymentService
    ) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @PathVariable Long registrationId,
            Authentication authentication
    ) {
        Long participantId =
                (Long) authentication.getPrincipal();

        PaymentResponse response =
                paymentService.processPayment(
                        registrationId,
                        participantId
                );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }
}
