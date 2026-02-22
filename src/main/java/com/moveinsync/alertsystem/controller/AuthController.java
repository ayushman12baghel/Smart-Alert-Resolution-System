package com.moveinsync.alertsystem.controller;

import com.moveinsync.alertsystem.dto.LoginRequest;
import com.moveinsync.alertsystem.dto.TokenResponse;
import com.moveinsync.alertsystem.security.JwtUtil;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication controller — issues JWTs in exchange for valid credentials.
 *
 * <h2>Endpoint</h2>
 * 
 * <pre>
 * POST /api/auth/login
 * Content-Type: application/json
 *
 * { "username": "admin", "password": "admin123" }
 *
 * HTTP 200
 * { "token": "eyJ...", "type": "Bearer", "expiresIn": 86400000 }
 * </pre>
 *
 * <h2>Error Cases</h2>
 * <ul>
 * <li>Wrong credentials → Spring Security throws
 * {@code BadCredentialsException}
 * → returns HTTP 401 automatically.</li>
 * <li>Blank fields → Bean Validation returns HTTP 400.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * Include the returned token in all subsequent requests:
 * 
 * <pre>
 * Authorization: Bearer &lt;token&gt;
 * </pre>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;

    @Value("${jwt.expiration-ms:86400000}")
    private long expirationMs;

    public AuthController(AuthenticationManager authenticationManager,
            JwtUtil jwtUtil) {
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

    /**
     * Authenticates the user and returns a signed JWT on success.
     *
     * <p>
     * Spring Security's {@link AuthenticationManager} verifies the credentials
     * against the in-memory user store. On failure it throws
     * {@code AuthenticationException}, which propagates as HTTP 401.
     *
     * @param request validated login payload
     * @return 200 with {@link TokenResponse} containing the JWT
     */
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        log.info("POST /api/auth/login — authenticating user '{}'", request.username());

        // Throws AuthenticationException (→ 401) if credentials are invalid
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.username(),
                        request.password()));

        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        String token = jwtUtil.generateToken(userDetails.getUsername());

        log.info("User '{}' authenticated successfully — JWT issued.", userDetails.getUsername());

        return ResponseEntity.ok(TokenResponse.of(token, expirationMs));
    }
}
