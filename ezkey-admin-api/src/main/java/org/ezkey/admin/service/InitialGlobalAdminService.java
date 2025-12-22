/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: InitialGlobalAdminService
 * Description: Service for initializing the initial global administrator with SOC 2 compliant
 *              username and email.
 */

package org.ezkey.admin.service;

import java.time.Duration;
import java.util.Optional;
import org.ezkey.admin.config.InitialGlobalAdminProperties;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for initializing the initial global administrator with SOC 2 compliant credentials.
 *
 * <p>This service ensures that the initial global admin has an identifiable username and email
 * address for SOC 2 compliance. It runs before the MFA bootstrap service to ensure the admin exists
 * with proper credentials.
 *
 * <p><b>Responsibilities:</b>
 *
 * <ul>
 *   <li>Validate that initial global admin configuration is provided
 *   <li>Find or create the initial global admin with configured username and email
 *   <li>Update existing admin if it has placeholder username
 *   <li>Ensure SOC 2 compliance (identifiable username, email required)
 * </ul>
 *
 * <p><b>Execution Order:</b> Runs before AdminBootstrapService (Order 1) to ensure admin exists
 * with proper credentials before MFA bootstrap.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@Service
public class InitialGlobalAdminService {

  private static final Logger logger = LoggerFactory.getLogger(InitialGlobalAdminService.class);

  private final EzkeyAdminRepository adminRepository;
  private final InitialGlobalAdminProperties initialGlobalAdminProperties;
  private final LockingTaskExecutor lockingTaskExecutor;

  public InitialGlobalAdminService(
      EzkeyAdminRepository adminRepository,
      InitialGlobalAdminProperties initialGlobalAdminProperties,
      LockingTaskExecutor lockingTaskExecutor) {
    this.adminRepository = adminRepository;
    this.initialGlobalAdminProperties = initialGlobalAdminProperties;
    this.lockingTaskExecutor = lockingTaskExecutor;
  }

  /**
   * Initialize the initial global administrator on application startup.
   *
   * <p>This method validates the configuration and ensures the initial global admin exists with SOC
   * 2 compliant credentials. It runs before the MFA bootstrap service.
   *
   * <p><b>HA Safety:</b> Uses distributed locking to ensure only one instance performs bootstrap in
   * HA deployments.
   */
  @EventListener(ApplicationReadyEvent.class)
  @Order(1) // Run before AdminBootstrapService (which has default Order)
  public void initializeGlobalAdmin() {
    logger.info("🔧 Initializing initial global administrator...");

    // Use distributed lock to ensure only one instance performs bootstrap in HA
    boolean executed =
        lockingTaskExecutor.executeWithLock(
            "ADMIN_STARTUP_BOOTSTRAP", Duration.ofMinutes(5), this::doInitializeGlobalAdmin);

    if (!executed) {
      logger.info("Skipping global admin initialization - another instance is handling bootstrap");
    }
  }

  /**
   * Perform the actual global admin initialization (called within distributed lock).
   *
   * <p>This method validates the configuration and ensures the initial global admin exists with SOC
   * 2 compliant credentials.
   */
  @Transactional
  private void doInitializeGlobalAdmin() {
    // Validate configuration
    validateConfiguration();

    String configuredUsername = initialGlobalAdminProperties.getUsername();
    String configuredEmail = initialGlobalAdminProperties.getEmail();
    String configuredFirstName = initialGlobalAdminProperties.getFirstName();
    String configuredLastName = initialGlobalAdminProperties.getLastName();

    // Check if admin with configured username already exists
    Optional<EzkeyAdmin> existingAdminByUsername =
        adminRepository.findByUsername(configuredUsername);

    if (existingAdminByUsername.isPresent()) {
      EzkeyAdmin admin = existingAdminByUsername.get();
      // Verify it's a GLOBAL_ADMIN
      if (admin.getAdminType() != EzkeyAdmin.AdminType.GLOBAL_ADMIN) {
        throw new IllegalStateException(
            "Admin with username '"
                + configuredUsername
                + "' exists but is not a GLOBAL_ADMIN. "
                + "Cannot use this username for initial global admin.");
      }

      // Update email, first name, and last name if not set or different
      boolean updated = false;
      if (admin.getEmail() == null || !admin.getEmail().equals(configuredEmail)) {
        admin.setEmail(configuredEmail);
        updated = true;
      }
      if (admin.getFirstName() == null || !admin.getFirstName().equals(configuredFirstName)) {
        admin.setFirstName(configuredFirstName);
        updated = true;
      }
      if (admin.getLastName() == null || !admin.getLastName().equals(configuredLastName)) {
        admin.setLastName(configuredLastName);
        updated = true;
      }
      if (updated) {
        adminRepository.save(admin);
        logger.info(
            "✅ Updated global admin: {} ({}) - {} {}",
            configuredUsername,
            configuredEmail,
            configuredFirstName,
            configuredLastName);
      } else {
        logger.info(
            "✅ Global admin already exists with correct credentials: {} ({}) - {} {}",
            configuredUsername,
            configuredEmail,
            configuredFirstName,
            configuredLastName);
      }
      return;
    }

    // Check if placeholder admin exists (created by migration V3)
    Optional<EzkeyAdmin> placeholderAdmin = adminRepository.findByUsername("admin");

    if (placeholderAdmin.isPresent()) {
      EzkeyAdmin admin = placeholderAdmin.get();
      // Verify it's a GLOBAL_ADMIN
      if (admin.getAdminType() != EzkeyAdmin.AdminType.GLOBAL_ADMIN) {
        throw new IllegalStateException(
            "Placeholder admin exists but is not a GLOBAL_ADMIN. "
                + "Cannot update to initial global admin.");
      }

      // Update placeholder admin with configured credentials
      logger.info(
          "🔄 Updating placeholder admin to configured global admin: {} ({}) - {} {}",
          configuredUsername,
          configuredEmail,
          configuredFirstName,
          configuredLastName);
      admin.setUsername(configuredUsername);
      admin.setEmail(configuredEmail);
      admin.setFirstName(configuredFirstName);
      admin.setLastName(configuredLastName);
      adminRepository.save(admin);
      logger.info(
          "✅ Global admin initialized: {} ({}) - {} {}",
          configuredUsername,
          configuredEmail,
          configuredFirstName,
          configuredLastName);
      return;
    }

    // No admin exists - create new one
    logger.info(
        "🆕 Creating initial global admin: {} ({}) - {} {}",
        configuredUsername,
        configuredEmail,
        configuredFirstName,
        configuredLastName);
    EzkeyAdmin globalAdmin = new EzkeyAdmin(configuredUsername, EzkeyAdmin.AdminType.GLOBAL_ADMIN);
    globalAdmin.setEmail(configuredEmail);
    globalAdmin.setFirstName(configuredFirstName);
    globalAdmin.setLastName(configuredLastName);
    adminRepository.save(globalAdmin);
    logger.info(
        "✅ Initial global admin created: {} ({}) - {} {}",
        configuredUsername,
        configuredEmail,
        configuredFirstName,
        configuredLastName);
  }

  /**
   * Validate that the initial global admin configuration meets SOC 2 requirements.
   *
   * @throws IllegalStateException if configuration is invalid
   */
  private void validateConfiguration() {
    String username = initialGlobalAdminProperties.getUsername();
    String email = initialGlobalAdminProperties.getEmail();
    String firstName = initialGlobalAdminProperties.getFirstName();
    String lastName = initialGlobalAdminProperties.getLastName();

    if (username == null || username.isBlank()) {
      throw new IllegalStateException(
          "Initial global admin username is REQUIRED for SOC 2 compliance. "
              + "Set ezkey.admin.initial.username to identify a specific individual.");
    }

    if (!initialGlobalAdminProperties.isUsernameValid()) {
      throw new IllegalStateException(
          "Initial global admin username '"
              + username
              + "' is generic and violates SOC 2 compliance. "
              + "Username must identify a specific individual (e.g., 'john.doe', not 'admin'). "
              + "Set ezkey.admin.initial.username to an identifiable username.");
    }

    if (email == null || email.isBlank()) {
      throw new IllegalStateException(
          "Initial global admin email is REQUIRED for SOC 2 compliance (CC6.1, CC7.2). "
              + "Set ezkey.admin.initial.email to a valid email address.");
    }

    if (firstName == null || firstName.isBlank()) {
      throw new IllegalStateException(
          "Initial global admin first name is REQUIRED for SOC 2 compliance (CC6.1, CC7.2). "
              + "Set ezkey.admin.initial.first-name to the administrator's first name.");
    }

    if (lastName == null || lastName.isBlank()) {
      throw new IllegalStateException(
          "Initial global admin last name is REQUIRED for SOC 2 compliance (CC6.1, CC7.2). "
              + "Set ezkey.admin.initial.last-name to the administrator's last name.");
    }

    logger.debug(
        "✅ Initial global admin configuration validated: {} ({}) - {} {}",
        username,
        email,
        firstName,
        lastName);
  }
}
