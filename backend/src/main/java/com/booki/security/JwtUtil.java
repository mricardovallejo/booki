package com.booki.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    /** The value shipped in application.yml / .env.example — never acceptable for signing real tokens. */
    static final String DEV_PLACEHOLDER_SECRET = "change-me-in-production-this-is-a-dev-secret-32bytes";

    private final SecretKey key;
    private final long expirationMs;

    public JwtUtil(@Value("${booki.jwt.secret:}") String secret,
                   @Value("${booki.jwt.expiration-ms}") long expirationMs) {
        this.key = resolveKey(secret);
        this.expirationMs = expirationMs;
    }

    /**
     * A configured secret is used as-is. A missing / placeholder / too-short one
     * is replaced by a random ephemeral key with a loud warning: `bootRunLocal`
     * and the test suite keep working with zero setup, while a real deployment
     * (which must set {@code JWT_SECRET}) is never signing tokens anyone could
     * forge. The ephemeral key does not survive a restart and is per-instance —
     * fine for dev, unusable in production, which is the point.
     */
    private static SecretKey resolveKey(String secret) {
        boolean usable = secret != null
                && !secret.isBlank()
                && !secret.equals(DEV_PLACEHOLDER_SECRET)
                && secret.getBytes(StandardCharsets.UTF_8).length >= 32;
        if (usable) {
            return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
        log.warn("""

                ================================================================
                 booki.jwt.secret is unset, the built-in placeholder, or < 32
                 bytes. Signing tokens with a RANDOM ephemeral key instead:
                 sessions will not survive a restart and are invalid across
                 instances. Set the JWT_SECRET environment variable to a strong
                 random 32+ byte value before any shared or public deployment.
                ================================================================
                """);
        return Jwts.SIG.HS384.key().build();
    }

    public String generateToken(String email, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);
        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key)
                .compact();
    }

    public Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public String extractEmail(String token) {
        return parseToken(token).getSubject();
    }

    /**
     * @return the {@code userId} claim, or {@code null} if the token has no
     *         usable numeric claim (a malformed token never reaches here — the
     *         filter calls {@link #validateToken} first).
     */
    public Long extractUserId(String token) {
        // Read as Number, not Long: the configured JSON deserializer (jjwt-gson)
        // represents whole-number claims as Double, and JJWT won't auto-narrow
        // Double -> Long. Number.longValue() works regardless of which concrete
        // numeric type the deserializer produced.
        try {
            Number userId = parseToken(token).get("userId", Number.class);
            return userId != null ? userId.longValue() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }
}
