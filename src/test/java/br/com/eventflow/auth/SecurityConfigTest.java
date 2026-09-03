package br.com.eventflow.auth;

import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@TestPropertySource(properties = {
        "app.jwt.secret=eventflow-test-secret-key-with-at-least-32-bytes",
        "app.jwt.expiration=3600000",
        "app.auth.cookie-name=eventflow_token",
        "app.auth.cookie-secure=false"
})
@Import(SecurityConfigTest.TestEndpointsConfiguration.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldAllowRegisterWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType("application/json")
                        .content("""
                                {
                                  "name": "Samuel Gomes",
                                  "email": "security-test@example.com",
                                  "password": "strong-password",
                                  "role": "PARTICIPANT"
                                }
                                """))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();

                    if (status == 401 || status == 403) {
                        throw new AssertionError(
                                "Register endpoint should be publicly accessible, but returned " + status
                        );
                    }
                });
    }

    @Test
    void shouldAllowLoginWithoutAuthentication() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "email": "missing@example.com",
                                  "password": "strong-password"
                                }
                                """))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();

                    if (status == 403) {
                        throw new AssertionError(
                                "Login endpoint should be publicly accessible, but returned 403"
                        );
                    }
                });
    }

    @Test
    void shouldAllowHealthWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldProtectOtherEndpoints() throws Exception {
        mockMvc.perform(get("/test/protected"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldProtectCurrentUserEndpointWithoutAuthentication()
            throws Exception {

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldAuthenticateProtectedEndpointWithValidJwtCookie()
            throws Exception {

        User user = new User(
                "Security User",
                "security@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        String token = jwtService.generateToken(user);

        mockMvc.perform(
                        get("/test/authenticated")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                token
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(content().string("10"));
    }

    @TestConfiguration
    static class TestEndpointsConfiguration {

        @Bean
        SecurityTestController securityTestController() {
            return new SecurityTestController();
        }
    }

    @RestController
    static class SecurityTestController {

        @GetMapping("/test/protected")
        ResponseEntity<Void> protectedEndpoint() {
            return ResponseEntity.ok().build();
        }

        @GetMapping("/test/authenticated")
        ResponseEntity<Long> authenticated(Authentication authentication) {
            return ResponseEntity.ok(
                    (Long) authentication.getPrincipal()
            );
        }
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
