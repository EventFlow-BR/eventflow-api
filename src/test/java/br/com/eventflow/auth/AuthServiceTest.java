package br.com.eventflow.auth;

import br.com.eventflow.auth.dto.RegisterUserRequest;
import br.com.eventflow.auth.dto.RegisterUserResponse;
import br.com.eventflow.shared.exception.ConflictException;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
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

        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        ConflictException exception = assertThrows(
                ConflictException.class,
                () -> authService.register(request)
        );

        assertEquals(
                "Email is already registered",
                exception.getMessage()
        );
    }
}
