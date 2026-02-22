package com.moveinsync.alertsystem.controller;

import com.moveinsync.alertsystem.dto.AlertRequest;
import com.moveinsync.alertsystem.entity.Alert;
import com.moveinsync.alertsystem.service.AlertService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for alert ingestion and retrieval.
 *
 * <h2>Endpoints</h2>
 * 
 * <pre>
 * POST /api/alerts          — Ingest a new alert and run rule evaluation.
 * GET  /api/alerts/{id}     — Retrieve a single alert by UUID.
 * </pre>
 *
 * <p>
 * The controller is deliberately thin: it maps the inbound
 * {@link AlertRequest} DTO to a JPA {@link Alert} entity and delegates
 * all business logic to {@link AlertService}. No persistence code lives here.
 */
@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private static final Logger log = LoggerFactory.getLogger(AlertController.class);

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    // -------------------------------------------------------------------------
    // POST /api/alerts
    // -------------------------------------------------------------------------

    /**
     * Ingests a new alert, runs rule evaluation, and returns the final persisted
     * state.
     *
     * <p>
     * HTTP 201 Created is returned on success. The response body contains the
     * fully populated alert — including its database-assigned UUID, timestamp, and
     * any status/severity changes applied by the rule engine.
     *
     * @param request the validated inbound alert payload
     * @return 201 with the processed alert, or 400 if validation fails
     */
    @PostMapping
    public ResponseEntity<Alert> ingestAlert(@Valid @RequestBody AlertRequest request) {
        log.info("POST /api/alerts — driverId='{}', sourceType='{}', severity={}",
                request.driverId(), request.sourceType(), request.severity());

        Alert incoming = Alert.builder()
                .driverId(request.driverId())
                .sourceType(request.sourceType())
                .severity(request.severity())
                .metadata(request.metadata())
                .build();

        Alert processed = alertService.processNewAlert(incoming);

        return ResponseEntity.status(HttpStatus.CREATED).body(processed);
    }

    // -------------------------------------------------------------------------
    // GET /api/alerts/{id}
    // -------------------------------------------------------------------------

    /**
     * Retrieves a single alert by its UUID.
     *
     * @param id the UUID of the alert to fetch
     * @return 200 with the alert body, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Alert> getAlert(@PathVariable UUID id) {
        log.debug("GET /api/alerts/{}", id);
        Alert alert = alertService.getById(id);
        return ResponseEntity.ok(alert);
    }

    // -------------------------------------------------------------------------
    // PUT /api/alerts/{id}/resolve
    // -------------------------------------------------------------------------

    /**
     * Manually resolves an alert, setting its status to {@code RESOLVED}.
     *
     * <p>
     * Idempotent — calling this on an already-RESOLVED alert is safe and
     * returns 200 without error.
     *
     * @param id the UUID of the alert to resolve
     * @return 200 with the updated alert, or 404 if not found
     */
    @PutMapping("/{id}/resolve")
    public ResponseEntity<Alert> resolveAlert(@PathVariable UUID id) {
        log.info("PUT /api/alerts/{}/resolve", id);
        Alert resolved = alertService.resolveAlert(id);
        return ResponseEntity.ok(resolved);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/alerts/all
    // -------------------------------------------------------------------------

    /**
     * Utility endpoint to wipe the database for fresh testing.
     * * @return 204 No Content on success
     */
    @DeleteMapping("/all")
    public ResponseEntity<Void> clearAllAlerts() {
        log.warn("REST request to delete all alerts - DB Cleanup triggered");
        alertService.deleteAllAlerts(); // AlertService mein ye method call karenge
        return ResponseEntity.noContent().build();
    }
}
