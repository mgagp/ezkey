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

import org.ezkey.admin.audit.AuditHelper;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordChangeRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminPasswordChangeResponseDto;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.service.AdminAuthService;
import org.ezkey.audit.service.AuditService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
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
    private final AuditService auditService;

    @Autowired(required = false)
    private AdminRateLimitFilter rateLimitFilter;

    public AdminAuthController(AdminAuthService authService, AuditService auditService) {
        this.authService = authService;
        this.auditService = auditService;
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
        
        String clientIp = AuditHelper.extractClientIp(httpRequest);
        String userAgent = AuditHelper.extractUserAgent(httpRequest);
        
        AdminLoginResponseDto response = authService.authenticate(request);
        
        if (response.getSuccess()) {
            logger.info("✅ Login successful for username: {} from IP: {}", 
                request.getUsername(), clientIp);
            
            // Record successful attempt for rate limiting (clears failure count)
            if (rateLimitFilter != null) {
                rateLimitFilter.recordSuccessfulAttempt(clientIp);
            }
            
            // Audit log: successful login
            auditService.logEvent(new AuditService.AuditLogBuilder()
                .eventType("ADMIN_AUTH")
                .eventAction("LOGIN")
                .eventStatus("SUCCESS")
                .apiName("ADMIN_API")
                .endpointPath(httpRequest.getRequestURI())
                .httpMethod(httpRequest.getMethod())
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .eventDetails("Username: " + request.getUsername())
            );
            
            return ResponseEntity.ok(response);
        } else {
            logger.warn("❌ Login failed for username: {} from IP: {} - Reason: {}", 
                request.getUsername(), clientIp, response.getMessage());
            
            // Record failed attempt for rate limiting (may trigger IP blocking)
            if (rateLimitFilter != null) {
                rateLimitFilter.recordFailedAttempt(clientIp);
            }
            
            // Audit log: failed login
            auditService.logEvent(new AuditService.AuditLogBuilder()
                .eventType("ADMIN_AUTH")
                .eventAction("LOGIN")
                .eventStatus("FAILURE")
                .apiName("ADMIN_API")
                .endpointPath(httpRequest.getRequestURI())
                .httpMethod(httpRequest.getMethod())
                .ipAddress(clientIp)
                .userAgent(userAgent)
                .eventDetails("Username: " + request.getUsername())
                .errorMessage(response.getMessage())
            );
            
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
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authorization,
            HttpServletRequest httpRequest) {
        try {
            // Extract bearer token from authorization header
            String bearerToken = authorization.replace("Bearer ", "");
            authService.logout(bearerToken);
            
            // Audit log: successful logout
            auditService.logEvent(new AuditService.AuditLogBuilder()
                .eventType("ADMIN_AUTH")
                .eventAction("LOGOUT")
                .eventStatus("SUCCESS")
                .apiName("ADMIN_API")
                .endpointPath(httpRequest.getRequestURI())
                .httpMethod(httpRequest.getMethod())
                .ipAddress(AuditHelper.extractClientIp(httpRequest))
                .userAgent(AuditHelper.extractUserAgent(httpRequest))
            );
            
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            // Audit log: failed logout
            auditService.logEvent(new AuditService.AuditLogBuilder()
                .eventType("ADMIN_AUTH")
                .eventAction("LOGOUT")
                .eventStatus("ERROR")
                .apiName("ADMIN_API")
                .endpointPath(httpRequest.getRequestURI())
                .httpMethod(httpRequest.getMethod())
                .ipAddress(AuditHelper.extractClientIp(httpRequest))
                .userAgent(AuditHelper.extractUserAgent(httpRequest))
                .errorMessage(e.getMessage())
            );
            
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Change administrator password.
     * <p>
     * This endpoint allows authenticated administrators to change their password.
     * It validates the current password, checks new password strength, updates
     * the password hash, and invalidates all existing tokens for security.
     * </p>
     * 
     * <p>
     * <b>Security Features:</b>
     * <ul>
     * <li>Requires valid bearer token for authentication</li>
     * <li>Validates current password before allowing change</li>
     * <li>Enforces strong password requirements (min 12 chars, uppercase, lowercase, digit, special char)</li>
     * <li>Prevents reuse of current password</li>
     * <li>Invalidates all existing tokens after successful change</li>
     * <li>Includes MFA enrollment reminder if applicable</li>
     * </ul>
     * </p>
     *
     * @param authorization the authorization header containing the bearer token
     * @param request the password change request containing current and new passwords
     * @return ResponseEntity containing password change result with enrollment reminder
     */
    @PostMapping("/change-password")
    public ResponseEntity<AdminPasswordChangeResponseDto> changePassword(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody AdminPasswordChangeRequestDto request,
            HttpServletRequest httpRequest) {
        
        try {
            // Extract and validate bearer token
            String bearerToken = authorization.replace("Bearer ", "");
            EzkeyAdmin admin = authService.validateToken(bearerToken);
            
            if (admin == null) {
                logger.warn("Password change attempt with invalid token");
                
                // Audit log: failed password change (invalid token)
                auditService.logEvent(new AuditService.AuditLogBuilder()
                    .eventType("ADMIN_AUTH")
                    .eventAction("PASSWORD_CHANGE")
                    .eventStatus("FAILURE")
                    .apiName("ADMIN_API")
                    .endpointPath(httpRequest.getRequestURI())
                    .httpMethod(httpRequest.getMethod())
                    .ipAddress(AuditHelper.extractClientIp(httpRequest))
                    .userAgent(AuditHelper.extractUserAgent(httpRequest))
                    .errorMessage("Invalid or expired token")
                );
                
                AdminPasswordChangeResponseDto errorResponse = new AdminPasswordChangeResponseDto();
                errorResponse.setSuccess(false);
                errorResponse.setMessage("Invalid or expired token");
                return ResponseEntity.status(401).body(errorResponse);
            }
            
            logger.info("🔐 Password change request received for admin: {}", admin.getUsername());
            
            // Process password change
            AdminPasswordChangeResponseDto response = authService.changePassword(admin, request);
            
            if (response.getSuccess()) {
                logger.info("✅ Password changed successfully for admin: {}", admin.getUsername());
                
                // Audit log: successful password change
                auditService.logEvent(new AuditService.AuditLogBuilder()
                    .eventType("ADMIN_AUTH")
                    .eventAction("PASSWORD_CHANGE")
                    .eventStatus("SUCCESS")
                    .apiName("ADMIN_API")
                    .endpointPath(httpRequest.getRequestURI())
                    .httpMethod(httpRequest.getMethod())
                    .ipAddress(AuditHelper.extractClientIp(httpRequest))
                    .userAgent(AuditHelper.extractUserAgent(httpRequest))
                    .adminId(admin.getAdminId())
                    .eventDetails("Admin: " + admin.getUsername())
                );
                
                return ResponseEntity.ok(response);
            } else {
                logger.warn("❌ Password change failed for admin {}: {}", 
                    admin.getUsername(), response.getMessage());
                
                // Audit log: failed password change
                auditService.logEvent(new AuditService.AuditLogBuilder()
                    .eventType("ADMIN_AUTH")
                    .eventAction("PASSWORD_CHANGE")
                    .eventStatus("FAILURE")
                    .apiName("ADMIN_API")
                    .endpointPath(httpRequest.getRequestURI())
                    .httpMethod(httpRequest.getMethod())
                    .ipAddress(AuditHelper.extractClientIp(httpRequest))
                    .userAgent(AuditHelper.extractUserAgent(httpRequest))
                    .adminId(admin.getAdminId())
                    .eventDetails("Admin: " + admin.getUsername())
                    .errorMessage(response.getMessage())
                );
                
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("❌ Password change error: {}", e.getMessage(), e);
            
            // Audit log: error during password change
            auditService.logEvent(new AuditService.AuditLogBuilder()
                .eventType("ADMIN_AUTH")
                .eventAction("PASSWORD_CHANGE")
                .eventStatus("ERROR")
                .apiName("ADMIN_API")
                .endpointPath(httpRequest.getRequestURI())
                .httpMethod(httpRequest.getMethod())
                .ipAddress(AuditHelper.extractClientIp(httpRequest))
                .userAgent(AuditHelper.extractUserAgent(httpRequest))
                .errorMessage(e.getMessage())
            );
            
            AdminPasswordChangeResponseDto errorResponse = new AdminPasswordChangeResponseDto();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("An error occurred during password change");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }
}
