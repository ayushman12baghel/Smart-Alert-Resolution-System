package com.moveinsync.alertsystem.worker;

import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.enums.AlertStatus;
import com.moveinsync.alertsystem.event.TransitionPublisher;
import com.moveinsync.alertsystem.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Scheduled worker that auto-closes stale alerts.
 *
 * An alert is eligible if it has been OPEN or ESCALATED for more than
 * {@value #AUTO_CLOSE_AFTER_HOURS} hours with no manual resolution.
 *
 * The cron expression is externalised to {@code alert.auto-close.cron} in application.yml.
 * The method is idempotent: it only selects alerts in active states, so restarting
 * the job or running it twice in quick succession is safe.
 */
@Component
public class AutoCloseProcessor {

    private static final Logger log = LoggerFactory.getLogger(AutoCloseProcessor.class);

    /** How old (in hours) an unresolved alert must be before it is auto-closed. */
    static final int AUTO_CLOSE_AFTER_HOURS = 24;

    private static final List<AlertStatus> ACTIVE_STATUSES = List.of(AlertStatus.OPEN, AlertStatus.ESCALATED);

    private final AlertRepository alertRepository;
    private final TransitionPublisher transitionPublisher;

    public AutoCloseProcessor(AlertRepository alertRepository,
            TransitionPublisher transitionPublisher) {
        this.alertRepository = alertRepository;
        this.transitionPublisher = transitionPublisher;
    }

    @Scheduled(cron = "${alert.auto-close.cron:0 0/5 * * * ?}")
    @Transactional
    public void processStaleAlerts() {
        log.debug("AutoCloseProcessor: starting run at {}", Instant.now());

        List<Alert> activeAlerts = alertRepository.findAllByStatusIn(ACTIVE_STATUSES);

        if (activeAlerts.isEmpty()) {
            log.debug("AutoCloseProcessor: no active alerts found, skipping.");
            return;
        }

        Instant cutoff = Instant.now().minus(AUTO_CLOSE_AFTER_HOURS, ChronoUnit.HOURS);
        int closedCount = 0;

        for (Alert alert : activeAlerts) {
            if (alert.getTimestamp().isBefore(cutoff)) {
                AlertStatus previousStatus = alert.getStatus();

                alert.setStatus(AlertStatus.AUTO_CLOSED);
                alertRepository.save(alert);

                String reason = String.format(
                        "Auto-closed by scheduled worker: alert was %s for more than %d hours "
                                + "(created=%s, cutoff=%s)",
                        previousStatus, AUTO_CLOSE_AFTER_HOURS,
                        alert.getTimestamp(), cutoff);

                transitionPublisher.publish(
                        alert.getId(),
                        previousStatus,
                        AlertStatus.AUTO_CLOSED,
                        reason);

                log.info("Auto-closed alert id={}, driverId='{}', previousStatus={}",
                        alert.getId(), alert.getDriverId(), previousStatus);

                closedCount++;
            }
        }

        log.info("AutoCloseProcessor: run complete — {}/{} alerts auto-closed.",
                closedCount, activeAlerts.size());
    }
}
