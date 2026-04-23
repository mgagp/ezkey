/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors Licensed under the MIT License. See LICENSE file in the
 * project root for full license information.
 *
 * Application: EzkeyAdminApplication Description: Spring Boot application for Ezkey Admin API.
 */

package org.ezkey.admin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for Ezkey Admin API.
 *
 * <p>This application provides administrative endpoints for managing integrations. It scans both
 * the core package (for shared services and entities) and the admin package (for admin-specific
 * controllers and configuration).
 *
 * <p><b>Scheduled Tasks:</b> This application enables scheduling for periodic tasks including:
 *
 * <ul>
 *   <li>Token cleanup and rotation
 *   <li>Encryption key rotation (KeyRotationService)
 *   <li>Re-encryption batch processing (ReencryptionService)
 *   <li>Audit log cleanup
 *   <li>Database partition creation (PartitionSchedulerService)
 *   <li>Authentication attempt TTL persistence ({@link
 *       org.ezkey.authattempt.service.AuthAttemptExpiryScheduler})
 * </ul>
 *
 * Scheduled tasks are configured via {@link org.springframework.scheduling.annotation.Scheduled}
 * annotations and can be controlled through application properties.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * <p><b>Usage:</b> Admin API application
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootApplication(
    scanBasePackages = {
      "org.ezkey.admin",
      "org.ezkey.authattempt",
      "org.ezkey.enrollment",
      "org.ezkey.integration",
      "org.ezkey.exception",
      "org.ezkey.audit", //
      "org.ezkey.alert", // Operator-facing alert subsystem (ezkey_alert)
      "org.ezkey.signature",
      "org.ezkey.config",
      "org.ezkey.instance",
      "org.ezkey.tenant",
      "org.ezkey.adminauth",
      "org.ezkey.security", // Tink encryption services
      "org.ezkey.database", // Database partition management
      "org.ezkey.service", // EntityEligibilityService and shared core services
    })
@EnableScheduling
public class AdminApplication {

  /**
   * Main method to start the Ezkey Admin API application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(AdminApplication.class, args);
  }
}
