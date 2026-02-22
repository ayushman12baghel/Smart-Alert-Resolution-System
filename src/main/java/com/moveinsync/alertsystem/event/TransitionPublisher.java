package com.moveinsync.alertsystem.event;

import com.moveinsync.alertsystem.enums.AlertStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Thin wrapper around Spring's {@link ApplicationEventPublisher} for publishing
 * {@link AlertTransitionEvent}s.
 *
 * <p>
 * Keeping the publish logic in its own component rather than injecting
 * {@code ApplicationEventPublisher} directly into {@code AlertService} has
 * two benefits:
 * <ul>
 * <li>It is easy to mock in unit tests without stubbing the full Spring event
 * bus.</li>
 * <li>Additional cross-cutting concerns (e.g. metrics counters, tracing spans)
 * can be added here in one place without touching service code.</li>
 * </ul>
 */
@Component
public class TransitionPublisher {

    private static final Logger log = LoggerFactory.getLogger(TransitionPublisher.class);

    private final ApplicationEventPublisher eventPublisher;

    public TransitionPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * Constructs and publishes an {@link AlertTransitionEvent} for the given
     * status change.
     *
     * <p>
     * Publishing is synchronous from the caller's point of view — the event
     * object is handed to the Spring event bus here. Whether downstream listeners
     * handle it synchronously or asynchronously (via {@code @Async}) is the
     * <em>listener's</em> concern, not the publisher's.
     *
     * @param alertId          the UUID of the alert that changed state
     * @param previousStatus   the status before the change (may be {@code null}
     *                         for brand-new alerts entering OPEN)
     * @param newStatus        the status after the change
     * @param transitionReason human-readable reason for the transition
     */
    public void publish(UUID alertId,
            AlertStatus previousStatus,
            AlertStatus newStatus,
            String transitionReason) {

        AlertTransitionEvent event = new AlertTransitionEvent(
                alertId, previousStatus, newStatus, transitionReason);

        log.debug("Publishing AlertTransitionEvent: alertId={}, {} → {}",
                alertId, previousStatus, newStatus);

        eventPublisher.publishEvent(event);
    }
}
