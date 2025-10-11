/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminMfaService
 * Description: Orchestrates admin MFA attempt creation and validation using temp tokens.
 */

package org.ezkey.admin.service;

import java.time.LocalDateTime;

import org.ezkey.admin.dto.request.AdminMfaAttemptRequestDto;
import org.ezkey.admin.dto.request.AdminMfaValidateRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminMfaAttemptResponseDto;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.integration.domain.entity.AdminTempToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTempTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for admin MFA flow using temporary tokens.
 *
 * @since 2025
 */
@Service
@Transactional
public class AdminMfaService {

    private static final Logger logger = LoggerFactory.getLogger(AdminMfaService.class);

    private final AdminTempTokenRepository tempTokenRepository;
    private final AuthAttemptService authAttemptService;
    private final AdminAuthService adminAuthService;
    private final AuthAttemptRepository authAttemptRepository;
    private final AdminTempTokenTxHelper tempTokenTxHelper;

    public AdminMfaService(AdminTempTokenRepository tempTokenRepository,
                           AuthAttemptService authAttemptService,
                           AdminAuthService adminAuthService,
                           AuthAttemptRepository authAttemptRepository,
                           AdminTempTokenTxHelper tempTokenTxHelper) {
        this.tempTokenRepository = tempTokenRepository;
        this.authAttemptService = authAttemptService;
        this.adminAuthService = adminAuthService;
        this.authAttemptRepository = authAttemptRepository;
        this.tempTokenTxHelper = tempTokenTxHelper;
    }

    /**
     * Validate temp token and create an auth attempt for the admin's enrollment.
     */
    public AdminMfaAttemptResponseDto createMfaAttempt(AdminMfaAttemptRequestDto request) {
        AdminTempToken temp = tempTokenRepository.findByTempTokenAndActiveTrue(request.getTempToken())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive temp token"));

        if (temp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Temp token expired");
        }

        EzkeyAdmin admin = temp.getAdmin();
        if (admin.getMfaEnrollment() == null) {
            throw new IllegalArgumentException("Admin has no MFA enrollment bound");
        }

        AuthAttemptCreateRequest domainReq = new AuthAttemptCreateRequest();
        domainReq.setEnrollmentId(admin.getMfaEnrollment().getEnrollmentId());
        domainReq.setChallengeRequested(Boolean.FALSE);
        AuthAttemptCreateResponse created = authAttemptService.create(domainReq);

        AdminMfaAttemptResponseDto dto = new AdminMfaAttemptResponseDto();
        dto.setAuthAttemptId(created.getAuthAttemptId());
        return dto;
    }

    /**
     * Validate MFA result and exchange temp token for bearer token.
     * <p>
     * This method waits for the user to approve/reject the MFA request on their device
     * using the existing AuthAttemptService.waitForResponse() mechanism. It blocks until
     * the device responds, times out (5 minutes), or the request expires.
     * </p>
     * 
     * @param request contains tempToken and authAttemptId
     * @return login response with bearer token if accepted
     * @throws IllegalArgumentException if temp token or auth attempt is invalid
     * @throws SecurityException if MFA is rejected, expired, or times out
     */
    public AdminLoginResponseDto validateMfa(AdminMfaValidateRequestDto request) {
        // 1. Validate temp token
        AdminTempToken temp = tempTokenRepository.findByTempTokenAndActiveTrue(request.getTempToken())
            .orElseThrow(() -> new IllegalArgumentException("Invalid or inactive temp token"));

        if (temp.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Temp token expired");
        }

        EzkeyAdmin admin = temp.getAdmin();
        
        // 2. Verify admin has MFA enrollment
        if (admin.getMfaEnrollment() == null) {
            throw new IllegalArgumentException("Admin has no MFA enrollment bound");
        }

        // 3. Verify that the auth attempt belongs to this admin's enrollment
        Integer authAttemptId = request.getAuthAttemptId();
        AuthAttempt authAttempt = authAttemptRepository.findById(authAttemptId)
            .orElseThrow(() -> new IllegalArgumentException("Auth attempt not found"));
        
        if (!authAttempt.getEnrollmentId().equals(admin.getMfaEnrollment().getEnrollmentId())) {
            throw new IllegalArgumentException("Auth attempt does not belong to admin's enrollment");
        }

        // 4. Wait for device response using existing service (blocks until completion or timeout)
        // Default: 5 minutes timeout, 2 seconds polling interval
        AuthAttemptWaitRequest waitRequest = new AuthAttemptWaitRequest(300, 2);
        logger.info("⏳ Waiting for MFA response for admin: {} (authAttemptId: {})", 
            admin.getUsername(), authAttemptId);
        
        AuthAttemptWaitResponse waitResponse = authAttemptService.waitForResponse(authAttemptId, waitRequest);

        // 5. Invalidate temp token (single-use, regardless of outcome)
        // Use separate transaction helper to ensure commit even if exception is thrown
        tempTokenTxHelper.invalidateTempToken(request.getTempToken(), admin.getUsername());

        // 6. Process wait response based on status
        String status = waitResponse.getStatus();
        
        if ("ACCEPTED".equals(status)) {
            // SUCCESS: Issue bearer token
            logger.info("✅ MFA accepted for admin: {} after {}s", 
                admin.getUsername(), waitResponse.getWaitDuration());
            return adminAuthService.authenticateAfterMfa(admin);
        } 
        else if ("REJECTED".equals(status)) {
            // REJECTED: User denied the request
            logger.warn("❌ MFA rejected by user for admin: {}", admin.getUsername());
            throw new SecurityException("MFA request was rejected by user");
        } 
        else if ("EXPIRED".equals(status)) {
            // EXPIRED: Superseded by newer attempt or expired
            logger.warn("⏱️ MFA auth attempt expired for admin: {}", admin.getUsername());
            throw new SecurityException("MFA request expired or was superseded");
        }
        else if (Boolean.TRUE.equals(waitResponse.getTimeoutReached())) {
            // TIMEOUT: No response within 5 minutes
            logger.warn("⏱️ MFA timeout reached for admin: {} after {}s", 
                admin.getUsername(), waitResponse.getWaitDuration());
            throw new SecurityException("MFA request timed out - no response from device");
        }
        else {
            // UNKNOWN: Should not happen but handle defensively
            logger.error("❌ Unexpected MFA status for admin {}: {}", admin.getUsername(), status);
            throw new IllegalStateException("Unexpected auth attempt status: " + status);
        }
    }
}


