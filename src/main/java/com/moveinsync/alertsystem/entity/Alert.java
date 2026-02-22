package com.moveinsync.alertsystem.entity;

import com.moveinsync.alertsystem.enums.AlertSeverity;
import com.moveinsync.alertsystem.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Represents an alert ingested from a vehicle/driver event source.
 *
 * The {@code metadata} field is stored as a PostgreSQL JSONB column,
 * allowing flexible key-value payloads without altering the schema.
 */
@Entity
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    /**
     * Unique identifier for this alert, auto-generated as a UUID by the database.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * The system or module that produced this alert (e.g., "GPS_TRACKER",
     * "SPEED_MONITOR").
     */
    @Column(name = "source_type", nullable = false)
    private String sourceType;

    /**
     * Severity level of the alert: INFO, WARNING, or CRITICAL.
     * Stored as a string in the database for readability.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private AlertSeverity severity;

    /**
     * Current lifecycle status of the alert.
     * Stored as a string in the database for readability.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private AlertStatus status = AlertStatus.OPEN;

    /**
     * The instant at which this alert was created. Managed automatically by
     * Hibernate.
     */
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    /**
     * The driver associated with this alert.
     */
    @Column(name = "driver_id", nullable = false)
    private String driverId;

    /**
     * Arbitrary key-value metadata about the alert event (e.g., speed, location,
     * route).
     * Mapped to a PostgreSQL JSONB column via Hibernate 6 / Spring Boot 3.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata", columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    /**
     * Optimistic-locking version counter managed exclusively by Hibernate.
     *
     * <p>
     * Prevents lost-update anomalies when two concurrent requests (e.g., two
     * supervisors resolving the same escalated alert at the same millisecond)
     * both read the same row and then try to save conflicting changes. The second
     * writer will receive an
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * which the {@link com.moveinsync.alertsystem.exception.GlobalExceptionHandler}
     * converts to HTTP 409 Conflict.
     */
    @Version
    @Column(name = "version")
    private Long version;

    /**
     * Idempotency token that prevents duplicate alerts from being persisted when
     * the same event arrives more than once within the same 60-second window
     * (e.g., a SPEED_MONITOR burst or a client retry storm).
     *
     * <p>
     * Computed in {@link com.moveinsync.alertsystem.service.AlertService} as
     * {@code driverId + "::" + sourceType + "::" + (epochMs / 60_000)} before
     * the first {@code save()} call. The unique database constraint turns any
     * race-condition duplicate into a
     * {@link org.springframework.dao.DataIntegrityViolationException} that the
     * global handler maps to HTTP 409 Conflict.
     */
    @Column(name = "deduplication_key", unique = true, length = 255)
    private String deduplicationKey;
}
