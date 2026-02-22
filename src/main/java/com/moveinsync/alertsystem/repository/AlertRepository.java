package com.moveinsync.alertsystem.repository;

import com.moveinsync.alertsystem.dto.DailyStatusCount;
import com.moveinsync.alertsystem.dto.DriverAlertCountDto;
import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.enums.AlertStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link Alert} entities.
 *
 * <p>
 * Three query groups are supported:
 * <ol>
 * <li><b>Cron Job</b>: fetch all alerts in a set of statuses for periodic
 * evaluation.</li>
 * <li><b>Rule Engine</b>: count recent alerts per driver+source to detect
 * threshold breaches.</li>
 * <li><b>Dashboard Leaderboard</b>: aggregate the top-N driver offenders with
 * active alerts.</li>
 * </ol>
 */
@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    // -------------------------------------------------------------------------
    // Cron Job — Auto-Close Worker
    // -------------------------------------------------------------------------

    /**
     * Returns all alerts whose status is contained in {@code statuses}.
     * (Kept for the AutoCloseWorker which needs the full list to process.)
     */
    List<Alert> findAllByStatusIn(List<AlertStatus> statuses);

    /**
     * <b>Paginated</b> version of {@link #findAllByStatusIn(List)} — use this
     * for any API endpoint to avoid loading thousands of rows into memory.
     *
     * @param statuses the set of statuses to filter by
     * @param pageable page/size/sort descriptor
     * @return a page of matching alerts
     */
    Page<Alert> findAllByStatusIn(List<AlertStatus> statuses, Pageable pageable);

    /**
     * Efficiently counts alerts by a single status using an index-only scan.
     * <p>
     * Replaces the anti-pattern of {@code findAllByStatusIn(...).size()} which
     * loads every row into the JVM heap just to count them.
     *
     * @param status the status to count
     * @return number of alerts in that status
     */
    long countByStatus(AlertStatus status);

    /**
     * Looks up an alert by its idempotency / deduplication key.
     * <p>
     * Used by {@link com.moveinsync.alertsystem.service.AlertService} to detect
     * a duplicate submission before attempting a database insert.
     *
     * @param deduplicationKey the pre-computed key string
     * @return the existing alert, if any
     */
    Optional<Alert> findByDeduplicationKey(String deduplicationKey);

    // -------------------------------------------------------------------------
    // Rule Engine — Threshold Evaluation
    // -------------------------------------------------------------------------

    /**
     * Counts how many alerts exist for the given driver, source type, and time
     * window.
     *
     * <p>
     * Used by concrete {@code RuleStrategy} implementations to check whether
     * the number of events within the last N minutes exceeds a configured
     * threshold.
     *
     * <p>
     * Example: "3 overspeeding alerts for driver D1 in the last 60 minutes."
     *
     * @param driverId   the driver to scope the count to
     * @param sourceType the alert source (e.g. "SPEED_MONITOR")
     * @param after      the start of the time window (exclusive lower bound)
     * @return number of matching alerts in the given window
     */
    long countByDriverIdAndSourceTypeAndTimestampAfter(
            String driverId,
            String sourceType,
            Instant after);

    /**
     * Returns all alerts for the given driver and source type whose status is
     * one of the supplied values.
     *
     * <p>
     * Used by {@code ComplianceRuleStrategy} to find OPEN / ESCALATED COMPLIANCE
     * alerts that should be auto-closed when a resolution event arrives.
     *
     * @param driverId   the driver whose alerts are to be fetched
     * @param sourceType the alert source (e.g. "COMPLIANCE")
     * @param statuses   the set of statuses to match
     * @return matching alerts; may be empty but never null
     */
    List<Alert> findByDriverIdAndSourceTypeAndStatusIn(
            String driverId,
            String sourceType,
            List<AlertStatus> statuses);

    // -------------------------------------------------------------------------
    // Time-Filtered Queries
    // -------------------------------------------------------------------------

    /**
     * Returns all alerts with the given status whose creation timestamp is
     * strictly after {@code after}, ordered newest-first.
     * (Kept for internal use; prefer the paginated overload in API controllers.)
     */
    List<Alert> findByStatusAndTimestampAfterOrderByTimestampDesc(
            AlertStatus status,
            Instant after);

    /**
     * <b>Paginated</b> version — use this on the {@code /auto-closed} endpoint.
     *
     * @param status   filter by status (e.g. AUTO_CLOSED)
     * @param after    exclusive lower bound on creation timestamp
     * @param pageable page/size/sort descriptor
     * @return a page of matching alerts
     */
    Page<Alert> findByStatusAndTimestampAfterOrderByTimestampDesc(
            AlertStatus status,
            Instant after,
            Pageable pageable);

    // -------------------------------------------------------------------------
    // Trends — Daily Aggregation
    // -------------------------------------------------------------------------

    /**
     * Groups all alerts by calendar day (in {@code tz} timezone) and status,
     * returning the count per (day, status) pair ordered chronologically.
     *
     * <p>
     * The {@code :tz} parameter is a valid IANA timezone string (e.g.
     * {@code "UTC"}, {@code "Asia/Kolkata"}). Using a parameter instead of
     * hard-coding {@code 'UTC'} lets clients see daily buckets that align with
     * their local midnight rather than always UTC midnight.
     *
     * @param tz IANA timezone identifier (validated by the service layer)
     * @return one row per (day, status) combination, oldest day first
     */
    @Query(value = """
            SELECT
                TO_CHAR(day_bucket, 'YYYY-MM-DD') AS day,
                status,
                COUNT(*)                          AS count
            FROM (
                SELECT
                    DATE_TRUNC('day', timestamp AT TIME ZONE :tz) AS day_bucket,
                    status
                FROM alerts
            ) bucketed
            GROUP BY day_bucket, status
            ORDER BY day_bucket
            """, nativeQuery = true)
    List<DailyStatusCount> findDailyAlertCountsByStatus(@Param("tz") String tz);

    // -------------------------------------------------------------------------
    // Dashboard — Leaderboard / Top Offenders
    // -------------------------------------------------------------------------

    /**
     * Returns the top-N drivers ranked by their count of active (OPEN or ESCALATED)
     * alerts, in descending order.
     *
     * <p>
     * Results are wrapped in {@link DriverAlertCountDto} via a JPQL constructor
     * expression. Pass {@code PageRequest.of(0, 5)} as the {@code pageable}
     * argument
     * to retrieve the top-5 offenders for the Redis-cached dashboard widget.
     *
     * <p>
     * Using a {@link List} return type with {@link Pageable} intentionally avoids
     * the automatic count-query that {@code Page<T>} would trigger for GROUP BY
     * queries.
     *
     * @param pageable controls the result size (e.g. top 5); sorting is fixed in
     *                 the query
     * @return ordered list of (driverId, alertCount) projections
     */
    @Query("""
            SELECT new com.moveinsync.alertsystem.dto.DriverAlertCountDto(a.driverId, COUNT(a))
            FROM Alert a
            WHERE a.status IN (
                com.moveinsync.alertsystem.enums.AlertStatus.OPEN,
                com.moveinsync.alertsystem.enums.AlertStatus.ESCALATED
            )
            GROUP BY a.driverId
            ORDER BY COUNT(a) DESC
            """)
    List<DriverAlertCountDto> findTopDriverOffenders(Pageable pageable);
}
