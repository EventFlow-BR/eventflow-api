package br.com.eventflow.auth;

import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private static final String SECRET =
            "eventflow-test-secret-key-with-at-least-32-bytes";

    private static final long EXPIRATION_MILLISECONDS = 3_600_000L;

    private final JwtService jwtService =
            new JwtService(SECRET, EXPIRATION_MILLISECONDS);

    @Test
    void shouldGenerateTokenWithUserIdRoleAndConfiguredExpiration() {
        User user = new User(
                "Samuel Gomes",
                "samuel@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        String token = jwtService.generateToken(user);

        assertEquals(10L, jwtService.extractUserId(token));
        assertEquals(
                UserRole.PARTICIPANT,
                jwtService.extractRole(token)
        );

        Claims claims = jwtService.parseToken(token);

        assertNotNull(token);
        assertEquals("10", claims.getSubject());
        assertEquals("PARTICIPANT", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());

        long tokenDuration =
                claims.getExpiration().getTime()
                        - claims.getIssuedAt().getTime();

        assertEquals(EXPIRATION_MILLISECONDS, tokenDuration);
    }

    @Test
    void shouldRejectExpiredToken() {
        JwtService expiredJwtService =
                new JwtService(SECRET, -1_000L);

        User user = new User(
                "Samuel Gomes",
                "samuel@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        String token = expiredJwtService.generateToken(user);

        assertThrows(
                JwtException.class,
                () -> expiredJwtService.parseToken(token)
        );
    }

    @Test
    void shouldRejectTamperedToken() {
        User user = new User(
                "Samuel Gomes",
                "samuel@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        String token = jwtService.generateToken(user);

        String tamperedToken =
                token.substring(0, token.length() - 1)
                        + (token.endsWith("a") ? "b" : "a");

        assertThrows(
                JwtException.class,
                () -> jwtService.parseToken(tamperedToken)
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
