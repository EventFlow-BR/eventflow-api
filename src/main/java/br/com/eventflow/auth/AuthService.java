package br.com.eventflow.auth;

import br.com.eventflow.auth.dto.*;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import jakarta.transaction.Transactional;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
public class AuthService {

    private static final String USERS_EMAIL_UNIQUE_CONSTRAINT = "users_email_key";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterUserResponse register(RegisterUserRequest request) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new ConflictException("Email is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.password());

        User user = new User(
                request.name().trim(),
                normalizedEmail,
                passwordHash,
                request.role()
        );

        User savedUser;

        try {
            savedUser = userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isEmailUniqueConstraintViolation(exception)) {
                throw new ConflictException("Email is already registered");
            }

            throw exception;
        }

        return new RegisterUserResponse(
                savedUser.getUserId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole()
        );
    }

    private boolean isEmailUniqueConstraintViolation(
            DataIntegrityViolationException exception
    ) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException constraintViolation) {
                return USERS_EMAIL_UNIQUE_CONSTRAINT.equals(
                        constraintViolation.getConstraintName()
                );
            }

            cause = cause.getCause();
        }

        return false;
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String normalizedEmail = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new UnauthorizedException("Invalid email or password")
                );

        if(!passwordEncoder.matches(
                request.password(),
                user.getPasswordHash()
        )) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = jwtService.generateToken(user);

        LoginResponse response = new LoginResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );

        return new LoginResult(token, response);
    }

    public CurrentUserResponse getCurrentUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new UnauthorizedException("Authentication is no longer valid")
                );

        return new CurrentUserResponse(
                user.getUserId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );
    }
}
