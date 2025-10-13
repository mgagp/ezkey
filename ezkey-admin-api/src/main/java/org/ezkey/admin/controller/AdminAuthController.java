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

import java.time.LocalDateTime;

import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordChangeRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordlessWaitRequestDto;
import org.ezkey.admin.dto.request.AdminRecoveryRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminPasswordChangeResponseDto;
import org.ezkey.admin.dto.response.AdminRecoveryResponseDto;
import org.ezkey.admin.security.AdminRateLimitFilter;
import org.ezkey.admin.service.AdminAuthService;
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
    
    private final org.ezkey.admin.service.AdminRecoveryService recoveryService;

    @Autowired(required = false)
    private AdminRateLimitFilter rateLimitFilter;

    public AdminAuthController(AdminAuthService authService,
                                org.ezkey.admin.service.AdminRecoveryService recoveryService) {
        this.authService = authService;
        this.recoveryService = recoveryService;
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
            @Valid @RequestBody AdminPasswordChangeRequestDto request) {
        
        try {
            // Extract and validate bearer token
            String bearerToken = authorization.replace("Bearer ", "");
            EzkeyAdmin admin = authService.validateToken(bearerToken);
            
            if (admin == null) {
                logger.warn("Password change attempt with invalid token");
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
                return ResponseEntity.ok(response);
            } else {
                logger.warn("❌ Password change failed for admin {}: {}", 
                    admin.getUsername(), response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }
            
        } catch (Exception e) {
            logger.error("❌ Password change error: {}", e.getMessage(), e);
            AdminPasswordChangeResponseDto errorResponse = new AdminPasswordChangeResponseDto();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("An error occurred during password change");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Wait for passwordless authentication completion.
     * <p>
     * This endpoint is used in the two-step passwordless flow when challenge
     * verification is required. After receiving authAttemptId and challengeCode
     * from the /login endpoint, the client displays the challenge to the user
     * then calls this endpoint to wait for device approval.
     * </p>
     * <p>
     * This endpoint blocks for up to 5 minutes waiting for the device response.
     * </p>
     * <p>
     * <b>Security:</b> The challengeCode is required to prevent enumeration attacks
     * on authAttemptId. Only clients that legitimately initiated the authentication
     * and received the challenge can proceed.
     * </p>
     *
     * @param request the wait request containing auth attempt ID and challenge code
     * @return ResponseEntity with bearer token on success, error on failure
     */
    @PostMapping("/passwordless-wait")
    public ResponseEntity<AdminLoginResponseDto> passwordlessWait(
            @Valid @RequestBody AdminPasswordlessWaitRequestDto request) {
        
        try {
            logger.info("🔐 Passwordless wait request for authAttemptId: {}", request.getAuthAttemptId());
            
            AdminLoginResponseDto response = authService.waitForPasswordlessAuth(
                request.getAuthAttemptId(), 
                request.getChallengeCode()
            );
            
            logger.info("✅ Passwordless authentication successful");
            return ResponseEntity.ok(response);
            
        } catch (org.ezkey.admin.exception.AuthenticationException e) {
            logger.warn("❌ Passwordless wait failed (authentication): {}", e.getMessage());
            return ResponseEntity.status(403)
                .body(new AdminLoginResponseDto("Authentication failed: " + e.getMessage()));
            
        } catch (IllegalArgumentException e) {
            logger.warn("❌ Passwordless wait failed (invalid request): {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(new AdminLoginResponseDto("Invalid request: " + e.getMessage()));
                
        } catch (Exception e) {
            logger.error("❌ Passwordless wait failed (unexpected): {}", e.getMessage(), e);
            AdminLoginResponseDto errorResponse = new AdminLoginResponseDto();
            errorResponse.setSuccess(false);
            errorResponse.setMessage("An unexpected error occurred during authentication");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    /**
     * Recover admin access using a recovery code.
     * <p>
     * This endpoint allows administrators who have lost access to their enrolled
     * device to regain access using one of their single-use recovery codes.
     * The recovery code grants a temporary token (30 minutes validity) with
     * limited permissions to re-bind enrollment only.
     * </p>
     * <p>
     * <b>Security Features:</b>
     * <ul>
     * <li>Single-use recovery codes (removed from array after use)</li>
     * <li>BCrypt hashed storage</li>
     * <li>Limited token (30 min validity, enrollment binding only)</li>
     * <li>Rate limited to prevent brute force</li>
     * <li>Audit logged as critical security event</li>
     * </ul>
     * </p>
     *
     * @param request the recovery request containing username and recovery code
     * @param httpRequest the HTTP servlet request for IP extraction
     * @return ResponseEntity containing recovery token or error
     */
    @PostMapping("/recover")
    public ResponseEntity<AdminRecoveryResponseDto> recover(
            @Valid @RequestBody AdminRecoveryRequestDto request,
            HttpServletRequest httpRequest) {
        
        String clientIp = extractClientIp(httpRequest);
        
        try {
            logger.warn("🔑 Recovery attempt for admin: {} from IP: {}", request.getUsername(), clientIp);
            
            String recoveryToken = recoveryService.validateRecoveryCode(
                request.getUsername(), 
                request.getRecoveryCode()
            );
            
            // Get admin to determine codes remaining
            // Note: Use validateRecoveryToken (not validateToken) since it's a temp token, not bearer
            EzkeyAdmin admin = recoveryService.validateRecoveryToken(recoveryToken);
            int codesRemaining = admin != null && admin.getRecoveryCodes() != null 
                ? admin.getRecoveryCodes().length 
                : 0;
            
            AdminRecoveryResponseDto response = new AdminRecoveryResponseDto(
                recoveryToken,
                LocalDateTime.now().plusMinutes(30),
                codesRemaining
            );
            
            logger.warn("✅ Recovery successful for admin: {} ({} codes remaining)", 
                request.getUsername(), codesRemaining);
            
            // Record successful attempt for rate limiting
            if (rateLimitFilter != null) {
                rateLimitFilter.recordSuccessfulAttempt(clientIp);
            }
            
            return ResponseEntity.ok(response);
            
        } catch (org.ezkey.admin.exception.AuthenticationException e) {
            logger.warn("❌ Recovery failed for admin: {} from IP: {} - Reason: {}", 
                request.getUsername(), clientIp, e.getMessage());
            
            // Record failed attempt for rate limiting
            if (rateLimitFilter != null) {
                rateLimitFilter.recordFailedAttempt(clientIp);
            }
            
            return ResponseEntity.status(403)
                .body(new AdminRecoveryResponseDto("Recovery failed: " + e.getMessage()));
                
        } catch (Exception e) {
            logger.error("❌ Recovery error for admin: {} - {}", request.getUsername(), e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new AdminRecoveryResponseDto("An error occurred during recovery"));
        }
    }
}
