package com.stockguard.security.jwt;

import com.stockguard.user.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Generates, signs, and validates JSON Web Tokens (JWT) using HMAC-SHA256.
 * Enforces security validations, profile-specific checks, and claim extraction.
 */
@Component
public class JwtTokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);
    private static final String DEFAULT_DEV_SECRET = "stockguard_default_dev_secret_key_replace_in_production_32chars!";

    private final Environment environment;

    @Value("${stockguard.jwt.secret}")
    private String jwtSecret;

    @Value("${stockguard.jwt.expiration-ms:86400000}")
    private long jwtExpirationMs;

    private SecretKey signingKey;

    public JwtTokenProvider(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void init() {
        boolean isProduction = environment.acceptsProfiles(Profiles.of("prod", "production"));

        if (isProduction) {
            if (jwtSecret == null || jwtSecret.isBlank() || DEFAULT_DEV_SECRET.equals(jwtSecret.trim())) {
                throw new IllegalStateException(
                        "CRITICAL SECURITY VIOLATION: Production JWT secret is not configured or is using default placeholder. " +
                                "Configure JWT_SECRET in environment variables with at least 256-bit entropy."
                );
            }
            if (jwtSecret.trim().length() < 32) {
                throw new IllegalStateException(
                        "CRITICAL SECURITY VIOLATION: Production JWT secret must be at least 32 characters (256 bits)."
                );
            }
        }

        if (jwtSecret == null || jwtSecret.trim().length() < 32) {
            throw new IllegalStateException(
                    "JWT secret must be at least 32 characters (256 bits) for HMAC-SHA256 encryption."
            );
        }

        this.signingKey = Keys.hmacShaKeyFor(jwtSecret.trim().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Generates a signed JWT token containing email, role, and userId claims.
     */
    public String generateTokenFromEmail(String email, Role role, Long userId) {
        Instant now = Instant.now();
        Instant expiryDate = now.plusMillis(jwtExpirationMs);

        return Jwts.builder()
                .subject(email)
                .claim("userId", userId)
                .claim("role", role != null ? role.name() : Role.CUSTOMER.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiryDate))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Validates signature, structure, and expiration of the provided JWT string.
     *
     * @param token JWT token string
     * @return true if token is structurally sound, correctly signed, and not expired
     */
    public boolean validateToken(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }

        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SecurityException ex) {
            log.warn("Invalid JWT signature: {}", ex.getMessage());
        } catch (MalformedJwtException ex) {
            log.warn("Malformed JWT token: {}", ex.getMessage());
        } catch (ExpiredJwtException ex) {
            log.warn("Expired JWT token: {}", ex.getMessage());
        } catch (UnsupportedJwtException ex) {
            log.warn("Unsupported JWT token: {}", ex.getMessage());
        } catch (IllegalArgumentException ex) {
            log.warn("JWT claims string is empty or invalid: {}", ex.getMessage());
        }

        return false;
    }

    /**
     * Extracts the subject (email) from a validated token.
     */
    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject();
    }

    /**
     * Extracts the Role from token claims.
     */
    public Role getRoleFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        String roleStr = claims.get("role", String.class);
        return roleStr != null ? Role.valueOf(roleStr) : Role.CUSTOMER;
    }

    /**
     * Extracts the User ID from token claims.
     */
    public Long getUserIdFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        Number userId = claims.get("userId", Number.class);
        return userId != null ? userId.longValue() : null;
    }

    public Claims getClaimsFromToken(String token) {
        Jws<Claims> jwsClaims = Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token);
        return jwsClaims.getPayload();
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }
}
