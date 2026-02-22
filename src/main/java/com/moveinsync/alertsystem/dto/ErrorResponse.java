package com.moveinsync.alertsystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardised error envelope returned by
 * {@link com.moveinsync.alertsystem.exception.GlobalExceptionHandler}
 * for every non-2xx response.
 *
 * <pre>
 * {
 *   "timestamp": "2026-02-22T10:15:30Z",
 *   "status":    404,
 *   "error":     "Not Found",
 *   "message":   "Alert not found with id: 3fa85f64-...",
 *   "path":      "/api/alerts/3fa85f64-...",
 *   "fields":    { "username": "must not be blank" }   ← only on validation errors
 * }
 * </pre>
 *
 * @param timestamp UTC instant when the error occurred
 * @param status    HTTP status code
 * @param error     HTTP reason phrase (e.g. "Not Found")
 * @param message   human-readable explanation
 * @param path      request URI that triggered the error
 * @param fields    per-field validation messages; {@code null} for
 *                  non-validation errors
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fields) {

    /** Convenience factory — no field-level detail. */
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(Instant.now(), status, error, message, path, null);
    }

    /** Convenience factory — includes per-field validation messages. */
    public static ErrorResponse ofValidation(int status, String error,
            String message, String path,
            Map<String, String> fields) {
        return new ErrorResponse(Instant.now(), status, error, message, path, fields);
    }
}
