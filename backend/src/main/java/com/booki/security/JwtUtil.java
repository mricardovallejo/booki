package com.booki.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

@Slf4j
@Component
public class JwtUtil {

    /** The value shipped in application.yml / .env.example — never acceptable for signing real tokens. */
    static final String DEV_PLACEHOLDER_SECRET = "change-me-in-production-this-is-a-dev-secret-32bytes";

    private static final long PASSWORD_RESET_VALIDITY_MS = 60 * 60 * 1000;
    private static final String RESET_USER_CLAIM = "resetUserId";
    private static final String RESET_FINGERPRINT_CLAIM = "passwordFingerprint";

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

    /**
     * The token inside a password-reset link. It has no {@code userId} claim, so
     * the auth filter never accepts it as a session, and session tokens lack the
     * reset claim, so they can't reset a password. It carries a fingerprint of the
     * current password hash: changing the password makes the fingerprint stop
     * matching, which is what makes a link single-use with nothing stored.
     */
    public String generatePasswordResetToken(Long userId, String passwordHash) {
        Date now = new Date();
        return Jwts.builder()
                .claim(RESET_USER_CLAIM, userId)
                .claim(RESET_FINGERPRINT_CLAIM, fingerprint(passwordHash))
                .issuedAt(now)
                .expiration(new Date(now.getTime() + PASSWORD_RESET_VALIDITY_MS))
                .signWith(key)
                .compact();
    }

    /** @return the account a valid, unexpired reset token is for, or {@code null} for anything else */
    public Long extractPasswordResetUserId(String token) {
        try {
            Number userId = parseToken(token).get(RESET_USER_CLAIM, Number.class);
            return userId != null ? userId.longValue() : null;
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    /** False once the password has changed since the link was issued. Call after {@link #extractPasswordResetUserId}. */
    public boolean matchesCurrentPassword(String resetToken, String passwordHash) {
        return fingerprint(passwordHash).equals(parseToken(resetToken).get(RESET_FINGERPRINT_CLAIM, String.class));
    }

    private static String fingerprint(String passwordHash) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(passwordHash.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
