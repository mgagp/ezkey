/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AdminAuthController
 * Description: REST controller for administrator authentication.
 */

package org.ezkey.admin.controller;

import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.service.AdminAuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

/**
 * REST controller for administrator authentication.
 * <p>
 * This controller provides endpoints for administrator authentication including
 * login and logout functionality. It handles the generation and validation of
 * bearer tokens for API access.
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
@RestController
@RequestMapping("/api/v1/admin/auth")
public class AdminAuthController {

    private static final Logger logger = LoggerFactory.getLogger(AdminAuthController.class);

    private final AdminAuthService authService;

    @Autowired(required = false)
    private AdminRateLimitFilter rateLimitFilter;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    /**
     * Authenticate administrator with username and password.
     * <p>
     * This endpoint allows administrators to authenticate using their credentials
     * and receive a bearer token for subsequent API calls.
     * Rate limiting is applied to prevent brute force attacks.
     * </p>
     *
     * @param request the login request containing username and password
     * @param httpRequest the HTTP servlet request for IP extraction
     * @return ResponseEntity containing authentication response with bearer token
     */
    @PostMapping("/login")
    public ResponseEntity<AdminLoginResponseDto> login(
            @Valid @RequestBody AdminLoginRequestDto request,
            HttpServletRequest httpRequest) {
        
        logger.info("🌐 Login request received for username: {}", request.getUsername());
        
        AdminLoginResponseDto response = authService.authenticate(request);
        
        // Extract client IP for rate limiting tracking
        String clientIp = extractClientIp(httpRequest);
        
        if (response.getSuccess()) {
            logger.info("✅ Login successful for username: {} from IP: {}", 
                request.getUsername(), clientIp);
            
            // Record successful attempt for rate limiting (clears failure count)
            if (rateLimitFilter != null) {
                rateLimitFilter.recordSuccessfulAttempt(clientIp);
            }
            
            return ResponseEntity.ok(response);
        } else {
            logger.warn("❌ Login failed for username: {} from IP: {} - Reason: {}", 
                request.getUsername(), clientIp, response.getMessage());
            
            // Record failed attempt for rate limiting (may trigger IP blocking)
            if (rateLimitFilter != null) {
                rateLimitFilter.recordFailedAttempt(clientIp);
            }
            
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Extracts client IP address from HTTP request.
     * <p>
     * Checks X-Forwarded-For and X-Real-IP headers before falling back
     * to direct connection IP. This matches the rate limiting filter logic.
     * </p>
     *
     * @param request the HTTP servlet request
     * @return client IP address
     */
    private String extractClientIp(HttpServletRequest request) {
        // Priority 1: X-Forwarded-For (standard proxy header)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }

        // Priority 2: X-Real-IP (nginx proxy header)
        String xRealIP = request.getHeader("X-Real-IP");
        if (xRealIP != null && !xRealIP.isEmpty()) {
            return xRealIP;
        }

        // Fallback: Direct connection IP
        return request.getRemoteAddr();
    }

    /**
     * Logout administrator and invalidate token.
     * <p>
     * This endpoint invalidates the current bearer token, effectively
     * logging out the administrator from the system.
     * </p>
     *
     * @param authorization the authorization header containing the bearer token
     * @return ResponseEntity confirming logout
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader("Authorization") String authorization) {
        try {
            // Extract bearer token from authorization header
            String bearerToken = authorization.replace("Bearer ", "");
            authService.logout(bearerToken);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
