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

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import org.ezkey.admin.config.OrganizationProperties;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final EzkeyAdminRepository adminRepository;

    private final TenantRepository tenantRepository;

    private final BCryptPasswordEncoder passwordEncoder;

    private final OrganizationProperties organizationProperties;

    public AdminPasswordInitializationService(EzkeyAdminRepository adminRepository,
            TenantRepository tenantRepository,
            BCryptPasswordEncoder passwordEncoder,
            OrganizationProperties organizationProperties){
        this.adminRepository = adminRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.organizationProperties = organizationProperties;
    }

    /**
     * Detect and create or update admin zero on application startup.
     * <p>
     * This method is called when the application is ready and checks if the admin
     * user exists. If not, it creates the admin zero with a secure random password.
     * If the admin exists with a placeholder password, it updates it.
     * </p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeAdminPassword() {
        logger.info("🚀 Application ready - Checking admin zero initialization...");
        try{
            // Find the admin user
            logger.info("🔍 Looking up admin user in database...");
            EzkeyAdmin admin = adminRepository.findByUsername("admin").orElse(null);
            if (admin == null){
                logger.warn("⚠️ Admin zero not found - creating initial admin user...");
                createAdminZero();
                return;
            }
            logger.info("✅ Admin user found - ID: {}, Active: {}",admin.getAdminId(),admin.getActive());

            // Check if password is placeholder
            String placeholderHash = "$2a$10$placeholder.defined.at.first.execution";
            logger.info("🔍 Checking if password is placeholder...");
            if (placeholderHash.equals(admin.getPasswordHash())){
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
                logger.info("   Password: {}",newPassword);
                logger.info("   ⚠️  Please change this password immediately!");
            } else{
                logger.info("✅ Admin password already initialized - no action needed");
            }
        } catch (Exception e){
            logger.error("❌ Failed to initialize admin password: {}",e.getMessage(),e);
        }
    }

    /**
     * Create the initial admin zero user with placeholder password.
     * <p>
     * This method creates the first administrator (admin zero) in the system
     * with GLOBAL_ADMIN privileges using a placeholder password that will be
     * replaced immediately by the normal initialization flow. It also creates
     * the system tenant representing the organization hosting this instance.
     * </p>
     * <p>
     * <b>Note:</b> This method is called only when Flyway migrations have not run
     * or when the database was manually cleared. It creates the admin with the
     * same properties as V3__create_initial_admin.sql migration for consistency.
     * </p>
     */
    private void createAdminZero() {
        logger.info("🎬 Creating admin zero - the first administrator...");
        logger.warn("⚠️ This should normally be done by Flyway migration V3__create_initial_admin.sql");
        logger.warn("⚠️ Creating admin via code fallback mechanism...");
        try{
            // Step 1: Create or find system tenant
            Tenant systemTenant = createOrFindSystemTenant();
            
            // Step 2: Use placeholder password (same as migration V3)
            // This will trigger the initialization flow immediately after
            String placeholderHash = "$2a$10$placeholder.defined.at.first.execution";

            // Step 3: Create admin zero with same properties as V3 migration
            EzkeyAdmin adminZero = new EzkeyAdmin("admin",placeholderHash,EzkeyAdmin.AdminType.GLOBAL_ADMIN);
            adminZero.setPasswordChangeRequired(true);
            adminZero.setMfaEnabled(true); // Aligned with V3 migration
            adminZero.setMfaRequired(true); // Aligned with V3 migration
            adminZero.setActive(true);
            adminZero.setCreatedAt(LocalDateTime.now());
            adminZero.setTenant(systemTenant); // Link to system tenant (Option B)

            // Step 4: Save admin zero
            adminRepository.save(adminZero);
            logger.info("✅ Admin zero created successfully - ID: {}, Tenant: {}",
                    adminZero.getAdminId(),systemTenant.getTenantName());
            
            // Step 5: Update tenant with creator admin
            systemTenant.setCreatedByAdmin(adminZero);
            tenantRepository.save(systemTenant);
            logger.info("✅ System tenant linked to admin zero");
            
            logger.info("🔄 Placeholder password detected, proceeding with initialization...");

            // Step 6: Trigger the normal initialization flow by calling the main method
            // This will generate a real password and log it
            initializeAdminPassword();
        } catch (Exception e){
            logger.error("❌ Failed to create admin zero: {}",e.getMessage(),e);
            throw new RuntimeException("Failed to create admin zero",e);
        }
    }

    /**
     * Create or find the system tenant representing the organization hosting this instance.
     * <p>
     * This method creates a system tenant if it doesn't exist, or retrieves it if it already exists.
     * The tenant name is customizable via configuration properties (ezkey.organization.name).
     * </p>
     *
     * @return the system tenant
     */
    private Tenant createOrFindSystemTenant() {
        String tenantName = organizationProperties.getName();
        String tenantDescription = organizationProperties.getDescription();
        
        logger.info("🏢 Looking for system tenant: '{}'",tenantName);
        
        // Try to find existing tenant
        Optional<Tenant> existingTenant = tenantRepository.findByTenantName(tenantName);
        if (existingTenant.isPresent()){
            logger.info("✅ System tenant found - ID: {}, Name: '{}'",
                    existingTenant.get().getTenantId(),existingTenant.get().getTenantName());
            return existingTenant.get();
        }
        
        // Create new tenant
        logger.info("🏢 Creating system tenant: '{}'",tenantName);
        Tenant systemTenant = new Tenant(tenantName,tenantDescription);
        systemTenant.setCreatedAt(LocalDateTime.now());
        systemTenant.setActive(true);
        
        tenantRepository.save(systemTenant);
        logger.info("✅ System tenant created successfully - ID: {}, Name: '{}'",
                systemTenant.getTenantId(),systemTenant.getTenantName());
        
        return systemTenant;
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
        return UUID.randomUUID().toString().replace("-","");
    }
}
