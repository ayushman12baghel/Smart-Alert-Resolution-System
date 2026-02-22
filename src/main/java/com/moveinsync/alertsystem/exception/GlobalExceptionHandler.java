package com.moveinsync.alertsystem.exception;

import com.moveinsync.alertsystem.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for the Alert System REST API.
 *
 * <p>
 * Every handler returns a structured {@link ErrorResponse} JSON body so
 * clients never see a raw stack trace.
 *
 * <h2>Handled cases</h2>
 * <ul>
 * <li>{@link MethodArgumentNotValidException} — Bean Validation failure on a
 * request DTO ({@code @Valid}). Returns HTTP 400 with per-field messages.</li>
 * <li>{@link ResourceNotFoundException} — A requested entity was not found.
 * Returns HTTP 404.</li>
 * <li>{@link AlertStateException} — An operation is illegal in the alert's
 * current state (e.g. resolving an already-closed alert). Returns HTTP
 * 400.</li>
 * <li>{@link IllegalArgumentException} — A caller supplied a bad argument
 * (e.g. an unsupported filter string). Returns HTTP 400.</li>
 * <li>{@link Exception} — Catch-all fallback. Returns HTTP 500 with a safe
 * message; the full stack trace is only logged server-side.</li>
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

        private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

        // -------------------------------------------------------------------------
        // 400 — Validation errors (@Valid on DTOs)
        // -------------------------------------------------------------------------

        /**
         * Handles Bean Validation failures produced by {@code @Valid} on request
         * bodies. Collects every field error into a {@code Map<field, message>}
         * and returns all of them in a single 400 response.
         */
        @ExceptionHandler(MethodArgumentNotValidException.class)
        public ResponseEntity<ErrorResponse> handleValidation(
                        MethodArgumentNotValidException ex,
                        HttpServletRequest request) {

                Map<String, String> fieldErrors = ex.getBindingResult()
                                .getFieldErrors()
                                .stream()
                                .collect(Collectors.toMap(
                                                FieldError::getField,
                                                fe -> fe.getDefaultMessage() != null
                                                                ? fe.getDefaultMessage()
                                                                : "invalid value",
                                                // Keep the first message if a field has multiple violations
                                                (first, second) -> first));

                log.debug("Validation failed for {}: {}", request.getRequestURI(), fieldErrors);

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.ofValidation(
                                                HttpStatus.BAD_REQUEST.value(),
                                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                                "Request validation failed. Check the 'fields' object for details.",
                                                request.getRequestURI(),
                                                fieldErrors));
        }

        // -------------------------------------------------------------------------
        // 400 — Illegal state / bad argument
        // -------------------------------------------------------------------------

        /**
         * Handles attempts to perform an operation that is invalid for the alert's
         * current status (e.g. resolving an already-resolved alert).
         */
        @ExceptionHandler(AlertStateException.class)
        public ResponseEntity<ErrorResponse> handleAlertState(
                        AlertStateException ex,
                        HttpServletRequest request) {

                log.warn("AlertStateException at {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                HttpStatus.BAD_REQUEST.value(),
                                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        /**
         * Handles bad caller arguments such as an unsupported filter string.
         */
        @ExceptionHandler(IllegalArgumentException.class)
        public ResponseEntity<ErrorResponse> handleIllegalArgument(
                        IllegalArgumentException ex,
                        HttpServletRequest request) {

                log.warn("IllegalArgumentException at {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                HttpStatus.BAD_REQUEST.value(),
                                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        // -------------------------------------------------------------------------
        // 404 — Resource not found
        // -------------------------------------------------------------------------

        /**
         * Handles lookups for alerts or other resources that do not exist.
         */
        @ExceptionHandler(ResourceNotFoundException.class)
        public ResponseEntity<ErrorResponse> handleNotFound(
                        ResourceNotFoundException ex,
                        HttpServletRequest request) {

                log.info("ResourceNotFoundException at {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.NOT_FOUND)
                                .body(ErrorResponse.of(
                                                HttpStatus.NOT_FOUND.value(),
                                                HttpStatus.NOT_FOUND.getReasonPhrase(),
                                                ex.getMessage(),
                                                request.getRequestURI()));
        }

        // -------------------------------------------------------------------------
        // 409 — Duplicate alert (idempotency key collision)
        // -------------------------------------------------------------------------

        /**
         * Handles a unique-constraint violation on {@code deduplication_key}.
         *
         * <p>
         * This happens when two identical (driverId + sourceType) events arrive
         * within the same 60-second window and both hit the database at the same
         * instant — a true race condition. The second write loses gracefully.
         *
         * <p>
         * {@link com.moveinsync.alertsystem.service.AlertService} already handles
         * the common case by returning the existing alert idempotently, so this
         * handler is a last-resort safety net for any other integrity violation.
         */
        @ExceptionHandler(DataIntegrityViolationException.class)
        public ResponseEntity<ErrorResponse> handleDuplicateKey(
                        DataIntegrityViolationException ex,
                        HttpServletRequest request) {

                log.warn("DataIntegrityViolation at {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.of(
                                                HttpStatus.CONFLICT.value(),
                                                HttpStatus.CONFLICT.getReasonPhrase(),
                                                "A duplicate alert was detected. " +
                                                                "An identical event for this driver already exists within the current minute.",
                                                request.getRequestURI()));
        }

        // -------------------------------------------------------------------------
        // 409 — Optimistic locking failure (concurrent state change)
        // -------------------------------------------------------------------------

        /**
         * Handles Hibernate's optimistic locking failure.
         *
         * <p>
         * Thrown when two concurrent requests both read the same {@link
         * com.moveinsync.alertsystem.entity.Alert} row and then both try to write
         * it. Hibernate detects the stale {@code @Version} counter on the second
         * write and throws this exception. The losing client should retry.
         */
        @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
        public ResponseEntity<ErrorResponse> handleOptimisticLock(
                        ObjectOptimisticLockingFailureException ex,
                        HttpServletRequest request) {

                log.warn("Optimistic locking failure at {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.CONFLICT)
                                .body(ErrorResponse.of(
                                                HttpStatus.CONFLICT.value(),
                                                HttpStatus.CONFLICT.getReasonPhrase(),
                                                "The alert was concurrently modified by another request. " +
                                                                "Please re-fetch the resource and retry your operation.",
                                                request.getRequestURI()));
        }

        // -------------------------------------------------------------------------
        // 400 — Malformed or oversized JSON body
        // -------------------------------------------------------------------------

        /**
         * Handles malformed JSON in the request body (e.g., a syntax error,
         * an unknown enum value, or a body that exceeded the Tomcat
         * {@code max-swallow-size} limit causing Jackson to receive a truncated
         * stream).
         */
        @ExceptionHandler(HttpMessageNotReadableException.class)
        public ResponseEntity<ErrorResponse> handleUnreadableBody(
                        HttpMessageNotReadableException ex,
                        HttpServletRequest request) {

                log.warn("HttpMessageNotReadable at {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.BAD_REQUEST)
                                .body(ErrorResponse.of(
                                                HttpStatus.BAD_REQUEST.value(),
                                                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                                                "Request body is malformed, contains an invalid value, " +
                                                                "or exceeds the maximum allowed size (2 MB).",
                                                request.getRequestURI()));
        }

        // -------------------------------------------------------------------------
        // 413 — Multipart upload too large
        // -------------------------------------------------------------------------

        /**
         * Handles a multipart upload that exceeds the configured
         * {@code spring.servlet.multipart.max-request-size} limit.
         */
        @ExceptionHandler(MaxUploadSizeExceededException.class)
        public ResponseEntity<ErrorResponse> handleUploadTooLarge(
                        MaxUploadSizeExceededException ex,
                        HttpServletRequest request) {

                log.warn("Upload too large at {}: {}", request.getRequestURI(), ex.getMessage());

                return ResponseEntity
                                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                                .body(ErrorResponse.of(
                                                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                                                HttpStatus.PAYLOAD_TOO_LARGE.getReasonPhrase(),
                                                "Request payload exceeds the maximum allowed size of 1 MB.",
                                                request.getRequestURI()));
        }

        // -------------------------------------------------------------------------
        // 500 — Catch-all fallback
        // -------------------------------------------------------------------------

        /**
         * Catches any unhandled exception and returns a generic 500 response.
         * The real exception is logged server-side; only a safe message is sent
         * to the client.
         */
        @ExceptionHandler(Exception.class)
        public ResponseEntity<ErrorResponse> handleGeneric(
                        Exception ex,
                        HttpServletRequest request) {

                log.error("Unhandled exception at {}: {}", request.getRequestURI(), ex.getMessage(), ex);

                return ResponseEntity
                                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                                .body(ErrorResponse.of(
                                                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                                                HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                                                "An unexpected error occurred. Please try again later.",
                                                request.getRequestURI()));
        }
}
