package br.com.eventflow.auth;

import br.com.eventflow.auth.dto.LoginRequest;
import br.com.eventflow.auth.dto.LoginResponse;
import br.com.eventflow.auth.dto.LoginResult;
import br.com.eventflow.shared.exception.GlobalExceptionHandler;
import br.com.eventflow.user.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
@TestPropertySource(properties = {
        "app.auth.cookie-name=eventflow_token",
        "app.auth.cookie-secure=true"
})
class AuthControllerSecureCookieTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @Test
    void shouldCreateSecureCookieWhenSecureConfigurationIsEnabled()
            throws Exception {

        LoginResponse response = new LoginResponse(
                1L,
                "Samuel Gomes",
                "samuel@example.com",
                UserRole.PARTICIPANT
        );

        when(authService.login(any(LoginRequest.class)))
                .thenReturn(new LoginResult(
                        "signed-jwt-token",
                        response
                ));

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
                        HttpHeaders.SET_COOKIE,
                        containsString("Secure")
                ))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE,
                        containsString("HttpOnly")
                ));
    }
}
