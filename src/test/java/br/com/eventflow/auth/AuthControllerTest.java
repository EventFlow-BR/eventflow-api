package br.com.eventflow.auth;

import br.com.eventflow.auth.dto.*;
import br.com.eventflow.shared.exception.ConflictException;
import br.com.eventflow.shared.exception.GlobalExceptionHandler;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import br.com.eventflow.shared.exception.UnauthorizedException;
import org.springframework.test.context.TestPropertySource;
import br.com.eventflow.auth.dto.CurrentUserResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;


@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "app.auth.cookie-name=eventflow_token",
        "app.auth.cookie-secure=false"
})
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldRegisterParticipant() throws Exception {
        RegisterUserResponse response = new RegisterUserResponse(
                1L,
                "Samuel Gomes",
                "samuel@example.com",
                UserRole.PARTICIPANT
        );

        when(authService.register(any(RegisterUserRequest.class)))
                .thenReturn(response);

        String requestBody = """
                {
                  "name": "Samuel Gomes",
                  "email": "samuel@example.com",
                  "password": "strong-password",
                  "role": "PARTICIPANT"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Samuel Gomes"))
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void shouldReturnBadRequestWhenRegistrationRequestIsInvalid() throws Exception {
        String requestBody = """
                {
                  "name": "",
                  "email": "invalid-email",
                  "password": "123",
                  "role": null
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.role").exists());
    }

    @Test
    void shouldReturnConflictWhenEmailIsAlreadyRegistered() throws Exception {
        when(authService.register(any(RegisterUserRequest.class)))
                .thenThrow(new ConflictException("Email is already registered"));

        String requestBody = """
                {
                  "name": "Samuel Gomes",
                  "email": "samuel@example.com",
                  "password": "strong-password",
                  "role": "PARTICIPANT"
                }
                """;

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email is already registered"))
                .andExpect(jsonPath("$.path").value("/api/auth/register"));
    }

    @Test
    void shouldLoginAndReturnJwtInHttpOnlyCookie() throws Exception {
        LoginResponse response = new LoginResponse(
                1L,
                "Samuel Gomes",
                "samuel@example.com",
                UserRole.PARTICIPANT
        );

        LoginResult loginResult = new LoginResult(
                "signed-jwt-token",
                response
        );

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(loginResult);

        when(jwtService.getExpirationSeconds())
                .thenReturn(3600L);

        String requestBody = """
            {
              "email": "samuel@example.com",
              "password": "strong-password"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("eventflow_token=signed-jwt-token")
                ))
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("HttpOnly")
                ))
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("SameSite=Lax")
                ))
                .andExpect(header().string(
                        "Set-Cookie",
                        containsString("Path=/")
                ))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Samuel Gomes"))
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.token").doesNotExist())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(content().string(
                        not(containsString("signed-jwt-token"))
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        not(containsString("Secure"))
                ));
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(authService.login(any(LoginRequest.class)))
                .thenThrow(
                        new UnauthorizedException(
                                "Invalid email or password"
                        )
                );

        String requestBody = """
            {
              "email": "samuel@example.com",
              "password": "wrong-password"
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message")
                        .value("Invalid email or password"))
                .andExpect(jsonPath("$.path")
                        .value("/api/auth/login"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void shouldReturnBadRequestWhenLoginRequestIsInvalid() throws Exception {
        String requestBody = """
            {
              "email": "invalid-email",
              "password": ""
            }
            """;

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message")
                        .value("Request validation failed"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    void shouldReturnCurrentAuthenticatedUser() throws Exception {
        CurrentUserResponse response =
                new CurrentUserResponse(
                        10L,
                        "Samuel Gomes",
                        "samuel@example.com",
                        UserRole.PARTICIPANT
                );

        when(authService.getCurrentUser(10L))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/auth/me")
                                .principal(
                                        new UsernamePasswordAuthenticationToken(
                                                10L,
                                                null,
                                                List.of(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_PARTICIPANT"
                                                        )
                                                )
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.name").value("Samuel Gomes"))
                .andExpect(jsonPath("$.email").value("samuel@example.com"))
                .andExpect(jsonPath("$.role").value("PARTICIPANT"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist());

        verify(authService).getCurrentUser(10L);
    }

    @Test
    void shouldReturnUnauthorizedWhenAuthenticatedUserNoLongerExists()
            throws Exception {

        when(authService.getCurrentUser(10L))
                .thenThrow(
                        new UnauthorizedException(
                                "Authentication is no longer valid"
                        )
                );

        mockMvc.perform(
                        get("/api/auth/me")
                                .principal(
                                        new UsernamePasswordAuthenticationToken(
                                                10L,
                                                null,
                                                List.of(
                                                        new SimpleGrantedAuthority(
                                                                "ROLE_PARTICIPANT"
                                                        )
                                                )
                                        )
                                )
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldLogoutAndClearAuthenticationCookie() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("eventflow_token=")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("Max-Age=0")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("SameSite=Lax")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("Path=/")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        not(containsString("Secure"))
                ));
    }

    @Test
    void shouldLogoutWithoutReturningToken() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        not(containsString("Secure"))
                ));
    }
}
