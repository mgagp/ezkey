/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: AcmeDemoApplication
 * Description: Main Spring Boot application for ACME demo integrating with Ezkey.
 */

package org.ezkey.demo.acme;

import java.nio.file.Path;
import org.ezkey.demo.acme.config.AcmeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main Spring Boot application for ACME demo.
 *
 * <p>This application demonstrates EZKey passwordless login integration using backend-side
 * authentication with API Key M2M communication.
 *
 * <p><b>Demo Purpose:</b> Provides a realistic demonstration of EZKey integration for developers.
 * Shows how an external application can leverage EZKey's API to implement secure passwordless
 * authentication flows.
 *
 * <p><b>Technical Stack:</b>
 *
 * <ul>
 *   <li><b>Backend:</b> Spring Boot 3.3.6, Java 21
 *   <li><b>Frontend:</b> Thymeleaf templates
 *   <li><b>UI Design:</b> Neo Brutalism styling
 *   <li><b>Port:</b> 8082
 * </ul>
 *
 * <p><b>Integration:</b> Communicates with ezkey-admin-api (port 9080) using API Key M2M
 * authentication to create auth attempts and wait for device approval.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AcmeProperties.class)
public class DemoAcmeApplication {

  private static final Logger logger = LoggerFactory.getLogger(DemoAcmeApplication.class);

  /**
   * Main method to start the ACME demo application.
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {
    // Log Spring Boot config locations before startup
    String additionalLocation = System.getenv("SPRING_CONFIG_ADDITIONAL_LOCATION");
    logger.info(
        "SPRING_CONFIG_ADDITIONAL_LOCATION env var: {}",
        additionalLocation != null ? additionalLocation : "NOT SET");
    logger.info("Working directory: {}", System.getProperty("user.dir"));
    logger.info(
        "Config file exists: {}",
        java.nio.file.Files.exists(Path.of("/app/config/application.properties")));

    var appContext = SpringApplication.run(DemoAcmeApplication.class, args);

    // Log configuration status after startup
    AcmeProperties properties = appContext.getBean(AcmeProperties.class);
    logger.info("Configuration loaded - Admin API URL: {}", properties.getAdminApiUrl());
    logger.info(
        "Configuration loaded - Integration Key: {}",
        properties.getIntegrationKey() != null && !properties.getIntegrationKey().isBlank()
            ? properties
                    .getIntegrationKey()
                    .substring(0, Math.min(20, properties.getIntegrationKey().length()))
                + "..."
            : "NOT SET");
    logger.info(
        "Configuration loaded - Secret Key: {}",
        properties.getSecretKey() != null && !properties.getSecretKey().isBlank()
            ? "SET (hidden)"
            : "NOT SET");

    // Also check environment directly
    org.springframework.core.env.Environment env = appContext.getEnvironment();
    logger.info(
        "Environment property ezkey.integration-key: {}",
        env.getProperty("ezkey.integration-key", "NOT FOUND"));
    logger.info(
        "Environment property ezkey.secret-key: {}",
        env.getProperty("ezkey.secret-key", "NOT FOUND") != null
                && !env.getProperty("ezkey.secret-key", "").isBlank()
            ? "SET (hidden)"
            : "NOT FOUND");
  }
}
