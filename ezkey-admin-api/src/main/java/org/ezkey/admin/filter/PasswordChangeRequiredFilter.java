/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Filter: PasswordChangeRequiredFilter
 * Description: Security filter that blocks access to endpoints when password change is required.
 */

package org.ezkey.admin.filter;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * Security filter that blocks access to most endpoints when password change is required.
 * <p>
 * This filter ensures that administrators with temporary passwords cannot access
 * sensitive endpoints until they change their password. Only login, logout, and
 * change-password endpoints are allowed when passwordChangeRequired=true.
 * </p>
 *
 * <p>
 * <b>Security Rationale:</b>
 * Without this filter, an administrator could continue using a temporary password
 * indefinitely by never calling the change-password endpoint. This filter enforces
 * the password change requirement by blocking all other API access.
 * </p>
 *
 * <p>
 * <b>Allowed Endpoints:</b>
 * <ul>
 * <li>/api/v1/admin/auth/login - Allow initial login</li>
 * <li>/api/v1/admin/auth/logout - Allow logout at any time</li>
 * <li>/api/v1/admin/auth/change-password - Allow password change</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Blocked Endpoints (when passwordChangeRequired=true):</b>
 * All other endpoints return 403 Forbidden with a descriptive error message.
 * </p>
 *
 * <p>
 * <b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 * </p>
 * <p>
 * <b>License:</b> MIT
 * </p>
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class PasswordChangeRequiredFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(PasswordChangeRequiredFilter.class);

    private final AdminTokenRepository adminTokenRepository;

    /**
     * List of endpoints that are accessible even when password change is required.
     */
    private static final List<String> ALLOWED_ENDPOINTS = Arrays.asList(
        "/api/v1/admin/auth/login",
        "/api/v1/admin/auth/logout",
        "/api/v1/admin/auth/change-password"
    );

    /**
     * Constructs the filter with required dependencies.
     *
     * @param adminTokenRepository repository for admin token operations
     */
    public PasswordChangeRequiredFilter(AdminTokenRepository adminTokenRepository) {
        this.adminTokenRepository = adminTokenRepository;
    }

    /**
     * Filters incoming requests to block access when password change is required.
     * <p>
     * This method intercepts all admin API requests and checks if the authenticated
     * administrator has passwordChangeRequired=true. If so, only allowed endpoints
     * are accessible; all others return 403 Forbidden.
     * </p>
     *
     * @param request the servlet request
     * @param response the servlet response
     * @param chain the filter chain
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String requestUri = httpRequest.getRequestURI();
        String method = httpRequest.getMethod();

        // Skip filter for allowed endpoints
        if (isAllowedEndpoint(requestUri)) {
            chain.doFilter(request, response);
            return;
        }

        // Extract bearer token from Authorization header
        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            // No token, let other security filters handle authentication
            chain.doFilter(request, response);
            return;
        }

        String bearerToken = authHeader.substring(7);

        // Validate token and check passwordChangeRequired
        try {
            logger.debug("🔍 Filter checking request: {} {}", method, requestUri);
            logger.debug("🔍 Bearer token: {}...", bearerToken.substring(0, Math.min(20, bearerToken.length())));
            
            AdminToken adminToken = adminTokenRepository
                .findByBearerTokenAndActiveTrueWithAdmin(bearerToken)
                .orElse(null);

            if (adminToken != null) {
                EzkeyAdmin admin = adminToken.getAdmin();
                logger.debug("🔍 Admin found: {}, passwordChangeRequired: {}", 
                    admin.getUsername(), admin.getPasswordChangeRequired());

                if (admin != null && admin.getPasswordChangeRequired() != null 
                        && admin.getPasswordChangeRequired()) {
                    // Password change required - block access
                    logger.warn("⚠️  Blocked request to {} for admin '{}' - password change required",
                        requestUri, admin.getUsername());

                    httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    httpResponse.setContentType("application/json");
                    httpResponse.setCharacterEncoding("UTF-8");
                    httpResponse.getWriter().write(
                        "{" +
                        "\"error\":\"Password change required\"," +
                        "\"message\":\"You must change your temporary password before accessing other resources. " +
                        "Please use POST /api/v1/admin/auth/change-password endpoint.\"," +
                        "\"endpoint\":\"/api/v1/admin/auth/change-password\"" +
                        "}"
                    );
                    return;
                } else {
                    logger.debug("🔍 Admin passwordChangeRequired=false, allowing request");
                }
            } else {
                logger.debug("🔍 No admin token found for bearer token");
            }

            // Token valid and no password change required, continue
            logger.debug("🔍 Allowing request to proceed");
            chain.doFilter(request, response);

        } catch (Exception e) {
            logger.error("Error in PasswordChangeRequiredFilter: {}", e.getMessage(), e);
            // On error, let request proceed to avoid blocking legitimate requests
            chain.doFilter(request, response);
        }
    }

    /**
     * Checks if the requested endpoint is in the allowed list.
     * <p>
     * Uses endsWith() to match endpoints regardless of context path or additional path segments.
     * </p>
     *
     * @param requestUri the request URI to check
     * @return true if endpoint is allowed, false otherwise
     */
    private boolean isAllowedEndpoint(String requestUri) {
        return ALLOWED_ENDPOINTS.stream()
            .anyMatch(requestUri::endsWith);
    }
}

