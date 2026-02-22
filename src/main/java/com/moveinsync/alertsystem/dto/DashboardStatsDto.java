package com.moveinsync.alertsystem.dto;

/**
 * Aggregate statistics snapshot for the Dashboard overview widget.
 *
 * <p>
 * All counts reflect the current state of the {@code alerts} table at the
 * time of the query. This record is intentionally <em>not</em> cached because
 * counts change frequently; only the leaderboard (expensive GROUP BY) is
 * cached.
 *
 * @param totalAlerts     total number of alerts ever ingested
 * @param openCount       alerts currently in OPEN status
 * @param escalatedCount  alerts currently in ESCALATED status
 * @param autoClosedCount alerts that were AUTO_CLOSED by the cron worker
 * @param resolvedCount   alerts that were manually RESOLVED
 */
public record DashboardStatsDto(
        long totalAlerts,
        long openCount,
        long escalatedCount,
        long autoClosedCount,
        long resolvedCount) {
}
