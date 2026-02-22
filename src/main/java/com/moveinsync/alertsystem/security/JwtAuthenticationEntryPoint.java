package com.moveinsync.alertsystem.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.moveinsync.alertsystem.dto.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Custom {@link AuthenticationEntryPoint} that returns a structured JSON 401
 * instead of Spring Security's default HTML error page or redirect.
 *
 * <h2>When is this invoked?</h2>
 * <p>
 * {@link org.springframework.security.web.access.ExceptionTranslationFilter}
 * catches any {@link AuthenticationException} thrown inside the security filter
 * chain and delegates to this entry point. This covers two cases:
 * <ol>
 * <li>A request arrives with <b>no</b> {@code Authorization} header on a
 * protected endpoint — the anonymous user is rejected.</li>
 * <li>The {@link JwtAuthenticationFilter} detected a <b>present-but-invalid</b>
 * token (expired, malformed, bad signature), stored a message in
 * {@code request.setAttribute("jwt.error", ...)} and continued the chain
 * without populating the
 * {@link org.springframework.security.core.context.SecurityContext}.
 * Spring Security then blocks the request and triggers this entry point.</li>
 * </ol>
 *
 * <h2>Response shape</h2>
 * 
 * <pre>
 * HTTP/1.1 401 Unauthorized
 * Content-Type: application/json
 *
 * {
 *   "timestamp": "2026-02-22T10:15:30Z",
 *   "status":    401,
 *   "error":     "Unauthorized",
 *   "message":   "JWT token has expired. Please re-authenticate.",
 *   "path":      "/api/alerts"
 * }
 * </pre>
 */
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public JwtAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // The JwtAuthenticationFilter sets this attribute with a specific reason
        // when a token is present but structurally invalid.
        String message = (String) request.getAttribute("jwt.error");
        if (message == null) {
            message = "Authentication required. " +
                    "Provide a valid 'Authorization: Bearer <token>' header.";
        }

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                HttpStatus.UNAUTHORIZED.getReasonPhrase(),
                message,
                request.getRequestURI());

        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
