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

import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
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

    public AdminAuthService(EzkeyAdminRepository adminRepository,
                            AdminTokenRepository tokenRepository,
                            BCryptPasswordEncoder passwordEncoder) {
        this.adminRepository = adminRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Authenticate administrator with username and password.
     * <p>
     * This method validates the administrator credentials and generates a bearer token
     * for successful authentications. The token is valid for 24 hours.
     * </p>
     *
     * @param request the login request containing username and password
     * @return AdminLoginResponseDto with authentication result
     * @throws RuntimeException if authentication fails
     */
    public AdminLoginResponseDto authenticate(AdminLoginRequestDto request) {
        logger.info("🔐 Starting authentication for username: {}", request.getUsername());
        
        try {
            // Find administrator by username
            logger.info("📋 Looking up administrator in database...");
            EzkeyAdmin admin = adminRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new RuntimeException("Administrator not found"));
            
            logger.info("✅ Administrator found - ID: {}, Type: {}, Active: {}", 
                admin.getAdminId(), admin.getAdminType(), admin.getActive());
            
            // Verify password
            logger.info("🔑 Verifying password...");
            if (!passwordEncoder.matches(request.getPassword(), admin.getPasswordHash())) {
                logger.warn("❌ Password verification failed for username: {}", request.getUsername());
                throw new RuntimeException("Invalid password");
            }
            logger.info("✅ Password verification successful");
            
            // Check if admin is active
            if (!admin.getActive()) {
                logger.warn("❌ Administrator account is inactive for username: {}", request.getUsername());
                throw new RuntimeException("Administrator account is inactive");
            }
            logger.info("✅ Administrator account is active");
            
            // Generate bearer token
            logger.info("🎫 Generating bearer token...");
            String bearerToken = "ezkey_" + UUID.randomUUID().toString().replace("-", "");
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
            logger.info("✅ Bearer token generated - Expires at: {}", expiresAt);
            
            // Create token entity
            logger.info("💾 Creating token entity...");
            AdminToken token = new AdminToken();
            token.setBearerToken(bearerToken);
            token.setAdmin(admin);
            token.setAdminType(admin.getAdminType().name());
            token.setTenant(admin.getTenant());
            token.setIntegration(admin.getIntegration());
            token.setExpiresAt(expiresAt);
            token.setCreatedAt(LocalDateTime.now());
            token.setActive(true);
            
            // Save token
            logger.info("💾 Saving token to database...");
            tokenRepository.save(token);
            logger.info("✅ Token saved successfully");
            
            // Update last login
            logger.info("🕒 Updating last login timestamp...");
            admin.setLastLoginAt(LocalDateTime.now());
            adminRepository.save(admin);
            logger.info("✅ Last login updated");
            
            // Return success response
            logger.info("🎉 Authentication successful for username: {} - Token: {}...", 
                request.getUsername(), bearerToken.substring(0, Math.min(20, bearerToken.length())));
            
            return new AdminLoginResponseDto(
                bearerToken,
                admin.getAdminType().name(),
                admin.getUsername(),
                expiresAt
            );
            
        } catch (Exception e) {
            logger.error("❌ Authentication failed for username: {} - Error: {}", 
                request.getUsername(), e.getMessage());
            // Return error response
            return new AdminLoginResponseDto("Authentication failed: " + e.getMessage());
        }
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
}
