package com.moveinsync.alertsystem.service;

import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.enums.AlertSeverity;
import com.moveinsync.alertsystem.enums.AlertStatus;
import com.moveinsync.alertsystem.event.TransitionPublisher;
import com.moveinsync.alertsystem.exception.AlertStateException;
import com.moveinsync.alertsystem.exception.ResourceNotFoundException;
import com.moveinsync.alertsystem.repository.AlertRepository;
import com.moveinsync.alertsystem.rules.RuleEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Orchestrates the full alert lifecycle: persist → rule evaluation → re-persist if changed
 * → publish transition event for async audit write.
 *
 * The entire method runs in a single transaction. The {@link TransitionPublisher} fires after
 * both saves succeed; {@link com.moveinsync.alertsystem.event.AuditListener} is {@code @Async},
 * so the history write happens off the HTTP thread without blocking the response.
 */
@Service
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final RuleEngine ruleEngine;
    private final TransitionPublisher transitionPublisher;

    public AlertService(AlertRepository alertRepository,
            RuleEngine ruleEngine,
            TransitionPublisher transitionPublisher) {
        this.alertRepository = alertRepository;
        this.ruleEngine = ruleEngine;
        this.transitionPublisher = transitionPublisher;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Processes a brand-new alert end-to-end: persist → evaluate → re-persist
     * if changed → publish transition event if status changed.
     *
     * @param incomingAlert the alert populated by the controller (id and
     *                      timestamp will be assigned by the database)
     * @return the final, fully-persisted alert (after rule evaluation)
     */
    @Transactional
    public Alert processNewAlert(Alert incomingAlert) {

        incomingAlert.setStatus(AlertStatus.OPEN);

        // Deduplication: a composite unique index on (driverId, sourceType, minute_bucket)
        // prevents the same event being recorded twice. Concurrent duplicates hit the DB
        // constraint → DataIntegrityViolationException; we catch and return the existing
        // alert idempotently rather than surfacing a 500.
        String dedupKey = computeDeduplicationKey(
                incomingAlert.getDriverId(), incomingAlert.getSourceType());
        incomingAlert.setDeduplicationKey(dedupKey);

        Alert savedAlert;
        try {
            savedAlert = alertRepository.save(incomingAlert);
        } catch (DataIntegrityViolationException ex) {
            log.warn("Duplicate alert suppressed (deduplicationKey='{}') for driverId='{}' " +
                    "sourceType='{}'. Returning existing alert.",
                    dedupKey, incomingAlert.getDriverId(), incomingAlert.getSourceType());
            // Return the already-persisted alert — idempotent behaviour.
            return alertRepository.findByDeduplicationKey(dedupKey)
                    .orElseThrow(() -> ex); // constraint fired but row missing — propagate original
        }
        log.info("Alert persisted: id={}, driverId='{}', sourceType='{}', severity={}, status={}",
                savedAlert.getId(), savedAlert.getDriverId(),
                savedAlert.getSourceType(), savedAlert.getSeverity(), savedAlert.getStatus());

        AlertStatus statusBeforeEval = savedAlert.getStatus();
        AlertSeverity severityBeforeEval = savedAlert.getSeverity();

        Alert evaluatedAlert = ruleEngine.evaluate(savedAlert);

        boolean statusChanged = evaluatedAlert.getStatus() != statusBeforeEval;
        boolean severityChanged = evaluatedAlert.getSeverity() != severityBeforeEval;

        if (statusChanged || severityChanged) {
            evaluatedAlert = alertRepository.save(evaluatedAlert);
        }

        // Publish after both saves succeed so the audit listener gets a stable, persisted ID.
        if (statusChanged) {
            String reason = buildTransitionReason(evaluatedAlert);
            transitionPublisher.publish(
                    evaluatedAlert.getId(),
                    statusBeforeEval,
                    evaluatedAlert.getStatus(),
                    reason);
        }

        return evaluatedAlert;
    }

    @Transactional(readOnly = true)
    public Alert getById(UUID alertId) {
        return alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private String buildTransitionReason(Alert alert) {
        return String.format(
                "Rule engine escalation: sourceType='%s', driverId='%s', " +
                        "finalSeverity=%s, finalStatus=%s",
                alert.getSourceType(),
                alert.getDriverId(),
                alert.getSeverity(),
                alert.getStatus());
    }

    /**
     * Manually resolves an alert.
     *
     * RESOLVED and AUTO_CLOSED are terminal states — the state-machine guard below
     * throws {@link AlertStateException} (mapped to HTTP 409) to prevent double-resolution
     * races and accidental re-processing of system-closed alerts.
     */
    @Transactional
    public Alert resolveAlert(UUID alertId) {
        Alert alert = alertRepository.findById(alertId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert", alertId));

        AlertStatus previous = alert.getStatus();

        // State-machine guard: RESOLVED and AUTO_CLOSED are terminal states.
        if (previous == AlertStatus.RESOLVED) {
            throw new AlertStateException(
                    "Alert " + alertId + " is already RESOLVED and cannot be resolved again.");
        }
        if (previous == AlertStatus.AUTO_CLOSED) {
            throw new AlertStateException(
                    "Alert " + alertId + " is AUTO_CLOSED. Manually reopen it before resolving.");
        }

        alert.setStatus(AlertStatus.RESOLVED);
        Alert saved = alertRepository.save(alert);

        log.info("Alert {} manually resolved ({} → RESOLVED).", alertId, previous);

        transitionPublisher.publish(
                saved.getId(),
                previous,
                AlertStatus.RESOLVED,
                "Manual resolution via PUT /api/alerts/" + alertId + "/resolve");

        return saved;
    }

    public void deleteAllAlerts() {
        alertRepository.deleteAll();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the time-bucketed idempotency key for a (driverId, sourceType)
     * pair. Two events from the same driver and source that arrive within the
     * same 60-second window will produce the identical key, causing the DB
     * unique constraint to reject the second insert.
     *
     * <p>
     * Format: {@code "<driverId>::<sourceType>::<epochMinutes>"}
     */
    private String computeDeduplicationKey(String driverId, String sourceType) {
        long minuteBucket = System.currentTimeMillis() / 60_000L;
        return driverId + "::" + sourceType + "::" + minuteBucket;
    }
}
