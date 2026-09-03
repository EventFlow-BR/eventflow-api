package br.com.eventflow.auth;

import br.com.eventflow.auth.dto.*;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final String cookieName;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            @Value("${app.auth.cookie-name}") String cookieName,
            @Value("${app.auth.cookie-secure}") boolean cookieSecure
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.cookieName = cookieName;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterUserResponse> register(
            @Valid @RequestBody RegisterUserRequest request
    ) {
        RegisterUserResponse response = authService.register(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        LoginResult result = authService.login(request);
        ResponseCookie cookie = ResponseCookie
                .from(cookieName, result.token())
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ofSeconds(jwtService.getExpirationSeconds()))
                .build();

        return ResponseEntity
                .ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .body(result.user());
    }

    @GetMapping("/me")
    public ResponseEntity<CurrentUserResponse> me(
            Authentication authentication
    ) {
        Long userId = (Long) authentication.getPrincipal();

        CurrentUserResponse response =
                authService.getCurrentUser(userId);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        ResponseCookie cookie = ResponseCookie.from(cookieName, "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(Duration.ZERO)
                .build();

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .build();
    }
}
