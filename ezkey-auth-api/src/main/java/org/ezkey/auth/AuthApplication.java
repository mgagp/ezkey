/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: EzkeyAuthApplication
 *
 * Description: Spring Boot application for Ezkey Auth API.
 */

package org.ezkey.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application for Ezkey Auth API.
 *
 * <p>This application provides authentication endpoints for mobile devices, including enrollment
 * and authentication attempts. It scans both the core package (for shared services and entities)
 * and the auth package (for auth-specific controllers and configuration).
 *
 * @since 2025
 */
@SpringBootApplication(
    scanBasePackages = { //
      "org.ezkey.auth", //
      "org.ezkey.authattempt", //
      "org.ezkey.enrollment", //
      "org.ezkey.exception", //
      "org.ezkey.audit", //
      "org.ezkey.signature", //
      "org.ezkey.config", // Configuration properties
      "org.ezkey.security", // Tink encryption services
    })
public class AuthApplication {

  /**
   * Main method to start the Ezkey Auth API application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    SpringApplication.run(AuthApplication.class, args);
  }
}
