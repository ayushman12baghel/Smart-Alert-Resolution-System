package com.moveinsync.alertsystem.rules;

import com.moveinsync.alertsystem.config.RuleProperties;
import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.enums.AlertSeverity;
import com.moveinsync.alertsystem.enums.AlertStatus;
import com.moveinsync.alertsystem.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Rule strategy for over-speeding events (sourceType =
 * {@code "SPEED_MONITOR"}).
 *
 * <h2>Rule Logic</h2>
 * <p>
 * When a new over-speeding alert arrives, this strategy counts how many
 * over-speeding alerts already exist for the <em>same driver</em> within the
 * configured sliding time window. If that count reaches or exceeds the
 * configured threshold, the incoming alert is immediately escalated:
 * <ul>
 * <li>severity → {@link AlertSeverity#CRITICAL}</li>
 * <li>status → {@link AlertStatus#ESCALATED}</li>
 * </ul>
 *
 * <h2>Configuration (application.yml)</h2>
 * 
 * <pre>
 * rules:
 *   strategies:
 *     SPEED_MONITOR:
 *       escalate-if-count: 3   # breach threshold
 *       window-mins: 60        # sliding window in minutes
 * </pre>
 *
 * <p>
 * If no entry exists for {@code SPEED_MONITOR} in {@code application.yml},
 * the {@link RuleProperties.RuleThreshold} defaults ({@code 3 events / 60 min})
 * are used, so the application remains safe to start without the config block.
 */
@Component
public class OverspeedingRuleStrategy implements RuleStrategy {

    private static final Logger log = LoggerFactory.getLogger(OverspeedingRuleStrategy.class);

    /** The sourceType value this strategy is responsible for. */
    public static final String SOURCE_TYPE = "SPEED_MONITOR";

    private final AlertRepository alertRepository;
    private final RuleProperties ruleProperties;

    public OverspeedingRuleStrategy(AlertRepository alertRepository,
            RuleProperties ruleProperties) {
        this.alertRepository = alertRepository;
        this.ruleProperties = ruleProperties;
    }

    @Override
    public String getSourceType() {
        return SOURCE_TYPE;
    }

    @Override
    public Alert evaluate(Alert alert) {
        // Load thresholds — fall back to defaults if config is absent for this source
        RuleProperties.RuleThreshold threshold = ruleProperties.getStrategies()
                .getOrDefault(SOURCE_TYPE, new RuleProperties.RuleThreshold());

        int escalateIfCount = threshold.getEscalateIfCount();
        int windowMins = threshold.getWindowMins();

        Instant windowStart = Instant.now().minus(windowMins, ChronoUnit.MINUTES);

        long recentCount = alertRepository.countByDriverIdAndSourceTypeAndTimestampAfter(
                alert.getDriverId(),
                SOURCE_TYPE,
                windowStart);

        // +1 accounts for the current alert, which has not been persisted yet at evaluation time
        if (recentCount + 1 >= escalateIfCount) {
            log.warn("Escalation triggered for driverId='{}': {} overspeeding events " +
                    "(including current) in the last {} minutes (threshold={}).",
                    alert.getDriverId(), recentCount + 1, windowMins, escalateIfCount);

            alert.setSeverity(AlertSeverity.CRITICAL);
            alert.setStatus(AlertStatus.ESCALATED);
        }

        return alert;
    }
}
