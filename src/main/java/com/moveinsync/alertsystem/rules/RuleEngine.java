package com.moveinsync.alertsystem.rules;

import com.moveinsync.alertsystem.entity.Alert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Central dispatcher for the Rule Engine (Strategy Pattern).
 *
 * At startup, Spring injects every {@link RuleStrategy} bean and this constructor
 * builds a {@code Map<sourceType → strategy>}. All routing is O(1) map lookup —
 * no if-else chains, no switch statements, no manual registration.
 *
 * Adding a new rule: create a {@code @Component} implementing {@link RuleStrategy}.
 * Spring discovers and registers it automatically.
 */
@Component
public class RuleEngine {

    private static final Logger log = LoggerFactory.getLogger(RuleEngine.class);

    /** sourceType → strategy dispatch map; built once, effectively immutable at runtime. */
    private final Map<String, RuleStrategy> strategyMap;

    public RuleEngine(List<RuleStrategy> strategies) {
        this.strategyMap = strategies.stream()
                .collect(Collectors.toMap(
                        RuleStrategy::getSourceType,
                        Function.identity(),
                        (existing, duplicate) -> {
                            throw new IllegalStateException(
                                    "Duplicate RuleStrategy registered for sourceType: "
                                            + existing.getSourceType());
                        }));

        log.info("RuleEngine initialised with {} strategies: {}",
                strategyMap.size(), strategyMap.keySet());
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Routes the alert to its registered strategy. If no strategy is registered
     * for the incoming sourceType, the alert is returned unchanged and a warning
     * is logged. Unknown source types are not errors — they simply have no rule yet.
     */
    public Alert evaluate(Alert alert) {
        return Optional.ofNullable(strategyMap.get(alert.getSourceType()))
                .map(strategy -> {
                    log.debug("Applying strategy '{}' to alert id={}",
                            strategy.getClass().getSimpleName(), alert.getId());
                    return strategy.evaluate(alert);
                })
                .orElseGet(() -> {
                    log.warn("No RuleStrategy registered for sourceType='{}'. " +
                            "Alert id={} will not be evaluated.",
                            alert.getSourceType(), alert.getId());
                    return alert;
                });
    }

    public boolean hasStrategyFor(String sourceType) {
        return strategyMap.containsKey(sourceType);
    }
}
