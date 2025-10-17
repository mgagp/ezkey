/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: EzkeyAdminApplication
 * Description: Spring Boot application for Ezkey Admin API.
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
 * <p><b>Scheduled Tasks:</b> This application enables scheduling for periodic tasks such as token
 * cleanup. Scheduled tasks are configured via {@link
 * org.springframework.scheduling.annotation.Scheduled} annotations and can be controlled through
 * application properties.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
      "org.ezkey.signature",
      "org.ezkey.config", // Configuration properties
      "org.ezkey.tenant", // NOUVEAU
      "org.ezkey.adminauth", // NOUVEAU
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
