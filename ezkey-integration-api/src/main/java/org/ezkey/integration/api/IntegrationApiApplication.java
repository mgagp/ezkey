/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: IntegrationApiApplication
 * Description: Spring Boot application for Ezkey Integration API — auth attempt lifecycle
 *              operations authenticated via API key (machine-to-machine).
 */

package org.ezkey.integration.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for Ezkey Integration API.
 *
 * <p>This application provides API key–authenticated endpoints for the auth attempt lifecycle:
 * create, wait, and cancel. Authentication is exclusively via API key (HTTP Basic Auth).
 *
 * <p><b>Virtual Threads:</b> This module runs on Java virtual threads ({@code
 * spring.threads.virtual.enabled=true}). The workload is I/O-bound (JDBC polling, short DB writes)
 * and scales to {@code N integrations × M users} — an unbounded volume not constrained by human
 * interaction rhythm.
 *
 * <p><b>No scheduling:</b> Key rotation and maintenance jobs run only in admin-api.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootApplication(
    scanBasePackages = {
      "org.ezkey.integration.api",
      "org.ezkey.authattempt",
      "org.ezkey.enrollment",
      "org.ezkey.integration",
      "org.ezkey.audit",
      "org.ezkey.config",
      "org.ezkey.security",
      "org.ezkey.exception",
      "org.ezkey.signature",
      "org.ezkey.service", // Shared core services (EntityEligibilityService, …)
    })
public class IntegrationApiApplication {

  /**
   * Main method to start the Ezkey Integration API application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(IntegrationApiApplication.class, args);
  }
}
