package com.moveinsync.alertsystem.entity;

import com.moveinsync.alertsystem.enums.AlertStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record that captures every status transition an {@link Alert}
 * undergoes.
 *
 * This table acts as the source-of-truth for the full lifecycle history
 * of an alert and is populated exclusively by the {@code AuditListener}
 * via Spring Application Events (Observer Pattern).
 */
@Entity
@Table(name = "alert_transition_history", indexes = {
        @Index(name = "idx_transition_alert_id", columnList = "alert_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlertTransitionHistory {

    /**
     * Unique identifier for this history record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "history_id", updatable = false, nullable = false)
    private UUID historyId;

    /**
     * Reference to the parent alert. Stored as a plain UUID foreign key
     * to avoid eager-loading the full Alert graph on every history query.
     */
    @Column(name = "alert_id", nullable = false, updatable = false)
    private UUID alertId;

    /**
     * The status the alert held before this transition.
     * Null for the initial OPEN transition (i.e., brand-new alert).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status")
    private AlertStatus previousStatus;

    /**
     * The status the alert moved into as a result of this transition.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    private AlertStatus newStatus;

    /**
     * Human-readable explanation for why this transition occurred
     * (e.g., "Rule OverspeedingRuleStrategy triggered: 3 events in 60 min",
     * "Auto-closed by scheduled worker: no activity for 24h").
     */
    @Column(name = "transition_reason", length = 1024)
    private String transitionReason;

    /**
     * The instant this history record was persisted. Managed automatically by
     * Hibernate.
     */
    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;
}
