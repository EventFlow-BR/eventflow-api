package br.com.eventflow.auth;

import br.com.eventflow.auth.dto.*;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.UnauthorizedException;
import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRepository;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                userRepository,
                passwordEncoder,
                jwtService
        );
    }

    @Test
    void shouldRegisterParticipantWithNormalizedEmailAndEncodedPassword() {
        RegisterUserRequest request = new RegisterUserRequest(
                " Samuel Gomes ",
                " Samuel@Example.COM ",
                "strong-password",
                UserRole.PARTICIPANT
        );

        when(userRepository.existsByEmail("samuel@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("strong-password"))
                .thenReturn("encoded-password");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegisterUserResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);

        verify(userRepository).saveAndFlush(userCaptor.capture());

        User savedUser = userCaptor.getValue();

        assertEquals("Samuel Gomes", savedUser.getName());
        assertEquals("samuel@example.com", savedUser.getEmail());
        assertEquals("encoded-password", savedUser.getPasswordHash());
        assertEquals(UserRole.PARTICIPANT, savedUser.getRole());

        assertEquals("Samuel Gomes", response.name());
        assertEquals("samuel@example.com", response.email());
        assertEquals(UserRole.PARTICIPANT, response.role());

        verify(passwordEncoder).encode("strong-password");
    }

    @Test
    void shouldRegisterOrganizer() {
        RegisterUserRequest request = new RegisterUserRequest(
                "Event Organizer",
                "organizer@example.com",
                "strong-password",
                UserRole.ORGANIZER
        );

        when(userRepository.existsByEmail("organizer@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("strong-password"))
                .thenReturn("encoded-password");

        when(userRepository.saveAndFlush(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        RegisterUserResponse response = authService.register(request);

        assertEquals(UserRole.ORGANIZER, response.role());
    }

    @Test
    void shouldRejectDuplicateEmail() {
        RegisterUserRequest request = new RegisterUserRequest(
                "Samuel Gomes",
                "SAMUEL@example.com",
                "strong-password",
                UserRole.PARTICIPANT
        );

        when(userRepository.existsByEmail("samuel@example.com"))
                .thenReturn(true);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> authService.register(request)
        );

        assertEquals(
                "Email is already registered",
                exception.getMessage()
        );

        verify(userRepository, never()).saveAndFlush(any());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void shouldRejectRegistrationWhenDatabaseDetectsDuplicateEmail() {
        RegisterUserRequest request = new RegisterUserRequest(
                "Samuel Gomes",
                "samuel@example.com",
                "strong-password",
                UserRole.PARTICIPANT
        );

        when(userRepository.existsByEmail("samuel@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("strong-password"))
                .thenReturn("encoded-password");

        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);

        when(constraintViolation.getConstraintName())
                .thenReturn("users_email_key");

        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException(
                        "Database constraint violation",
                        constraintViolation
                );

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(databaseException);

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> authService.register(request)
        );

        assertEquals(
                "Email is already registered",
                exception.getMessage()
        );
    }

    @Test
    void shouldNotConvertUnrelatedDatabaseConstraintViolationToDuplicateEmail() {
        RegisterUserRequest request = new RegisterUserRequest(
                "Samuel Gomes",
                "samuel@example.com",
                "strong-password",
                UserRole.PARTICIPANT
        );

        when(userRepository.existsByEmail("samuel@example.com"))
                .thenReturn(false);

        when(passwordEncoder.encode("strong-password"))
                .thenReturn("encoded-password");

        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);

        when(constraintViolation.getConstraintName())
                .thenReturn("chk_users_role");

        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException(
                        "Database constraint violation",
                        constraintViolation
                );

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(databaseException);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> authService.register(request)
        );

        assertEquals(databaseException, exception);
    }

    @Test
    void shouldLoginWithNormalizedEmailAndValidPassword() {
        LoginRequest request = new LoginRequest(
                " Samuel@Example.COM ",
                "strong-password"
        );

        User user = new User(
                "Samuel Gomes",
                "samuel@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        when(userRepository.findByEmail("samuel@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "strong-password",
                "encoded-password"
        )).thenReturn(true);

        when(jwtService.generateToken(user))
                .thenReturn("jwt-token");

        LoginResult result = authService.login(request);

        assertEquals("jwt-token", result.token());
        assertEquals("Samuel Gomes", result.user().name());
        assertEquals("samuel@example.com", result.user().email());
        assertEquals(UserRole.PARTICIPANT, result.user().role());

        verify(userRepository).findByEmail("samuel@example.com");
        verify(passwordEncoder).matches(
                "strong-password",
                "encoded-password"
        );
        verify(jwtService).generateToken(user);
    }

    @Test
    void shouldRejectLoginWhenEmailDoesNotExist() {
        LoginRequest request = new LoginRequest(
                "missing@example.com",
                "strong-password"
        );

        when(userRepository.findByEmail("missing@example.com"))
                .thenReturn(Optional.empty());

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request)
        );

        assertEquals(
                "Invalid email or password",
                exception.getMessage()
        );

        verify(passwordEncoder, never())
                .matches(anyString(), anyString());

        verify(jwtService, never())
                .generateToken(any());
    }

    @Test
    void shouldRejectLoginWhenPasswordIsInvalid() {
        LoginRequest request = new LoginRequest(
                "samuel@example.com",
                "wrong-password"
        );

        User user = new User(
                "Samuel Gomes",
                "samuel@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        when(userRepository.findByEmail("samuel@example.com"))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "wrong-password",
                "encoded-password"
        )).thenReturn(false);

        UnauthorizedException exception = assertThrows(
                UnauthorizedException.class,
                () -> authService.login(request)
        );

        assertEquals(
                "Invalid email or password",
                exception.getMessage()
        );

        verify(jwtService, never())
                .generateToken(any());
    }

    @Test
    void shouldReturnCurrentUser() {
        User user = new User(
                "Samuel Gomes",
                "samuel@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        when(userRepository.findById(10L))
                .thenReturn(Optional.of(user));

        CurrentUserResponse response =
                authService.getCurrentUser(10L);

        assertEquals(10L, response.id());
        assertEquals("Samuel Gomes", response.name());
        assertEquals("samuel@example.com", response.email());
        assertEquals(UserRole.PARTICIPANT, response.role());

        verify(userRepository).findById(10L);
    }

    @Test
    void shouldRejectCurrentUserWhenUserNoLongerExists() {
        when(userRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThrows(
                UnauthorizedException.class,
                () -> authService.getCurrentUser(10L)
        );
    }

    private void setUserIdForTest(User user, Long userId) {
        try {
            var field = User.class.getDeclaredField("userId");
            field.setAccessible(true);
            field.set(user, userId);
        } catch (ReflectiveOperationException exception) {
            throw new RuntimeException(exception);
        }
    }
}
