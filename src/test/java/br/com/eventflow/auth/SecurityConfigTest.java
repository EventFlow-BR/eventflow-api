package br.com.eventflow.auth;

import br.com.eventflow.event.EventService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

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

    @MockitoBean
    private EventService eventService;

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

    @Test
    void shouldRejectProtectedEndpointWithMalformedJwtCookie()
            throws Exception {

        mockMvc.perform(
                        get("/test/authenticated")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                "not-a-valid-jwt"
                                        )
                                )
                )
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldRejectProtectedEndpointWithExpiredJwtCookie()
            throws Exception {

        JwtService expiredJwtService =
                new JwtService(
                        "eventflow-test-secret-key-with-at-least-32-bytes",
                        -1_000L
                );

        User user = new User(
                "Security User",
                "expired@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        String expiredToken =
                expiredJwtService.generateToken(user);

        mockMvc.perform(
                        get("/test/authenticated")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                expiredToken
                                        )
                                )
                )
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldRejectProtectedEndpointWithTamperedJwtCookie()
            throws Exception {

        User user = new User(
                "Security User",
                "tampered@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        String token = jwtService.generateToken(user);

        String tamperedToken =
                token.substring(0, token.length() - 1)
                        + (token.endsWith("a") ? "b" : "a");

        mockMvc.perform(
                        get("/test/authenticated")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                tamperedToken
                                        )
                                )
                )
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldAllowOrganizerToCreateEvent() throws Exception {
        User organizer = new User(
                "Organizer",
                "organizer@example.com",
                "encoded-password",
                UserRole.ORGANIZER
        );

        setUserIdForTest(organizer, 10L);

        String token = jwtService.generateToken(organizer);

        mockMvc.perform(
                        post("/api/events")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                token
                                        )
                                )
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validEventRequestBody())
                )
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();

                    if (status == 401 || status == 403) {
                        throw new AssertionError(
                                "Organizer should be authorized to create events, but returned " + status
                        );
                    }
                });
    }

    @Test
    void shouldRejectParticipantCreatingEvent() throws Exception {
        User participant = new User(
                "Participant",
                "participant@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(participant, 20L);

        String token = jwtService.generateToken(participant);

        mockMvc.perform(
                        post("/api/events")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                token
                                        )
                                )
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validEventRequestBody())
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnauthenticatedEventCreation() throws Exception {
        mockMvc.perform(
                        post("/api/events")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validEventRequestBody())
                )
                .andExpect(status().is4xxClientError());
    }

    @Test
    void shouldAllowOrganizerToUpdateEvent() throws Exception {
        String token = generateTokenFor(
                10L,
                UserRole.ORGANIZER
        );

        mockMvc.perform(
                        put("/api/events/100")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                token
                                        )
                                )
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Updated Event",
                                      "description": "Updated description",
                                      "location": "Petrópolis",
                                      "startDate": "2026-11-10T10:00:00-03:00",
                                      "endDate": "2026-11-10T18:00:00-03:00",
                                      "capacity": 100,
                                      "price": 50.00
                                    }
                                    """)
                )
                .andExpect(
                        result -> {
                            int status =
                                    result.getResponse().getStatus();

                            assertNotEquals(401, status);
                            assertNotEquals(403, status);
                        }
                );
    }

    @Test
    void shouldForbidParticipantFromUpdatingEvent()
            throws Exception {

        String token = generateTokenFor(
                20L,
                UserRole.PARTICIPANT
        );

        mockMvc.perform(
                        put("/api/events/100")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                token
                                        )
                                )
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                    {
                                      "name": "Updated Event",
                                      "description": "Updated description",
                                      "location": "Petrópolis",
                                      "startDate": "2026-11-10T10:00:00-03:00",
                                      "endDate": "2026-11-10T18:00:00-03:00",
                                      "capacity": 100,
                                      "price": 50.00
                                    }
                                    """)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowOrganizerToPublishEvent()
            throws Exception {

        String token = generateTokenFor(
                10L,
                UserRole.ORGANIZER
        );

        mockMvc.perform(
                        post("/api/events/100/publish")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                token
                                        )
                                )
                                .with(csrf())
                )
                .andExpect(
                        result -> {
                            int status =
                                    result.getResponse().getStatus();

                            assertNotEquals(401, status);
                            assertNotEquals(403, status);
                        }
                );
    }

    @Test
    void shouldForbidParticipantFromPublishingEvent()
            throws Exception {

        String token = generateTokenFor(
                20L,
                UserRole.PARTICIPANT
        );

        mockMvc.perform(
                        post("/api/events/100/publish")
                                .cookie(
                                        new Cookie(
                                                "eventflow_token",
                                                token
                                        )
                                )
                                .with(csrf())
                )
                .andExpect(status().isForbidden());
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

    private String validEventRequestBody() {
        return """
                {
                  "name": "Java Conference",
                  "description": "Backend conference",
                  "location": "Petrópolis",
                  "startDate": "2026-10-10T10:00:00-03:00",
                  "endDate": "2026-10-10T18:00:00-03:00",
                  "capacity": 100,
                  "price": 50.00
                }
                """;
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

    private String generateTokenFor(
            Long userId,
            UserRole role
    ) {
        User user = new User(
                "Test User",
                "test@example.com",
                "encoded-password",
                role
        );

        setUserIdForTest(user, userId);

        return jwtService.generateToken(user);
    }
}