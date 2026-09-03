package br.com.eventflow.auth;

import br.com.eventflow.user.UserRole;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAuthenticationFilterTest {

    private final JwtService jwtService = mock(JwtService.class);

    private final JwtAuthenticationFilter filter =
            new JwtAuthenticationFilter(
                    jwtService,
                    "eventflow_token"
            );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateRequestWhenJwtCookieIsValid() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setCookies(
                new Cookie("eventflow_token", "valid-token")
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        when(jwtService.extractUserId("valid-token"))
                .thenReturn(10L);

        when(jwtService.extractRole("valid-token"))
                .thenReturn(UserRole.PARTICIPANT);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNotNull(authentication);
        assertEquals(10L, authentication.getPrincipal());
        assertTrue(authentication.isAuthenticated());

        assertTrue(
                authentication.getAuthorities()
                        .stream()
                        .anyMatch(authority ->
                                authority
                                        .getAuthority()
                                        .equals("ROLE_PARTICIPANT")
                        )
        );

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenCookieIsMissing()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNull(authentication);

        verifyNoInteractions(jwtService);

        verify(filterChain)
                .doFilter(request, response);
    }

    @Test
    void shouldContinueWithoutAuthenticationWhenJwtIsInvalid()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setCookies(
                new Cookie("eventflow_token", "invalid-token")
        );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        FilterChain filterChain = mock(FilterChain.class);

        when(jwtService.extractUserId("invalid-token"))
                .thenThrow(new JwtException("Invalid token"));

        filter.doFilter(
                request,
                response,
                filterChain
        );

        Authentication authentication =
                SecurityContextHolder
                        .getContext()
                        .getAuthentication();

        assertNull(authentication);

        verify(filterChain)
                .doFilter(request, response);
    }
}
