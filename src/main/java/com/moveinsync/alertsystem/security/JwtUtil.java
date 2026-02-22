package com.moveinsync.alertsystem.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

/**
 * Utility component for all JWT operations: generation, parsing, and
 * validation.
 *
 * <h2>Algorithm</h2>
 * <p>
 * Uses HS256 (HMAC-SHA-256) signed with a Base64-encoded secret bound from
 * {@code jwt.secret} in {@code application.yml}. The secret must decode to
 * at least 256 bits (32 bytes).
 *
 * <h2>Token Lifetime</h2>
 * <p>
 * Controlled by {@code jwt.expiration-ms} (default: 86 400 000 ms = 24 h).
 */
@Component
public class JwtUtil {

    /** Base64-encoded HS256 secret, injected from application.yml. */
    @Value("${jwt.secret}")
    private String secret;

    /** Token validity window in milliseconds. */
    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    // -------------------------------------------------------------------------
    // Token Generation
    // -------------------------------------------------------------------------

    /**
     * Generates a signed JWT for the given username.
     *
     * @param username the subject to encode in the token
     * @return compact, URL-safe JWT string
     */
    public String generateToken(String username) {
        return Jwts.builder()
                .subject(username)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(getSigningKey())
                .compact();
    }

    // -------------------------------------------------------------------------
    // Token Parsing
    // -------------------------------------------------------------------------

    /**
     * Extracts the username (subject) from a JWT.
     *
     * @param token the JWT string
     * @return the subject claim value
     */
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    /**
     * Extracts a single claim from the JWT payload using the provided resolver.
     *
     * @param token          the JWT string
     * @param claimsResolver function to map Claims → T
     * @param <T>            the return type
     * @return the resolved claim value
     */
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        return claimsResolver.apply(extractAllClaims(token));
    }

    // -------------------------------------------------------------------------
    // Token Validation
    // -------------------------------------------------------------------------

    /**
     * Returns {@code true} if the token is structurally valid, correctly signed,
     * not expired, and belongs to {@code userDetails}.
     *
     * @param token       the JWT to validate
     * @param userDetails the principal to match against the token's subject
     * @return {@code true} if the token is valid for this user
     */
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private boolean isTokenExpired(String token) {
        return extractClaim(token, Claims::getExpiration).before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Decodes the Base64 secret and wraps it in a HMAC-SHA key suitable for
     * signing and verifying HS256 tokens.
     */
    private SecretKey getSigningKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
