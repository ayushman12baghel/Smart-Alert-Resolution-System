package com.moveinsync.alertsystem.rules;

import com.moveinsync.alertsystem.config.RuleProperties;
import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.enums.AlertStatus;
import com.moveinsync.alertsystem.repository.AlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Rule strategy for compliance events (sourceType = {@code "COMPLIANCE"}).
 *
 * <h2>Rule Logic</h2>
 * <p>
 * When an incoming COMPLIANCE alert carries a metadata entry
 * {@code "event": "<autoCloseIf>"} (e.g. {@code "event": "document_valid"}),
 * this strategy treats it as a <em>resolution signal</em>:
 * <ol>
 * <li>All existing COMPLIANCE alerts for the same driver that are still
 * {@code OPEN} or {@code ESCALATED} are fetched from the database and
 * bulk-updated to {@link AlertStatus#AUTO_CLOSED}.</li>
 * <li>The incoming alert itself is also marked {@code AUTO_CLOSED} — it
 * represents the resolution event, not a new problem.</li>
 * </ol>
 * <p>
 * If the incoming alert does <em>not</em> carry the resolution signal (e.g.
 * a new compliance violation), it is left in its initial {@code OPEN} state
 * and no auto-closure is performed.
 *
 * <h2>Configuration ({@code application-rules.yml})</h2>
 * 
 * <pre>
 * rules:
 *   strategies:
 *     COMPLIANCE:
 *       auto-close-if: "document_valid"   # value of metadata["event"] that triggers closure
 * </pre>
 *
 * <p>
 * The {@code auto-close-if} value is bound through
 * {@link RuleProperties.RuleThreshold#getAutoCloseIf()} via
 * {@code @ConfigurationProperties(prefix = "rules")}. Changing it in YAML
 * requires no code modification.
 *
 * <h2>Auto-registration</h2>
 * <p>
 * This class is a {@code @Component}, so the {@link RuleEngine} picks it up
 * automatically on startup — no manual wiring required. The log line
 * 
 * <pre>
 * RuleEngine initialised with 2 strategies: [SPEED_MONITOR, COMPLIANCE]
 * </pre>
 * 
 * confirms successful registration.
 */
@Component
public class ComplianceRuleStrategy implements RuleStrategy {

    private static final Logger log = LoggerFactory.getLogger(ComplianceRuleStrategy.class);

    /** The sourceType value this strategy is responsible for. */
    public static final String SOURCE_TYPE = "COMPLIANCE";

    /**
     * Metadata key expected to carry the resolution signal value.
     * E.g. the payload {@code {"event":"document_valid"}} would use key
     * {@code "event"}.
     */
    private static final String METADATA_KEY = "event";

    private final AlertRepository alertRepository;
    private final RuleProperties ruleProperties;

    public ComplianceRuleStrategy(AlertRepository alertRepository,
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
        RuleProperties.RuleThreshold threshold = ruleProperties.getStrategies()
                .getOrDefault(SOURCE_TYPE, new RuleProperties.RuleThreshold());

        String autoCloseIf = threshold.getAutoCloseIf();

        String eventValue = alert.getMetadata() != null
                ? String.valueOf(alert.getMetadata().getOrDefault(METADATA_KEY, ""))
                : "";

        if (!autoCloseIf.equals(eventValue)) {
            return alert; // not a resolution signal — leave alert OPEN
        }

        // Bulk-close all existing OPEN / ESCALATED COMPLIANCE alerts for the same driver
        List<Alert> openAlerts = alertRepository.findByDriverIdAndSourceTypeAndStatusIn(
                alert.getDriverId(),
                SOURCE_TYPE,
                List.of(AlertStatus.OPEN, AlertStatus.ESCALATED));

        if (!openAlerts.isEmpty()) {
            openAlerts.forEach(a -> a.setStatus(AlertStatus.AUTO_CLOSED));
            alertRepository.saveAll(openAlerts);
        }

        // The incoming alert is the resolution event itself — mark it closed too
        alert.setStatus(AlertStatus.AUTO_CLOSED);
        return alert;
    }
}
