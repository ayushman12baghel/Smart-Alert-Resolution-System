package com.moveinsync.alertsystem.dto;

/**
 * One entry in the trends time-series, representing a single calendar day.
 *
 * <p>
 * Designed for direct consumption by Chart.js line graphs:
 *
 * <pre>
 * {
 *   "day":        "2026-02-21",
 *   "open":       4,
 *   "escalated":  2,
 *   "autoClosed": 5,
 *   "resolved":   1,
 *   "total":      12
 * }
 * </pre>
 *
 * Each field maps to a Chart.js dataset series by the same name.
 *
 * @param day        calendar date in {@code YYYY-MM-DD} format
 * @param open       count of OPEN alerts created on this day
 * @param escalated  count of ESCALATED alerts created on this day
 * @param autoClosed count of AUTO_CLOSED alerts created on this day
 * @param resolved   count of RESOLVED alerts created on this day
 * @param total      sum of all statuses for this day
 */
public record AlertTrendEntryDto(
        String day,
        long open,
        long escalated,
        long autoClosed,
        long resolved,
        long total) {
}
