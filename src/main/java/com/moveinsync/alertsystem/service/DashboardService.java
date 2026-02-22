package com.moveinsync.alertsystem.service;

import com.moveinsync.alertsystem.dto.AlertTrendEntryDto;
import com.moveinsync.alertsystem.dto.DailyStatusCount;
import com.moveinsync.alertsystem.dto.DashboardStatsDto;
import com.moveinsync.alertsystem.dto.DriverAlertCountDto;
import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.entity.AlertTransitionHistory;
import com.moveinsync.alertsystem.enums.AlertStatus;
import com.moveinsync.alertsystem.repository.AlertRepository;
import com.moveinsync.alertsystem.repository.AlertTransitionHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service layer for the Dashboard feature.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li>Aggregate alert counts for the overview stats widget.</li>
 * <li>Retrieve the transition history for the alert drill-down view.</li>
 * <li>Provide the top-5 driver leaderboard with Redis caching to avoid
 * repeated expensive {@code GROUP BY} queries.</li>
 * </ul>
 *
 * <h2>Caching Strategy</h2>
 * <p>
 * The leaderboard result is cached in Redis under the key {@code "topDrivers"}.
 * The cache must be explicitly evicted (or configured with a TTL) when alert
 * statuses change. For a production system, add a
 * {@code @CacheEvict(value = "topDrivers", allEntries = true)} call in
 * {@link AlertService#processNewAlert} or use a TTL-based eviction policy in
 * {@code application.yml} (see {@code spring.cache.redis.time-to-live}).
 */
@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);

    /** Number of top offenders to return on the leaderboard. */
    private static final int LEADERBOARD_SIZE = 5;

    private final AlertRepository alertRepository;
    private final AlertTransitionHistoryRepository historyRepository;

    public DashboardService(AlertRepository alertRepository,
            AlertTransitionHistoryRepository historyRepository) {
        this.alertRepository = alertRepository;
        this.historyRepository = historyRepository;
    }

    // -------------------------------------------------------------------------
    // Stats Overview
    // -------------------------------------------------------------------------

    /**
     * Returns a snapshot of aggregate alert counts across all statuses.
     *
     * <p>
     * Uses {@code COUNT}-based queries (index-only scans on the status column)
     * instead of loading every row into memory. Avoids the former OOM-prone
     * {@code findAllByStatusIn(...).size()} anti-pattern.
     *
     * @return current statistics snapshot
     */
    public DashboardStatsDto getStats() {
        long total = alertRepository.count();
        long open = alertRepository.countByStatus(AlertStatus.OPEN);
        long escalated = alertRepository.countByStatus(AlertStatus.ESCALATED);
        long autoClosed = alertRepository.countByStatus(AlertStatus.AUTO_CLOSED);
        long resolved = alertRepository.countByStatus(AlertStatus.RESOLVED);

        log.debug("DashboardStats: total={}, open={}, escalated={}, autoClosed={}, resolved={}",
                total, open, escalated, autoClosed, resolved);

        return new DashboardStatsDto(total, open, escalated, autoClosed, resolved);
    }

    // -------------------------------------------------------------------------
    // Leaderboard — Redis Cached
    // -------------------------------------------------------------------------

    /**
     * Returns the top-{@value #LEADERBOARD_SIZE} drivers with the most active
     * (OPEN or ESCALATED) alerts.
     *
     * <p>
     * The result is cached in Redis under the cache name {@code "topDrivers"}.
     * Spring's cache abstraction serialises the {@code List<DriverAlertCountDto>}
     * and stores it in Redis. Subsequent calls within the TTL window return the
     * cached value without touching the database.
     *
     * @return ordered list of (driverId, alertCount) pairs, largest first
     */
    @Cacheable(value = "topDrivers")
    public List<DriverAlertCountDto> getTopDriverOffenders() {
        log.debug("Cache miss for 'topDrivers' — querying database.");
        return alertRepository.findTopDriverOffenders(PageRequest.of(0, LEADERBOARD_SIZE));
    }

    // -------------------------------------------------------------------------
    // Drill-Down — Transition History
    // -------------------------------------------------------------------------

    /**
     * Returns the complete, chronologically ordered transition history for the
     * specified alert.
     *
     * <p>
     * Used by the front-end drill-down panel to render a status timeline.
     *
     * @param alertId the UUID of the alert whose history is requested
     * @return list of transition records ordered by timestamp ascending
     */
    public List<AlertTransitionHistory> getTransitionHistory(UUID alertId) {
        return historyRepository.findByAlertIdOrderByTimestampAsc(alertId);
    }

    // -------------------------------------------------------------------------
    // Active Alerts (OPEN + ESCALATED)
    // -------------------------------------------------------------------------

    /**
     * Returns paginated OPEN and ESCALATED alerts, newest first.
     * Used by the dashboard "Active Alerts" tab so operators can
     * click through and resolve them manually.
     *
     * @param pageable page/size/sort descriptor
     * @return page of active alerts
     */
    public Page<Alert> getActiveAlerts(Pageable pageable) {
        return alertRepository.findAllByStatusIn(
                List.of(AlertStatus.OPEN, AlertStatus.ESCALATED), pageable);
    }

    // -------------------------------------------------------------------------
    // Recent Closed Alerts  (AUTO_CLOSED + RESOLVED)
    // -------------------------------------------------------------------------

    /**
     * Returns paginated AUTO_CLOSED and RESOLVED alerts, newest first.
     * Used by the "Closed Alerts" dashboard tab so operators can see both
     * system-closed and manually-resolved incidents in one feed.
     *
     * @param pageable page/size/sort descriptor
     * @return page of closed alerts
     */
    public Page<Alert> getClosedAlerts(Pageable pageable) {
        return alertRepository.findAllByStatusIn(
                List.of(AlertStatus.AUTO_CLOSED, AlertStatus.RESOLVED), pageable);
    }

    /**
     * Returns AUTO_CLOSED alerts, optionally filtered by a time window.
     *
     * <p>
     * The method is paginated to prevent loading thousands of rows into memory.
     * Pass a {@link Pageable} — e.g.
     * {@code PageRequest.of(0, 50, Sort.by("timestamp").descending())}
     * — from the controller layer.
     *
     * <p>
     * Supported {@code filter} values:
     * <ul>
     * <li>{@code null} or blank — return all AUTO_CLOSED alerts (paged)</li>
     * <li>{@code "24h"} — last 24 hours</li>
     * <li>{@code "7d"} — last 7 days</li>
     * <li>{@code "30d"} — last 30 days</li>
     * </ul>
     *
     * @param filter   optional time-window string
     * @param pageable page/size/sort descriptor
     * @return page of matching alerts, newest first
     * @throws IllegalArgumentException if an unknown filter value is supplied
     */
    public Page<Alert> getAutoClosedAlerts(String filter, Pageable pageable) {
        if (filter == null || filter.isBlank()) {
            return alertRepository.findAllByStatusIn(List.of(AlertStatus.AUTO_CLOSED), pageable);
        }

        Instant cutoff = switch (filter) {
            case "24h" -> Instant.now().minus(24, ChronoUnit.HOURS);
            case "7d" -> Instant.now().minus(7, ChronoUnit.DAYS);
            case "30d" -> Instant.now().minus(30, ChronoUnit.DAYS);
            default -> {
                log.warn("getAutoClosedAlerts: unknown filter '{}', defaulting to 24h.", filter);
                yield Instant.now().minus(24, ChronoUnit.HOURS);
            }
        };

        log.debug("getAutoClosedAlerts: filter='{}', cutoff={}", filter, cutoff);
        return alertRepository.findByStatusAndTimestampAfterOrderByTimestampDesc(
                AlertStatus.AUTO_CLOSED, cutoff, pageable);
    }

    // -------------------------------------------------------------------------
    // Trends — Daily Analytics
    // -------------------------------------------------------------------------

    /**
     * Returns a day-by-day breakdown of alert counts per status, suitable for
     * rendering Chart.js line graphs.
     *
     * <p>
     * The raw {@code (day, status, count)} rows from the database are pivoted
     * into one {@link AlertTrendEntryDto} per calendar day, so each entry holds
     * counts for all four statuses plus a daily total.
     *
     * @return list of daily trend entries, oldest day first
     */
    /**
     * Returns a day-by-day breakdown of alert counts per status, bucketed in
     * the caller's timezone so that chart labels align with local midnight.
     *
     * @param timezone a valid IANA timezone string (e.g. {@code "UTC"},
     *                 {@code "Asia/Kolkata"}); defaults to {@code "UTC"} when
     *                 the caller omits the parameter
     * @return list of daily trend entries, oldest day first
     * @throws IllegalArgumentException if {@code timezone} is not a valid
     *                                  IANA timezone identifier
     */
    public List<AlertTrendEntryDto> getTrends(String timezone) {
        // Validate the timezone early so bad input returns a clean 400,
        // not a cryptic PostgreSQL error bubbled up as a 500.
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException(
                    "Invalid timezone '" + timezone + "'. Use a valid IANA identifier " +
                            "such as 'UTC', 'Asia/Kolkata', or 'America/New_York'.");
        }

        List<DailyStatusCount> rows = alertRepository.findDailyAlertCountsByStatus(timezone);

        // Pivot: day → { status → count }
        // LinkedHashMap preserves insertion order (rows already ordered by day)
        Map<String, Map<String, Long>> pivot = new LinkedHashMap<>();

        for (DailyStatusCount row : rows) {
            pivot
                    .computeIfAbsent(row.getDay(), d -> new LinkedHashMap<>())
                    .put(row.getStatus(), row.getCount());
        }

        List<AlertTrendEntryDto> result = new ArrayList<>(pivot.size());
        for (Map.Entry<String, Map<String, Long>> entry : pivot.entrySet()) {
            String day = entry.getKey();
            Map<String, Long> counts = entry.getValue();

            long open = counts.getOrDefault("OPEN", 0L);
            long escalated = counts.getOrDefault("ESCALATED", 0L);
            long autoClosed = counts.getOrDefault("AUTO_CLOSED", 0L);
            long resolved = counts.getOrDefault("RESOLVED", 0L);

            result.add(new AlertTrendEntryDto(
                    day, open, escalated, autoClosed, resolved,
                    open + escalated + autoClosed + resolved));
        }

        log.debug("getTrends(tz='{}'): returning {} daily entries.", timezone, result.size());
        return result;
    }
}
