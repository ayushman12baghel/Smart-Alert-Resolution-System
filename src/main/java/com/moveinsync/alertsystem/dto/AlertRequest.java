package com.moveinsync.alertsystem.dto;

import com.moveinsync.alertsystem.enums.AlertSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

/**
 * Inbound DTO for the {@code POST /api/alerts} endpoint.
 *
 * <p>
 * Decouples the API surface from the
 * {@link com.moveinsync.alertsystem.entity.Alert}
 * JPA entity so that database concerns (IDs, timestamps, status defaults) are
 * never
 * exposed or overridable by the caller. The controller maps this to an
 * {@code Alert}
 * entity before delegating to {@code AlertService}.
 *
 * @param driverId   the driver associated with the alert (required)
 * @param sourceType the originating system/sensor (e.g.
 *                   {@code "SPEED_MONITOR"}) (required)
 * @param severity   the initial severity level asserted by the source
 *                   (required)
 * @param metadata   optional free-form key-value payload stored as JSONB
 */
public record AlertRequest(

        @NotBlank(message = "driverId must not be blank") String driverId,

        @NotBlank(message = "sourceType must not be blank") String sourceType,

        @NotNull(message = "severity must not be null") AlertSeverity severity,

        Map<String, Object> metadata) {
}
