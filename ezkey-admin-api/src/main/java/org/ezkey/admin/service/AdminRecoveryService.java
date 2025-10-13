/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminRecoveryService
 * Description: Service for admin account recovery using single-use recovery codes.
 */

package org.ezkey.admin.service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.integration.domain.entity.AdminTempToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTempTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for admin account recovery using single-use recovery codes.
 * <p>
 * This service manages the generation, validation, and rotation of recovery codes
 * that provide emergency access when an administrator's device is lost or unavailable.
 * Recovery codes are single-use and grant limited temporary access for enrollment re-binding.
 * </p>
 *
 * <p>
 * <b>Security Model:</b>
 * <ul>
 * <li>10 recovery codes per admin (industry standard)</li>
 * <li>Format: XXX-XXX-XXX (9 alphanumeric characters, dash-separated)</li>
 * <li>BCrypt hashed storage (same security as passwords)</li>
 * <li>Single-use: Code removed from array after successful use</li>
 * <li>Limited access: Recovery token valid 30 minutes, enrollment binding only</li>
 * </ul>
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
@Service
@Transactional
public class AdminRecoveryService {

    private static final Logger logger = LoggerFactory.getLogger(AdminRecoveryService.class);
    
    private static final int RECOVERY_CODES_COUNT = 10;
    private static final String RECOVERY_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // No ambiguous chars
    private static final int RECOVERY_TOKEN_VALIDITY_MINUTES = 30;
    
    private final EzkeyAdminRepository adminRepository;
    private final AdminTempTokenRepository tempTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom;

    public AdminRecoveryService(EzkeyAdminRepository adminRepository,
                                AdminTempTokenRepository tempTokenRepository,
                                BCryptPasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.tempTokenRepository = tempTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.secureRandom = new SecureRandom();
    }

    /**
     * Generate recovery codes for an administrator.
     * <p>
     * Generates 10 single-use recovery codes in format XXX-XXX-XXX.
     * Codes are cryptographically secure and BCrypt hashed before storage.
     * </p>
     *
     * @return RecoveryCodesResult containing plain codes (for display) and hashed codes (for storage)
     */
    public RecoveryCodesResult generateRecoveryCodes() {
        List<String> plainCodes = new ArrayList<>();
        List<String> hashedCodes = new ArrayList<>();
        
        for (int i = 0; i < RECOVERY_CODES_COUNT; i++) {
            String plainCode = generateSingleRecoveryCode();
            String hashedCode = passwordEncoder.encode(plainCode);
            
            plainCodes.add(plainCode);
            hashedCodes.add(hashedCode);
        }
        
        logger.info("✅ Generated {} recovery codes", RECOVERY_CODES_COUNT);
        
        return new RecoveryCodesResult(plainCodes, hashedCodes);
    }

    /**
     * Generate a single recovery code in format XXX-XXX-XXX.
     * <p>
     * Uses SecureRandom for cryptographic security. Format is optimized for
     * human entry (no ambiguous characters like O/0, I/1).
     * </p>
     *
     * @return recovery code string (e.g., "A3K-9PZ-X7M")
     */
    private String generateSingleRecoveryCode() {
        StringBuilder code = new StringBuilder();
        
        for (int segment = 0; segment < 3; segment++) {
            if (segment > 0) {
                code.append("-");
            }
            for (int i = 0; i < 3; i++) {
                int index = secureRandom.nextInt(RECOVERY_CODE_CHARS.length());
                code.append(RECOVERY_CODE_CHARS.charAt(index));
            }
        }
        
        return code.toString();
    }

    /**
     * Validate recovery code and issue temporary recovery token.
     * <p>
     * Validates the recovery code against the admin's stored hashed codes.
     * If valid, marks the code as used (removes from array) and issues a
     * temporary token with limited permissions (enrollment binding only).
     * </p>
     *
     * @param username the administrator username
     * @param recoveryCode the plain recovery code to validate
     * @return temporary recovery token (30-minute validity)
     * @throws AuthenticationException if validation fails
     */
    public String validateRecoveryCode(String username, String recoveryCode) {
        logger.info("🔑 Recovery code validation attempt for admin: {}", username);
        
        // 1. Find admin
        EzkeyAdmin admin = adminRepository.findByUsername(username)
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));
        
        if (!admin.getActive()) {
            throw new AuthenticationException("Account is inactive");
        }
        
        // 2. Check if admin has recovery codes
        if (admin.getRecoveryCodes() == null || admin.getRecoveryCodes().length == 0) {
            logger.warn("❌ No recovery codes available for admin: {}", username);
            throw new AuthenticationException("No recovery codes available for this account");
        }
        
        // 3. Validate recovery code against hashed codes
        List<String> remainingCodes = new ArrayList<>();
        boolean codeFound = false;
        
        for (String hashedCode : admin.getRecoveryCodes()) {
            if (!codeFound && passwordEncoder.matches(recoveryCode, hashedCode)) {
                // Code matched - mark as used (don't add to remaining codes)
                codeFound = true;
                logger.info("✅ Recovery code validated for admin: {}", username);
            } else {
                // Keep unused codes
                remainingCodes.add(hashedCode);
            }
        }
        
        if (!codeFound) {
            logger.warn("❌ Invalid recovery code for admin: {}", username);
            throw new AuthenticationException("Invalid recovery code");
        }
        
        // 4. Update admin with remaining codes (single-use enforcement)
        admin.setRecoveryCodes(remainingCodes.toArray(new String[0]));
        adminRepository.save(admin);
        
        logger.warn("🔑 Recovery code used for admin: {} ({} codes remaining)", 
            username, remainingCodes.size());
        
        // 5. Generate temporary recovery token (30 minutes, limited permissions)
        String recoveryToken = "ezkey_recovery_" + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(RECOVERY_TOKEN_VALIDITY_MINUTES);
        
        AdminTempToken tempToken = new AdminTempToken();
        tempToken.setTempToken(recoveryToken);
        tempToken.setAdmin(admin);
        tempToken.setCreatedAt(LocalDateTime.now());
        tempToken.setExpiresAt(expiresAt);
        tempToken.setMfaRequired(false); // Recovery mode - limited access, not MFA flow
        tempToken.setActive(true);
        tempTokenRepository.save(tempToken);
        
        logger.info("✅ Recovery token issued for admin: {} (expires: {}, {} recovery codes remaining)",
            username, expiresAt, remainingCodes.size());
        
        return recoveryToken;
    }

    /**
     * Rotate recovery codes for an administrator.
     * <p>
     * Generates a new set of 10 recovery codes, invalidating all previous codes.
     * This should be done after using recovery mode or periodically for security.
     * </p>
     *
     * @param admin the administrator to rotate codes for
     * @return RecoveryCodesResult with new plain codes (for display)
     */
    public RecoveryCodesResult rotateRecoveryCodes(EzkeyAdmin admin) {
        logger.info("🔄 Rotating recovery codes for admin: {}", admin.getUsername());
        
        RecoveryCodesResult result = generateRecoveryCodes();
        admin.setRecoveryCodes(result.getHashedCodes().toArray(new String[0]));
        adminRepository.save(admin);
        
        logger.info("✅ Recovery codes rotated for admin: {}", admin.getUsername());
        
        return result;
    }

    /**
     * Result object containing plain and hashed recovery codes.
     */
    public static class RecoveryCodesResult {
        private final List<String> plainCodes;
        private final List<String> hashedCodes;

        public RecoveryCodesResult(List<String> plainCodes, List<String> hashedCodes) {
            this.plainCodes = plainCodes;
            this.hashedCodes = hashedCodes;
        }

        public List<String> getPlainCodes() {
            return plainCodes;
        }

        public List<String> getHashedCodes() {
            return hashedCodes;
        }
    }
}

