package br.com.eventflow.auth;

import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class JwtServiceTest {

    private static final String SECRET =
            "eventflow-test-secret-key-with-at-least-32-bytes";

    private final JwtService jwtService =
            new JwtService(SECRET, 3_600_000);

    @Test
    void shouldGenerateTokenWithUserIdAndRole() {
        User user = new User(
                "Samuel Gomes",
                "samuel@example.com",
                "encoded-password",
                UserRole.PARTICIPANT
        );

        setUserIdForTest(user, 10L);

        String token = jwtService.generateToken(user);

        Claims claims = jwtService.parseToken(token);

        assertNotNull(token);
        assertEquals("10", claims.getSubject());
        assertEquals("PARTICIPANT", claims.get("role"));
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
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
