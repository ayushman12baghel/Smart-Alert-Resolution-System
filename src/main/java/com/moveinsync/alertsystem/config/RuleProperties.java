package com.moveinsync.alertsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.util.HashMap;
import java.util.Map;

/**
 * Strongly-typed binding for the {@code rules} namespace in
 * {@code application.yml}.
 *
 * <p>
 * Each entry under {@code rules.strategies} is keyed by the alert
 * {@code sourceType} string (e.g. {@code SPEED_MONITOR}) and holds the
 * threshold parameters that the corresponding
 * {@link com.moveinsync.alertsystem.rules.RuleStrategy}
 * will read at evaluation time.
 *
 * <p>
 * Example {@code application.yml} block:
 * 
 * <pre>
 * rules:
 *   strategies:
 *     SPEED_MONITOR:
 *       escalate-if-count: 3
 *       window-mins: 60
 *     HARSH_BRAKING:
 *       escalate-if-count: 5
 *       window-mins: 30
 * </pre>
 *
 * <p>
 * Register this class in your Spring Boot application via
 * {@code @EnableConfigurationProperties(RuleProperties.class)} or by annotating
 * it with {@code @Component} — either approach works. The
 * {@code @ConfigurationProperties}
 * annotation alone does <em>not</em> register the bean automatically.
 */
@ConfigurationProperties(prefix = "rules")
public class RuleProperties {

    /**
     * Map of source-type key → threshold configuration.
     * Keys must exactly match the {@code sourceType} value stored on an
     * {@link com.moveinsync.alertsystem.entity.Alert}.
     */
    private Map<String, RuleThreshold> strategies = new HashMap<>();

    public Map<String, RuleThreshold> getStrategies() {
        return strategies;
    }

    public void setStrategies(Map<String, RuleThreshold> strategies) {
        this.strategies = strategies;
    }

    // -------------------------------------------------------------------------
    // Nested configuration record
    // -------------------------------------------------------------------------

    /**
     * Threshold parameters for a single rule strategy.
     *
     * <p>
     * Both fields have safe defaults so the application starts even if
     * a particular strategy block is missing from {@code application.yml}.
     */
    public static class RuleThreshold {

        /**
         * Number of alerts within the time window that must be breached before
         * the alert is escalated to CRITICAL.
         * Default: {@code 3}.
         */
        private int escalateIfCount = 3;

        /**
         * Length of the sliding time window, in minutes, over which
         * {@code escalate-if-count} is evaluated.
         * Default: {@code 60}.
         */
        private int windowMins = 60;

        /**
         * Metadata value that, when present under the {@code "event"} key,
         * signals that existing COMPLIANCE alerts for the driver should be
         * auto-closed. Bound from {@code rules.strategies.COMPLIANCE.auto-close-if}.
         * Default: {@code "document_valid"}.
         */
        private String autoCloseIf = "document_valid";

        public int getEscalateIfCount() {
            return escalateIfCount;
        }

        public void setEscalateIfCount(int escalateIfCount) {
            this.escalateIfCount = escalateIfCount;
        }

        public int getWindowMins() {
            return windowMins;
        }

        public void setWindowMins(int windowMins) {
            this.windowMins = windowMins;
        }

        public String getAutoCloseIf() {
            return autoCloseIf;
        }

        public void setAutoCloseIf(String autoCloseIf) {
            this.autoCloseIf = autoCloseIf;
        }
    }
}
