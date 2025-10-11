/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Controller: AdminMfaController
 * Description: Admin endpoints for MFA attempt and validation flows.
 */

package org.ezkey.admin.controller;

import java.util.Map;

import org.ezkey.admin.dto.request.AdminMfaAttemptRequestDto;
import org.ezkey.admin.dto.request.AdminMfaValidateRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminMfaAttemptResponseDto;
import org.ezkey.admin.service.AdminMfaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/**
 * REST controller exposing admin MFA operations.
 * <p>
 * This controller handles MFA authentication flow including attempt creation
 * and validation. It provides proper error handling for various failure scenarios.
 * </p>
 *
 * @since 2025
 */
@RestController
@RequestMapping("/api/v1/admin/mfa")
public class AdminMfaController {

    private static final Logger logger = LoggerFactory.getLogger(AdminMfaController.class);

    private final AdminMfaService mfaService;

    public AdminMfaController(AdminMfaService mfaService) {
        this.mfaService = mfaService;
    }

    /**
     * Create MFA attempt from a valid temporary token.
     */
    @PostMapping("/attempt")
    public ResponseEntity<AdminMfaAttemptResponseDto> createAttempt(
            @Valid @RequestBody AdminMfaAttemptRequestDto request) {
        return ResponseEntity.ok(mfaService.createMfaAttempt(request));
    }

    /**
     * Validate MFA and exchange temp token for bearer token.
     * <p>
     * This endpoint waits for device approval (blocking call up to 5 minutes)
     * and returns a bearer token on success or appropriate error on failure.
     * </p>
     * 
     * @param request validation request with temp token and auth attempt ID
     * @return bearer token on success, error message on failure
     */
    @PostMapping("/validate")
    public ResponseEntity<?> validate(@Valid @RequestBody AdminMfaValidateRequestDto request) {
        try {
            logger.info("🔐 MFA validation request received for authAttemptId: {}", request.getAuthAttemptId());
            AdminLoginResponseDto response = mfaService.validateMfa(request);
            logger.info("✅ MFA validation successful");
            return ResponseEntity.ok(response);
            
        } catch (SecurityException e) {
            // MFA rejected, expired, or timeout
            logger.warn("🔒 MFA validation failed (security): {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of(
                    "success", false,
                    "message", e.getMessage()
                ));
                
        } catch (IllegalArgumentException e) {
            // Invalid temp token or auth attempt
            logger.warn("❌ MFA validation failed (invalid request): {}", e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of(
                    "success", false,
                    "message", e.getMessage()
                ));
                
        } catch (Exception e) {
            // Unexpected error
            logger.error("❌ MFA validation failed (unexpected): {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "message", "An unexpected error occurred during MFA validation"
                ));
        }
    }
}



