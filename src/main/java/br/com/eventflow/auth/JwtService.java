package br.com.eventflow.auth;

import br.com.eventflow.user.User;
import br.com.eventflow.user.UserRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expiration;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.expiration}") long expiration
    ) {
        this.signingKey = Keys.hmacShaKeyFor(
                secret.getBytes(StandardCharsets.UTF_8)
        );
        this.expiration = expiration;
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusMillis(expiration);

        return Jwts.builder()
                .subject(user.getUserId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationSeconds() {
        return expiration / 1000;
    }

    public Long extractUserId(String token) {
        Claims claims = parseToken(token);

        return Long.valueOf(claims.getSubject());
    }

    public UserRole extractRole(String token) {
        Claims claims = parseToken(token);

        String role = claims.get("role", String.class);

        if (role == null) {
            throw new IllegalArgumentException(
                    "JWT role claim is missing"
            );
        }

        return UserRole.valueOf(role);
    }
}
