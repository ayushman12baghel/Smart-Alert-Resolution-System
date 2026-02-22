package com.moveinsync.alertsystem.rules;

import com.moveinsync.alertsystem.entity.Alert;

/**
 * Strategy contract for all rule evaluators in the Rule Engine.
 *
 * <p>
 * Every concrete implementation is responsible for exactly <em>one</em>
 * alert source type (e.g. {@code SPEED_MONITOR}, {@code HARSH_BRAKING}).
 * The {@link RuleEngine} uses {@link #getSourceType()} to build a dispatch
 * map at startup, routing each incoming alert to the correct strategy without
 * any {@code if-else} or {@code switch} blocks.
 *
 * <h2>Contract</h2>
 * <ul>
 * <li>{@link #getSourceType()} must return a non-null, non-empty string that
 * exactly matches the {@code sourceType} field stored on incoming
 * {@link Alert} objects.</li>
 * <li>{@link #evaluate(Alert)} receives the incoming alert, applies threshold
 * logic, <strong>mutates the alert's severity and/or status in place</strong>
 * if a rule fires, and returns the (possibly modified) alert.
 * Implementors must not call {@code AlertRepository.save()} — that is the
 * responsibility of {@code AlertService}.</li>
 * </ul>
 *
 * <h2>Adding a new rule</h2>
 * <ol>
 * <li>Create a new {@code @Component} class implementing this interface.</li>
 * <li>Add the corresponding threshold block under {@code rules.strategies} in
 * {@code application.yml}.</li>
 * <li>No other code needs to change — the {@link RuleEngine} picks up the new
 * bean automatically via Spring's dependency injection.</li>
 * </ol>
 */
public interface RuleStrategy {

    /**
     * Returns the {@code sourceType} string this strategy handles.
     *
     * <p>
     * Must be a constant, non-null value and must exactly match the
     * {@code sourceType} stored on inbound {@link Alert} objects
     * (case-sensitive comparison is used by the engine).
     *
     * @return the source-type key this strategy is responsible for
     */
    String getSourceType();

    /**
     * Evaluates the alert against this strategy's threshold rules and
     * returns the (potentially mutated) alert.
     *
     * <p>
     * Implementations should:
     * <ul>
     * <li>Query recent alert history to determine whether a threshold has
     * been breached.</li>
     * <li>If breached, update {@code alert.severity} and/or {@code alert.status}
     * directly on the passed-in object.</li>
     * <li>If not breached, return the alert unchanged.</li>
     * </ul>
     *
     * <p>
     * The caller ({@code AlertService}) is responsible for persisting the
     * returned alert. Implementors must <strong>not</strong> call save themselves.
     *
     * @param alert the incoming alert to evaluate (never null)
     * @return the same alert instance, possibly with updated fields
     */
    Alert evaluate(Alert alert);
}
