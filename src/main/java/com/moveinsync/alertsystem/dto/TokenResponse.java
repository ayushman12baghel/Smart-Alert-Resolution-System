package com.moveinsync.alertsystem.dto;

/**
 * Response body for a successful login.
 *
 * <pre>
 * {
 *   "token": "eyJhbGciOiJIUzI1NiJ9...",
 *   "type":  "Bearer",
 *   "expiresIn": 86400000
 * }
 * </pre>
 *
 * @param token     the signed JWT to include in subsequent requests as
 *                  {@code Authorization: Bearer <token>}
 * @param type      always {@code "Bearer"}
 * @param expiresIn token lifetime in milliseconds
 */
public record TokenResponse(
        String token,
        String type,
        long expiresIn) {

    /** Convenience factory — builds a standard Bearer response. */
    public static TokenResponse of(String token, long expiresInMs) {
        return new TokenResponse(token, "Bearer", expiresInMs);
    }
}
