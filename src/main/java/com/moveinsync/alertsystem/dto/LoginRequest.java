package com.moveinsync.alertsystem.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Inbound payload for {@code POST /api/auth/login}.
 *
 * @param username the account username
 * @param password the account password (plain-text; transmitted over HTTPS in
 *                 production)
 */
public record LoginRequest(
        @NotBlank(message = "Username must not be blank") String username,

        @NotBlank(message = "Password must not be blank") String password) {
}
