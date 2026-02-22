package com.moveinsync.alertsystem.event;

import com.moveinsync.alertsystem.enums.AlertStatus;

import java.util.UUID;

/**
 * Immutable event record published whenever an
 * {@link com.moveinsync.alertsystem.entity.Alert}
 * undergoes a status transition.
 *
 * <p>
 * Using a plain Java {@code record} (instead of extending
 * {@link org.springframework.context.ApplicationEvent}) is the idiomatic
 * Spring 6 / Spring Boot 3 approach: the framework's
 * {@link org.springframework.context.ApplicationEventPublisher} and
 * {@link org.springframework.context.event.EventListener} both work with
 * arbitrary objects, so there is no need to inherit from the older base class.
 *
 * <p>
 * This event is published by
 * {@link com.moveinsync.alertsystem.event.TransitionPublisher}
 * and consumed asynchronously by
 * {@link com.moveinsync.alertsystem.event.AuditListener}.
 *
 * @param alertId          the UUID of the alert that transitioned
 * @param previousStatus   the status the alert held before the transition
 *                         (may be {@code null} for the initial OPEN creation
 *                         event)
 * @param newStatus        the status the alert moved into
 * @param transitionReason human-readable explanation (e.g. "Rule fired: 3
 *                         events in 60 min")
 */
public record AlertTransitionEvent(
        UUID alertId,
        AlertStatus previousStatus,
        AlertStatus newStatus,
        String transitionReason) {
}
