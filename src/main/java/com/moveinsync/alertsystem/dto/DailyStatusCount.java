package com.moveinsync.alertsystem.dto;

/**
 * Spring Data JPA projection interface used to extract one row of the
 * daily-trend native query.
 *
 * <p>
 * Each row represents the count of alerts grouped by
 * (calendar day, status). The
 * {@link com.moveinsync.alertsystem.service.DashboardService}
 * pivots these rows into {@link AlertTrendEntryDto} objects for the API
 * response.
 */
public interface DailyStatusCount {

    /** Calendar day as {@code YYYY-MM-DD} string (e.g. {@code "2026-02-21"}). */
    String getDay();

    /** Alert status string (e.g. {@code "OPEN"}, {@code "ESCALATED"}). */
    String getStatus();

    /** Number of alerts with this status on this day. */
    Long getCount();
}
