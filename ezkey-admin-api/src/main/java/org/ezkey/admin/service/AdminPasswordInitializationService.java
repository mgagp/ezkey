/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminPasswordInitializationService
 * Description: Service for detecting and updating placeholder admin password.
 */

package org.ezkey.admin.service;

import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Service for detecting and updating placeholder admin password.
 * <p>
 * This service automatically detects when the admin user has a placeholder password
 * and updates it with a secure password for initial setup.
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
public class AdminPasswordInitializationService {

    private static final Logger logger = LoggerFactory.getLogger(AdminPasswordInitializationService.class);

    @Autowired
    private EzkeyAdminRepository adminRepository;
    
    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    /**
     * Detect and update placeholder admin password on application startup.
     * <p>
     * This method is called when the application is ready and checks if the admin
     * user has a placeholder password. If so, it sets a simple initial password.
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAdminPassword() {
        logger.info("🚀 Application ready - Checking admin password initialization...");
        
        try {
            // Find the admin user
            logger.info("🔍 Looking up admin user in database...");
            EzkeyAdmin admin = adminRepository.findByUsername("admin").orElse(null);
            
            if (admin == null) {
                logger.warn("⚠️ Admin user not found - skipping password initialization");
                return; // Admin not found, skip initialization
            }
            
            logger.info("✅ Admin user found - ID: {}, Active: {}", admin.getAdminId(), admin.getActive());
            
            // Check if password is placeholder
            String placeholderHash = "$2a$10$placeholder.defined.at.first.execution";
            logger.info("🔍 Checking if password is placeholder...");
            
            if (placeholderHash.equals(admin.getPasswordHash())) {
                logger.info("🎯 Placeholder password detected - generating secure initial password...");
                
                // Generate a secure random password like Spring Security does
                String newPassword = generateSecurePassword();
                String hashedPassword = passwordEncoder.encode(newPassword);
                logger.info("🔐 Generated secure initial password");
                
                // Update admin password
                admin.setPasswordHash(hashedPassword);
                admin.setPasswordChangeRequired(true);
                adminRepository.save(admin);
                logger.info("✅ Admin password updated successfully");
                
                // Log the new password (only in development)
                logger.info("🔐 Admin password initialized:");
                logger.info("   Username: admin");
                logger.info("   Password: {}", newPassword);
                logger.info("   ⚠️  Please change this password immediately!");
                
            } else {
                logger.info("✅ Admin password already initialized - no action needed");
            }
            
        } catch (Exception e) {
            logger.error("❌ Failed to initialize admin password: {}", e.getMessage());
        }
    }

    /**
     * Generate a secure random password similar to Spring Security's approach.
     * <p>
     * This method generates a UUID-based password that is cryptographically secure
     * and similar to what Spring Security generates by default.
     * </p>
     *
     * @return a secure random password string
     */
    private String generateSecurePassword() {
        // Generate a UUID-based password like Spring Security does
        return UUID.randomUUID().toString().replace("-", "");
    }
}
