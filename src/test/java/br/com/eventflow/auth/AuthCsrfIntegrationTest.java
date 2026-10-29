package br.com.eventflow.auth;

import br.com.eventflow.testinfra.AbstractIntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class AuthCsrfIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRejectAuthenticatedWritesWithoutCsrf() throws Exception {
        Cookie jwt = registerAndLogin();

        mockMvc.perform(post("/api/events")
                        .cookie(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/events/1")
                        .cookie(jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/logout").cookie(jwt))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldCreateAndUpdateEventWithCsrfObtainedOverHttp() throws Exception {
        Cookie jwt = registerAndLogin();
        var csrf = obtainCsrf(jwt);

        var created = mockMvc.perform(post("/api/events")
                        .cookie(jwt, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest()))
                .andExpect(status().isCreated())
                .andReturn();

        long eventId = objectMapper.readTree(
                created.getResponse().getContentAsString()
        ).get("id").asLong();

        mockMvc.perform(put("/api/events/" + eventId)
                        .cookie(jwt, csrf.cookie())
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest().replace("CSRF Event", "Updated CSRF Event")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated CSRF Event"));
    }

    @Test
    void shouldRejectMissingOrInvalidCsrfHeader() throws Exception {
        Cookie jwt = registerAndLogin();
        var csrf = obtainCsrf(jwt);

        mockMvc.perform(post("/api/events")
                        .cookie(jwt, csrf.cookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events")
                        .cookie(jwt, csrf.cookie())
                        .header(csrf.headerName(), "invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest()))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/events")
                        .cookie(jwt)
                        .header(csrf.headerName(), csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(eventRequest()))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldClearJwtCookieOnLogoutWithRealCsrf() throws Exception {
        Cookie jwt = registerAndLogin();
        var csrf = obtainCsrf(jwt);

        var response = mockMvc.perform(post("/api/auth/logout")
                        .cookie(jwt, csrf.cookie())
                        .header(csrf.headerName(), csrf.token()))
                .andExpect(status().isNoContent())
                .andReturn().getResponse();

        Cookie cleared = response.getCookie("eventflow_token");
        assertNotNull(cleared);
        assertEquals(0, cleared.getMaxAge());
        assertTrue(cleared.isHttpOnly());
    }

    @Test
    void shouldRequireAuthenticationToObtainCsrf() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().is4xxClientError());
    }

    private Cookie registerAndLogin() throws Exception {
        String email = "csrf-" + UUID.randomUUID() + "@example.com";
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"CSRF Organizer","email":"%s",
                                 "password":"strong-password","role":"ORGANIZER"}
                                """.formatted(email)))
                .andExpect(status().isCreated());

        var response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"strong-password"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse();

        Cookie jwt = response.getCookie("eventflow_token");
        assertNotNull(jwt);
        assertTrue(jwt.isHttpOnly());
        assertTrue(jwt.getSecure());
        return jwt;
    }

    private CsrfCredentials obtainCsrf(Cookie jwt) throws Exception {
        var response = mockMvc.perform(get("/api/auth/csrf").cookie(jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andReturn().getResponse();

        Cookie csrfCookie = response.getCookie("XSRF-TOKEN");
        assertNotNull(csrfCookie);
        assertTrue(csrfCookie.isHttpOnly());
        assertTrue(csrfCookie.getSecure());
        assertNull(response.getCookie("JSESSIONID"));
        var body = objectMapper.readTree(response.getContentAsString());
        return new CsrfCredentials(
                csrfCookie, body.get("headerName").asText(), body.get("token").asText()
        );
    }

    private String eventRequest() {
        return """
                {"name":"CSRF Event","description":"CSRF contract test",
                 "location":"Petrópolis","startDate":"2035-10-10T10:00:00Z",
                 "endDate":"2035-10-10T18:00:00Z","capacity":10,"price":50.00}
                """;
    }

    private record CsrfCredentials(Cookie cookie, String headerName, String token) {
    }
}
