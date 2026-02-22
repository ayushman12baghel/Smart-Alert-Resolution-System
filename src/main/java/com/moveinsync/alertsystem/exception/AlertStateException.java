package com.moveinsync.alertsystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when an operation is attempted on an alert whose current state
 * makes the operation invalid (e.g. resolving an already-resolved alert).
 *
 * <p>
 * Maps to HTTP 400 Bad Request.
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class AlertStateException extends RuntimeException {

    public AlertStateException(String message) {
        super(message);
    }
}
