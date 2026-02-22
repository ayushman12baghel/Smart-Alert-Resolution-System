package com.moveinsync.alertsystem.repository;

import com.moveinsync.alertsystem.entity.AlertTransitionHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link AlertTransitionHistory} entities.
 *
 * <p>
 * This repository is intentionally minimal. History records are immutable
 * audit entries — they are written once by the {@code AuditListener} and
 * thereafter only ever read. No update or delete operations are exposed.
 */
@Repository
public interface AlertTransitionHistoryRepository extends JpaRepository<AlertTransitionHistory, UUID> {

    // -------------------------------------------------------------------------
    // Drill-Down UI — Full Lifecycle Timeline
    // -------------------------------------------------------------------------

    /**
     * Returns the complete, chronologically ordered transition history for
     * a single alert.
     *
     * <p>
     * Used by the front-end drill-down panel to render a timeline such as:
     * 
     * <pre>
     *   OPEN → ESCALATED  (Rule triggered: 3 overspeed events in 60 min)
     *   ESCALATED → RESOLVED  (Manual resolution by ops team)
     * </pre>
     *
     * <p>
     * Results are ordered by {@code timestamp ASC} so the UI always receives
     * events in the order they occurred, regardless of insertion jitter.
     *
     * @param alertId the UUID of the parent alert
     * @return ordered list of transition records; empty if no history exists yet
     */
    List<AlertTransitionHistory> findByAlertIdOrderByTimestampAsc(UUID alertId);
}
