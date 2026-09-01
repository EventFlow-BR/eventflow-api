package br.com.eventflow.shared.exception;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/test/validation")
public class ValidationTestController {

    @PostMapping
    ResponseEntity<Void> validate(
            @Valid @RequestBody ValidationTestRequest request
    ) {
        return ResponseEntity.noContent().build();
    }

    record ValidationTestRequest(
            @NotBlank String name,
            @NotBlank @Email String email
    ) {
    }

    @GetMapping("/unexpected-error")
    ResponseEntity<Void> unexpectedError() {
        throw new IllegalStateException("Sensitive internal error");
    }
}
