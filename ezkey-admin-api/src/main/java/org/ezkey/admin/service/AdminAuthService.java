/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminAuthService
 * Description: Service for administrator authentication.
 */

package org.ezkey.admin.service;

import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.request.AdminPasswordChangeRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.dto.response.AdminPasswordChangeResponseDto;
import org.ezkey.admin.exception.AuthenticationException;
import org.ezkey.admin.util.PasswordValidator;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for administrator authentication.
 * <p>
 * This service handles the authentication of administrators using username and password.
 * It generates bearer tokens for successful authentications and manages token lifecycle.
 * </p>
 *
 * <p>
 * <b>Token Rotation:</b>
 * When token rotation on login is enabled, this service deactivates all existing
 * active tokens for an administrator when they log in, ensuring only one active
 * token exists at any time. This improves security by limiting the attack surface
 * and invalidating stolen tokens on next legitimate login.
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
public class AdminAuthService {

    private static final Logger logger = LoggerFactory.getLogger(AdminAuthService.class);

    private final EzkeyAdminRepository adminRepository;
    
    private final AdminTokenRepository tokenRepository;
    
    private final BCryptPasswordEncoder passwordEncoder;
    
    private final AdminTokenRotationProperties rotationProperties;

    public AdminAuthService(EzkeyAdminRepository adminRepository,
                            AdminTokenRepository tokenRepository,
                            BCryptPasswordEncoder passwordEncoder,
                            AdminTokenRotationProperties rotationProperties) {
        this.adminRepository = adminRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.rotationProperties = rotationProperties;
    }

    /**
     * Authenticate administrator with username and password.
     * <p>
     * This method orchestrates the authentication flow by delegating to focused
     * private methods. It validates credentials, rotates tokens, generates a new
     * token, and returns the authentication response. If password change is required,
     * the admin still receives a token but is reminded to change their password.
     * </p>
     *
     * @param request the login request containing username and password
     * @return AdminLoginResponseDto with authentication result
     */
    public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
        logger.info("Authentication attempt for user: {}", request.getUsername());
        
        try {
            // 1. Validate credentials (username, password, active status)
            EzkeyAdmin admin = validateCredentials(request);
            
            // 2. Rotate tokens if enabled (1 active token per admin)
            rotateTokensIfEnabled(admin);
            
            // 3. Generate and persist new token
            AdminToken token = generateAndPersistToken(admin);
            
            // 4. Update admin last login timestamp
            updateLastLogin(admin);
            
            // 5. Build and return success response (with password change warning if needed)
            return buildSuccessResponse(admin, token);
            
        } catch (AuthenticationException e) {
            logger.warn("Authentication failed for {}: {}", request.getUsername(), e.getMessage());
            return buildErrorResponse(e.getMessage());
        }
    }
    
    /**
     * Validate admin credentials including username, password, and active status.
     * <p>
     * This method performs all credential validation checks and throws
     * AuthenticationException if any check fails.
     * </p>
     *
     * @param request the login request containing credentials
     * @return the validated admin entity
     * @throws AuthenticationException if validation fails
     */
    private EzkeyAdmin validateCredentials(AdminLoginRequestDto request) {
        // Find admin by username
        EzkeyAdmin admin = adminRepository.findByUsername(request.getUsername())
            .orElseThrow(() -> new AuthenticationException("Invalid credentials"));
        
        // Verify password
        if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
            throw new AuthenticationException("Invalid credentials");
        }
        
        // Check active status
        if (!admin.getActive()) {
            throw new AuthenticationException("Account is inactive");
        }
        
        logger.debug("Credentials validated for admin: {}", admin.getUsername());
        return admin;
    }
    
    /**
     * Rotate tokens on login if enabled in configuration.
     * <p>
     * Deactivates all existing active tokens for this admin to enforce
     * the "one active token per admin" security policy.
     * </p>
     *
     * @param admin the administrator whose tokens should be rotated
     */
    private void rotateTokensIfEnabled(EzkeyAdmin admin) {
        if (!rotationProperties.isRotationOnLoginEnabled()) {
            return;
        }
        
        int deactivated = tokenRepository.deactivateAllTokensForAdmin(admin.getAdminId());
        
        if (deactivated > 0) {
            logger.info("Rotated {} old tokens for admin: {}", deactivated, admin.getUsername());
        }
    }
    
    /**
     * Generate a new bearer token and persist it to database.
     * <p>
     * Creates a new AdminToken entity with all necessary metadata
     * and saves it to the database.
     * </p>
     *
     * @param admin the administrator for whom to generate the token
     * @return the persisted token entity
     */
    private AdminToken generateAndPersistToken(EzkeyAdmin admin) {
        String bearerToken = generateBearerToken();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
        
        AdminToken token = new AdminToken();
        token.setBearerToken(bearerToken);
        token.setAdmin(admin);
        token.setAdminType(admin.getAdminType().name());
        token.setTenant(admin.getTenant());
        token.setIntegration(admin.getIntegration());
        token.setExpiresAt(expiresAt);
        token.setCreatedAt(LocalDateTime.now());
        token.setActive(true);
        
        tokenRepository.save(token);
        logger.debug("Token created for admin: {}", admin.getUsername());
        
        return token;
    }
    
    /**
     * Generate a secure bearer token string.
     * <p>
     * Format: ezkey_[UUID without hyphens]
     * </p>
     *
     * @return the generated bearer token string
     */
    private String generateBearerToken() {
        return "ezkey_" + UUID.randomUUID().toString().replace("-", "");
    }
    
    /**
     * Update the admin's last login timestamp.
     *
     * @param admin the administrator whose last login should be updated
     */
    private void updateLastLogin(EzkeyAdmin admin) {
        admin.setLastLoginAt(LocalDateTime.now());
        adminRepository.save(admin);
    }
    
    /**
     * Build success response DTO.
     * <p>
     * If password change is required, includes a reminder in the response
     * but still provides a valid bearer token to allow password change.
     * </p>
     *
     * @param admin the authenticated administrator
     * @param token the generated token
     * @return success response DTO
     */
    private AdminLoginResponseDto buildSuccessResponse(EzkeyAdmin admin, AdminToken token) {
        logger.info("Authentication successful for: {}", admin.getUsername());
        
        AdminLoginResponseDto response = new AdminLoginResponseDto(
            token.getBearerToken(),
            admin.getAdminType().name(),
            admin.getUsername(),
            token.getExpiresAt()
        );
        
        // Add password change required flag if needed
        if (admin.getPasswordChangeRequired() != null && admin.getPasswordChangeRequired()) {
            response.setPasswordChangeRequired(true);
            response.setMessage("Authentication successful - Password change required. " +
                "Please use /change-password endpoint before performing other operations.");
            logger.warn("⚠️  Admin {} logged in with passwordChangeRequired=true. " +
                "Token issued for password change only.", admin.getUsername());
        }
        
        return response;
    }
    
    /**
     * Build error response DTO.
     *
     * @param message the error message
     * @return error response DTO
     */
    private AdminLoginResponseDto buildErrorResponse(String message) {
        return new AdminLoginResponseDto("Authentication failed: " + message);
    }

    /**
     * Validate bearer token.
     * <p>
     * This method validates a bearer token and returns the associated administrator
     * information if the token is valid and not expired.
     * </p>
     *
     * @param bearerToken the bearer token to validate
     * @return EzkeyAdmin if token is valid, null otherwise
     */
    @Transactional(readOnly = true)
    public EzkeyAdmin validateToken(String bearerToken) {
        try {
            AdminToken token = tokenRepository.findByBearerTokenAndActiveTrue(bearerToken)
                .orElse(null);
            
            if (token == null) {
                return null;
            }
            
            // Check if token is expired
            if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
                return null;
            }
            
            // Update last used timestamp
            token.setLastUsedAt(LocalDateTime.now());
            tokenRepository.save(token);
            
            return token.getAdmin();
            
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Logout administrator by invalidating token.
     * <p>
     * This method invalidates the bearer token, effectively logging out
     * the administrator from the system.
     * </p>
     *
     * @param bearerToken the bearer token to invalidate
     */
    public void logout(String bearerToken) {
        try {
            AdminToken token = tokenRepository.findByBearerTokenAndActiveTrue(bearerToken)
                .orElse(null);
            
            if (token != null) {
                token.setActive(false);
                tokenRepository.save(token);
            }
        } catch (Exception e) {
            // Log error but don't throw exception
        }
    }

    /**
     * Change administrator password.
     * <p>
     * This method changes the password for an authenticated administrator.
     * It validates the current password, checks new password strength,
     * updates the password hash, and invalidates all existing tokens for security.
     * </p>
     *
     * @param admin the authenticated administrator
     * @param request the password change request
     * @return AdminPasswordChangeResponseDto with change result and enrollment reminder
     */
    public AdminPasswordChangeResponseDto changePassword(EzkeyAdmin admin, 
                                                         AdminPasswordChangeRequestDto request) {
        logger.info("Password change attempt for admin: {}", admin.getUsername());
        
        try {
            // 1. Validate current password
            if (!passwordEncoder.matches(request.getCurrentPassword(), admin.getPasswordHash())) {
                logger.warn("Password change failed for {}: incorrect current password", admin.getUsername());
                return createErrorResponse("Current password is incorrect");
            }
            
            // 2. Validate new password strength
            PasswordValidator.PasswordValidationResult validationResult = 
                PasswordValidator.validate(request.getNewPassword());
            
            if (!validationResult.isValid()) {
                logger.warn("Password change failed for {}: weak password", admin.getUsername());
                return createErrorResponse(validationResult.getAllErrorsAsString());
            }
            
            // 3. Check if new password is same as current (optional security check)
            if (passwordEncoder.matches(request.getNewPassword(), admin.getPasswordHash())) {
                logger.warn("Password change failed for {}: new password same as current", admin.getUsername());
                return createErrorResponse("New password must be different from current password");
            }
            
            // 4. Update password hash
            String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
            admin.setPasswordHash(newPasswordHash);
            admin.setPasswordChangeRequired(false);
            admin.setLastPasswordChange(LocalDateTime.now());
            
            // 5. Invalidate all existing tokens (forced rotation for security)
            int deactivated = tokenRepository.deactivateAllTokensForAdmin(admin.getAdminId());
            logger.info("Invalidated {} tokens for admin {} after password change", 
                deactivated, admin.getUsername());
            
            // 6. Save admin
            adminRepository.save(admin);
            
            // 7. Build success response with enrollment reminder
            AdminPasswordChangeResponseDto response = new AdminPasswordChangeResponseDto(
                true,
                "Password changed successfully. All existing tokens have been invalidated.",
                false
            );
            
            // 8. Add MFA enrollment reminder if applicable
            addMfaEnrollmentReminderIfNeeded(admin, response);
            
            logger.info("Password changed successfully for admin: {}", admin.getUsername());
            return response;
            
        } catch (Exception e) {
            logger.error("Password change failed for {}: {}", admin.getUsername(), e.getMessage(), e);
            return createErrorResponse("Password change failed: " + e.getMessage());
        }
    }

    /**
     * Add MFA enrollment reminder to response if admin has unbound enrollment.
     *
     * @param admin the administrator
     * @param response the response to modify
     */
    private void addMfaEnrollmentReminderIfNeeded(EzkeyAdmin admin, 
                                                   AdminPasswordChangeResponseDto response) {
        // Check if admin has MFA enrollment
        if (admin.getMfaEnrollment() != null) {
            Enrollment enrollment = admin.getMfaEnrollment();
            
            // Check if enrollment is not yet bound (device public key is null)
            boolean isBound = enrollment.getDevicePublicKey() != null;
            
            if (!isBound) {
                logger.info("Adding MFA enrollment reminder for admin: {}", admin.getUsername());
                
                AdminPasswordChangeResponseDto.MfaEnrollmentInfo enrollmentInfo = 
                    new AdminPasswordChangeResponseDto.MfaEnrollmentInfo(
                        enrollment.getEnrollmentId(),
                        enrollment.getEnrollmentProofToken(),
                        enrollment.getEnrollmentChallenge(),
                        false,
                        "Use these credentials to bind your MFA enrollment for enhanced security"
                    );
                
                response.setMfaEnrollment(enrollmentInfo);
            }
        }
    }

    /**
     * Create error response for password change.
     *
     * @param message the error message
     * @return error response
     */
    private AdminPasswordChangeResponseDto createErrorResponse(String message) {
        AdminPasswordChangeResponseDto response = new AdminPasswordChangeResponseDto();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
