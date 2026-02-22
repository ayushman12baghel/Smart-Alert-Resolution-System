package com.moveinsync.alertsystem.dto;

/**
 * Projection DTO used by the Dashboard leaderboard query.
 *
 * Carries a driver identifier and the number of active (OPEN / ESCALATED)
 * alerts associated with that driver. Instantiated via a JPQL constructor
 * expression, so the field order and types must exactly match the query's
 * SELECT clause.
 *
 * @param driverId   The driver's unique identifier.
 * @param alertCount The total count of active alerts for this driver.
 */
public record DriverAlertCountDto(String driverId, Long alertCount) {
}
