package com.moveinsync.alertsystem.event;

import com.moveinsync.alertsystem.entity.AlertTransitionHistory;
import com.moveinsync.alertsystem.repository.AlertTransitionHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Observer (Listener) that persists an {@link AlertTransitionHistory} record
 * every time an {@link AlertTransitionEvent} is received.
 *
 * <h2>Design Notes</h2>
 * <ul>
 * <li><b>{@code @EventListener}</b>: Registers this method as a Spring
 * application-event subscriber. No XML, no manual registration required.</li>
 * <li><b>{@code @Async}</b>: The listener runs on a separate thread (from
 * Spring's default async executor). This means the calling thread — the
 * HTTP request thread in {@code AlertService} — is <em>not</em> blocked
 * waiting for the audit write to complete. This keeps API response latency
 * unaffected by database write contention on the history table.</li>
 * </ul>
 *
 * <p>
 * <b>Important:</b> {@code @Async} on an {@code @EventListener} requires that
 * {@code @EnableAsync} is present somewhere in the application context. This is
 * configured in {@link com.moveinsync.alertsystem.config.AppConfig}.
 *
 * <h2>Failure Handling</h2>
 * <p>
 * If the async write fails (e.g. DB unavailable), the exception is logged but
 * does <em>not</em> roll back the main alert transaction, which has already
 * committed. For production use, consider adding a dead-letter mechanism or
 * a compensating retry.
 */
@Component
public class AuditListener {

    private static final Logger log = LoggerFactory.getLogger(AuditListener.class);

    private final AlertTransitionHistoryRepository historyRepository;

    public AuditListener(AlertTransitionHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    /**
     * Handles an {@link AlertTransitionEvent} by persisting a corresponding
     * {@link AlertTransitionHistory} record.
     *
     * <p>
     * Runs asynchronously on a thread-pool thread — see class-level Javadoc
     * for the rationale.
     *
     * @param event the published transition event (never null; Spring guarantees
     *              delivery)
     */
    @Async
    @EventListener
    public void onAlertTransition(AlertTransitionEvent event) {
        log.debug("AuditListener received event async: alertId={}, {} → {}",
                event.alertId(), event.previousStatus(), event.newStatus());

        try {
            AlertTransitionHistory history = AlertTransitionHistory.builder()
                    .alertId(event.alertId())
                    .previousStatus(event.previousStatus())
                    .newStatus(event.newStatus())
                    .transitionReason(event.transitionReason())
                    // timestamp is auto-populated by @CreationTimestamp
                    .build();

            historyRepository.save(history);

            log.info("Audit record saved: alertId={}, {} → {}, reason='{}'",
                    event.alertId(), event.previousStatus(), event.newStatus(),
                    event.transitionReason());

        } catch (Exception ex) {
            // Log but do not rethrow — this thread is decoupled from the main request.
            // A failed audit write must never surface as an API error.
            log.error("Failed to persist AlertTransitionHistory for alertId={}: {}",
                    event.alertId(), ex.getMessage(), ex);
        }
    }
}
