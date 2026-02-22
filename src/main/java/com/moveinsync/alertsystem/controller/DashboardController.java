package com.moveinsync.alertsystem.controller;

import com.moveinsync.alertsystem.dto.AlertTrendEntryDto;
import com.moveinsync.alertsystem.dto.DashboardStatsDto;
import com.moveinsync.alertsystem.dto.DriverAlertCountDto;
import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.entity.AlertTransitionHistory;
import com.moveinsync.alertsystem.service.DashboardService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller that exposes read-only dashboard endpoints.
 *
 * <h2>Endpoints</h2>
 * 
 * <pre>
 * GET /api/dashboard/stats                      — Aggregate alert counts overview.
 * GET /api/dashboard/leaderboard                — Top-5 driver offenders (Redis cached).
 * GET /api/dashboard/alerts/{alertId}/history   — Full transition timeline for one alert.
 * GET /api/dashboard/alerts/auto-closed         — All AUTO_CLOSED alerts (ops review).
 * </pre>
 *
 * <p>
 * All methods delegate entirely to {@link DashboardService}.
 * The controller applies no transformation — it is a pure HTTP adapter.
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final Logger log = LoggerFactory.getLogger(DashboardController.class);

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/stats
    // -------------------------------------------------------------------------

    /**
     * Returns a real-time snapshot of aggregate alert counts across all statuses.
     *
     * @return 200 with {@link DashboardStatsDto}
     */
    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getStats() {
        log.debug("GET /api/dashboard/stats");
        return ResponseEntity.ok(dashboardService.getStats());
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/leaderboard
    // -------------------------------------------------------------------------

    /**
     * Returns the top-5 drivers with the most active (OPEN/ESCALATED) alerts,
     * sorted descending by alert count.
     *
     * <p>
     * This response is served from Redis cache when available, avoiding the
     * {@code GROUP BY} database query on every request.
     *
     * @return 200 with a list of up to 5 {@link DriverAlertCountDto} records
     */
    @GetMapping("/leaderboard")
    public ResponseEntity<List<DriverAlertCountDto>> getLeaderboard() {
        log.debug("GET /api/dashboard/leaderboard");
        return ResponseEntity.ok(dashboardService.getTopDriverOffenders());
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/alerts/{alertId}/history
    // -------------------------------------------------------------------------

    /**
     * Returns the complete, chronologically ordered status-transition history
     * for a single alert.
     *
     * <p>
     * Useful for rendering a timeline such as:
     * 
     * <pre>
     *  OPEN → ESCALATED  (Rule triggered)
     *  ESCALATED → RESOLVED  (Manual close)
     * </pre>
     *
     * @param alertId the UUID of the alert
     * @return 200 with an ordered list of {@link AlertTransitionHistory} records
     */
    @GetMapping("/alerts/{alertId}/history")
    public ResponseEntity<List<AlertTransitionHistory>> getAlertHistory(
            @PathVariable UUID alertId) {
        log.debug("GET /api/dashboard/alerts/{}/history", alertId);
        return ResponseEntity.ok(dashboardService.getTransitionHistory(alertId));
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/alerts/active
    // -------------------------------------------------------------------------

    /**
     * Returns paginated OPEN and ESCALATED alerts, newest first.
     * Operators use this feed to find and manually resolve active incidents.
     *
     * @param pageable resolved from {@code ?page=}, {@code ?size=}, {@code ?sort=}
     * @return 200 with a paginated list of active alerts
     */
    @GetMapping("/alerts/active")
    public ResponseEntity<Page<Alert>> getActiveAlerts(
            @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("GET /api/dashboard/alerts/active");
        return ResponseEntity.ok(dashboardService.getActiveAlerts(pageable));
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/alerts/closed
    // -------------------------------------------------------------------------

    /**
     * Returns paginated AUTO_CLOSED and RESOLVED alerts combined, newest first.
     * Used by the dashboard "Closed Alerts" tab to surface both system-closed
     * and manually-resolved incidents in a single feed.
     *
     * @param pageable resolved from {@code ?page=}, {@code ?size=}, {@code ?sort=}
     * @return 200 with a paginated list of closed alerts
     */
    @GetMapping("/alerts/closed")
    public ResponseEntity<Page<Alert>> getClosedAlerts(
            @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("GET /api/dashboard/alerts/closed");
        return ResponseEntity.ok(dashboardService.getClosedAlerts(pageable));
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/alerts/auto-closed
    // -------------------------------------------------------------------------

    /**
     * Returns alerts that were automatically closed, with an optional time filter.
     * Results are paginated to avoid massive payloads.
     *
     * <p>
     * Pagination defaults: page 0, 50 records, descending by {@code timestamp}.
     * Override via standard Spring {@code ?page=0&size=20&sort=timestamp,desc}.
     *
     * <p>
     * Supported {@code filter} values:
     * <ul>
     * <li>(omitted) - all AUTO_CLOSED alerts</li>
     * <li>{@code 24h} - closed in the last 24 hours</li>
     * <li>{@code 7d} - closed in the last 7 days</li>
     * <li>{@code 30d} - closed in the last 30 days</li>
     * </ul>
     *
     * @param filter   optional time-window string (e.g. {@code ?filter=24h})
     * @param pageable resolved from {@code ?page=}, {@code ?size=}, {@code ?sort=}
     * @return 200 with a paginated list of matching AUTO_CLOSED alerts
     */
    @GetMapping("/alerts/auto-closed")
    public ResponseEntity<Page<Alert>> getAutoClosedAlerts(
            @RequestParam(required = false) String filter,
            @PageableDefault(size = 50, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        log.debug("GET /api/dashboard/alerts/auto-closed?filter={}, pageable={}", filter, pageable);
        return ResponseEntity.ok(dashboardService.getAutoClosedAlerts(filter, pageable));
    }

    // -------------------------------------------------------------------------
    // GET /api/dashboard/trends
    // -------------------------------------------------------------------------

    /**
     * Returns a daily time-series of alert counts grouped by status.
     *
     * <p>
     * Accepts an optional {@code tz} query parameter (IANA timezone string,
     * default {@code UTC}) so that daily buckets align with the caller's local
     * midnight. For example, a dashboard in IST (UTC+5:30) should pass
     * {@code ?tz=Asia/Kolkata} to avoid having days split at 18:30 local time.
     *
     * @param tz IANA timezone identifier (e.g. {@code Asia/Kolkata}, {@code UTC})
     * @return 200 with a list of {@link AlertTrendEntryDto} records, oldest day
     *         first
     */
    @GetMapping("/trends")
    public ResponseEntity<List<AlertTrendEntryDto>> getTrends(
            @RequestParam(defaultValue = "UTC") String tz) {
        log.debug("GET /api/dashboard/trends?tz={}", tz);
        return ResponseEntity.ok(dashboardService.getTrends(tz));
    }
}
