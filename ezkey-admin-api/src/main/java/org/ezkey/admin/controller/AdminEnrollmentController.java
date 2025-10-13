/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AdminEnrollmentController
 * Description: REST controller for admin enrollment management operations.
 */

package org.ezkey.admin.controller;

import org.ezkey.admin.dto.request.EnrollmentResetRequestDto;
import org.ezkey.admin.dto.response.EnrollmentResetResponseDto;
import org.ezkey.admin.service.AdminRecoveryService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * REST controller for admin enrollment management.
 * <p>
 * This controller provides endpoints for administrators to manage their
 * enrollments, including resetting enrollments after device loss.
 * </p>
 * <p>
 * <b>Security:</b> Enrollment reset requires a recovery token obtained from
 * /auth/recover endpoint (not a regular bearer token).
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
@RequestMapping("/api/v1/admin/enrollments")
public class AdminEnrollmentController {

    private static final Logger logger = LoggerFactory.getLogger(AdminEnrollmentController.class);

    private final AdminRecoveryService recoveryService;

    public AdminEnrollmentController(AdminRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    /**
     * Reset enrollment after device loss.
     * <p>
     * This endpoint allows administrators who have lost their device to reset
     * their enrollment, unbinding the old device and generating new credentials
     * for binding a replacement device.
     * </p>
     * <p>
     * <b>Authentication:</b> Requires a recovery token (not bearer token) obtained
     * from POST /auth/recover using a recovery code. The recovery token is valid
     * for 30 minutes and grants limited access to this endpoint only.
     * </p>
     * <p>
     * <b>Security:</b>
     * <ul>
     * <li>Validates recovery token (not bearer token)</li>
     * <li>Verifies admin owns the enrollment being reset</li>
     * <li>Unbinds old device immediately (device_public_key = null)</li>
     * <li>Generates new credentials (proof token + challenge)</li>
     * <li>Old device can no longer authenticate</li>
     * </ul>
     * </p>
     * <p>
     * <b>Workflow:</b>
     * <ol>
     * <li>Admin loses device</li>
     * <li>Admin uses recovery code → recovery token</li>
     * <li>Admin calls this endpoint → new enrollment credentials</li>
     * <li>Admin binds new device with new credentials</li>
     * <li>Admin can login passwordless with new device</li>
     * </ol>
     * </p>
     *
     * @param authorization the authorization header containing recovery token
     * @param request the reset request containing enrollment ID
     * @return ResponseEntity containing new enrollment credentials or error
     */
    @PostMapping("/reset")
    public ResponseEntity<EnrollmentResetResponseDto> resetEnrollment(
            @RequestHeader("Authorization") String authorization,
            @Valid @RequestBody EnrollmentResetRequestDto request) {
        
        try {
            // 1. Extract recovery token
            String token = authorization.replace("Bearer ", "");
            
            logger.warn("🔄 Enrollment reset request for enrollmentId: {} with recovery token", 
                request.getEnrollmentId());
            
            // 2. Validate it's a recovery token (not bearer token)
            if (!token.startsWith("ezkey_recovery_")) {
                logger.warn("❌ Invalid token type - expected recovery token, got bearer token");
                return ResponseEntity.status(403)
                    .body(new EnrollmentResetResponseDto(
                        "Invalid token type. Use recovery token from /auth/recover endpoint."));
            }
            
            // 3. Validate recovery token and get admin
            EzkeyAdmin admin = recoveryService.validateRecoveryToken(token);
            
            // 4. Reset enrollment (unbind old device, generate new credentials)
            Enrollment resetEnrollment = recoveryService.resetEnrollment(request.getEnrollmentId(), admin);
            
            // 5. Build response with new credentials
            EnrollmentResetResponseDto response = new EnrollmentResetResponseDto(
                resetEnrollment.getEnrollmentId(),
                resetEnrollment.getEnrollmentProofToken(),
                resetEnrollment.getEnrollmentChallenge(),
                resetEnrollment.getIntegrationId()
            );
            
            logger.warn("✅ Enrollment reset successful for admin: {} (enrollmentId: {})", 
                admin.getUsername(), resetEnrollment.getEnrollmentId());
            
            return ResponseEntity.ok(response);
            
        } catch (org.ezkey.admin.exception.AuthenticationException e) {
            logger.warn("❌ Enrollment reset failed (authentication): {}", e.getMessage());
            return ResponseEntity.status(403)
                .body(new EnrollmentResetResponseDto("Reset failed: " + e.getMessage()));
                
        } catch (IllegalArgumentException e) {
            logger.warn("❌ Enrollment reset failed (invalid request): {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(new EnrollmentResetResponseDto("Invalid request: " + e.getMessage()));
                
        } catch (Exception e) {
            logger.error("❌ Enrollment reset failed (unexpected): {}", e.getMessage(), e);
            return ResponseEntity.status(500)
                .body(new EnrollmentResetResponseDto("An unexpected error occurred during enrollment reset"));
        }
    }
}

