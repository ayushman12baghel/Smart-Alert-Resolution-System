package com.moveinsync.alertsystem.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that intercepts every request exactly once and extracts a
 * JWT Bearer token from the {@code Authorization} header.
 *
 * <h2>Flow</h2>
 * <ol>
 * <li>Read the {@code Authorization} header; skip filter if absent or not
 * prefixed with {@code "Bearer "}.</li>
 * <li>Extract the username from the token via {@link JwtUtil}.</li>
 * <li>If the username is non-null and no authentication is set yet, load
 * {@link UserDetails} from {@link UserDetailsService}.</li>
 * <li>Validate the token against those user details.</li>
 * <li>On success, build a {@link UsernamePasswordAuthenticationToken} and
 * place it in the {@link SecurityContextHolder}.</li>
 * <li>On failure (expired / malformed / bad signature), store a human-readable
 * error message in the request attribute {@code "jwt.error"} and continue
 * the chain without setting authentication — Spring Security will call
 * {@link com.moveinsync.alertsystem.security.JwtAuthenticationEntryPoint}
 * which reads the attribute and returns a structured JSON 401.</li>
 * </ol>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtUtil jwtUtil,
            UserDetailsService userDetailsService) {
        this.jwtUtil = jwtUtil;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        // Skip if no Bearer token is present
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Extract the raw JWT (strip "Bearer " prefix)
        final String jwt = authHeader.substring(7);
        final String username;

        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (ExpiredJwtException ex) {
            // Token was valid but its `exp` claim is in the past.
            request.setAttribute("jwt.error",
                    "JWT token has expired. Please re-authenticate.");
            filterChain.doFilter(request, response);
            return;
        } catch (SignatureException | MalformedJwtException ex) {
            // Token structure is broken or the signature does not match our secret.
            request.setAttribute("jwt.error",
                    "JWT token is invalid or has been tampered with.");
            filterChain.doFilter(request, response);
            return;
        } catch (Exception ex) {
            // Catch-all for any other JJWT parsing failure.
            request.setAttribute("jwt.error",
                    "JWT token could not be processed. Ensure it is a valid Bearer token.");
            filterChain.doFilter(request, response);
            return;
        }

        // Authenticate only if we have a username and nothing is authenticated yet
        if (username != null &&
                SecurityContextHolder.getContext().getAuthentication() == null) {

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            if (jwtUtil.isTokenValid(jwt, userDetails)) {
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities());

                authToken.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        filterChain.doFilter(request, response);
    }
}
