package com.moveinsync.alertsystem.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a requested resource (e.g. an Alert by UUID) does not exist.
 *
 * <p>
 * Annotated with {@code @ResponseStatus(404)} so Spring MVC returns the
 * correct HTTP status even without an explicit handler, but the
 * {@link GlobalExceptionHandler} also catches it to produce a structured JSON
 * body.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String resourceName, Object id) {
        super(resourceName + " not found with id: " + id);
    }
}
