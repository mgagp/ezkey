/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component Type: EmbeddedServerManager
 * Description: Manages lifecycle of embedded Spring Boot servers for functional testing
 */

package org.ezkey.tests;

import java.util.HashMap;
import java.util.Map;
import org.ezkey.admin.AdminApplication;
import org.ezkey.auth.AuthApplication;
import org.ezkey.tests.config.TestSecurityConfig;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Manages embedded Spring Boot servers for functional testing.
 *
 * <p>This class handles the lifecycle of Admin API and Auth API servers along with a PostgreSQL
 * TestContainer. It ensures proper startup, configuration, and shutdown of all components.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Starts PostgreSQL TestContainer automatically
 *   <li>Runs Flyway migrations before starting servers
 *   <li>Starts Admin API on port 9080
 *   <li>Starts Auth API on port 8080
 *   <li>Waits for servers to be ready
 *   <li>Provides shutdown for all components
 * </ul>
 *
 * @since 2025
 */
public class EmbeddedServerManager {

  private static final Logger log = LoggerFactory.getLogger(EmbeddedServerManager.class);

  private PostgreSQLContainer<?> postgresContainer;
  private ConfigurableApplicationContext adminContext;
  private ConfigurableApplicationContext authContext;

  private String adminUrl;
  private String authUrl;

  /**
   * Starts all embedded servers and database.
   *
   * <p>This method will:
   *
   * <ol>
   *   <li>Start PostgreSQL container
   *   <li>Run database migrations
   *   <li>Start Admin API
   *   <li>Start Auth API
   *   <li>Wait for all servers to be ready
   * </ol>
   *
   * @throws Exception if any component fails to start
   */
  public void startAll() throws Exception {
    log.info("Starting embedded servers for functional testing...");

    // Start PostgreSQL container
    startPostgres();

    // Run database migrations
    runMigrations();

    // Start Admin API
    startAdminApi();

    // Start Auth API
    startAuthApi();

    log.info("All embedded servers started successfully");
    log.info("Admin API: {}", adminUrl);
    log.info("Auth API: {}", authUrl);
  }

  /**
   * Stops all embedded servers and database.
   *
   * <p>Ensures graceful shutdown of all components in reverse order of startup.
   */
  public void stopAll() {
    log.info("Stopping embedded servers...");

    if (authContext != null) {
      try {
        authContext.close();
        log.info("Auth API stopped");
      } catch (Exception e) {
        log.error("Error stopping Auth API", e);
      }
    }

    if (adminContext != null) {
      try {
        adminContext.close();
        log.info("Admin API stopped");
      } catch (Exception e) {
        log.error("Error stopping Admin API", e);
      }
    }

    if (postgresContainer != null) {
      try {
        postgresContainer.stop();
        log.info("PostgreSQL container stopped");
      } catch (Exception e) {
        log.error("Error stopping PostgreSQL container", e);
      }
    }

    log.info("All embedded servers stopped");
  }

  /**
   * Gets the Admin API base URL.
   *
   * @return the Admin API URL
   */
  public String getAdminUrl() {
    return adminUrl;
  }

  /**
   * Gets the Auth API base URL.
   *
   * @return the Auth API URL
   */
  public String getAuthUrl() {
    return authUrl;
  }

  /**
   * Gets the database JDBC URL.
   *
   * @return the JDBC URL
   */
  public String getDatabaseUrl() {
    return postgresContainer != null ? postgresContainer.getJdbcUrl() : null;
  }

  private void startPostgres() {
    log.info("Starting PostgreSQL container...");

    postgresContainer =
        new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("ezkey_test")
            .withUsername("ezkey_test")
            .withPassword("ezkey_test");

    postgresContainer.start();

    log.info("PostgreSQL container started: {}", postgresContainer.getJdbcUrl());
  }

  private void runMigrations() {
    log.info("Running database migrations...");

    Flyway flyway =
        Flyway.configure()
            .dataSource(
                postgresContainer.getJdbcUrl(),
                postgresContainer.getUsername(),
                postgresContainer.getPassword())
            .locations("classpath:db/migration")
            .load();

    flyway.migrate();

    log.info("Database migrations completed");
  }

  private void startAdminApi() throws Exception {
    log.info("Starting Admin API...");

    SpringApplication app = new SpringApplication(AdminApplication.class, TestSecurityConfig.class);
    app.setWebApplicationType(WebApplicationType.SERVLET);

    // Override database and security properties
    Map<String, Object> properties = new HashMap<>();
    properties.put("spring.datasource.url", postgresContainer.getJdbcUrl());
    properties.put("spring.datasource.username", postgresContainer.getUsername());
    properties.put("spring.datasource.password", postgresContainer.getPassword());
    properties.put("spring.flyway.enabled", false);
    properties.put("spring.jpa.hibernate.ddl-auto", "validate");
    properties.put("ezkey.test.security.disabled", true);
    properties.put("ezkey.admin.ratelimit.enabled", false);
    properties.put("spring.task.scheduling.enabled", false);

    app.setDefaultProperties(properties);

    try {
      adminContext = app.run();
      adminUrl = "http://localhost:9080";
      log.info("Admin API started on port 9080");
    } catch (Exception e) {
      log.error("Failed to start Admin API", e);
      throw e;
    }
  }

  private void startAuthApi() throws Exception {
    log.info("Starting Auth API...");

    SpringApplication app = new SpringApplication(AuthApplication.class);
    app.setWebApplicationType(WebApplicationType.SERVLET);

    // Override database properties
    Map<String, Object> properties = new HashMap<>();
    properties.put("spring.datasource.url", postgresContainer.getJdbcUrl());
    properties.put("spring.datasource.username", postgresContainer.getUsername());
    properties.put("spring.datasource.password", postgresContainer.getPassword());
    properties.put("spring.flyway.enabled", false);
    properties.put("spring.jpa.hibernate.ddl-auto", "validate");
    properties.put("ezkey.auth.ratelimit.enabled", false);

    app.setDefaultProperties(properties);

    try {
      authContext = app.run();
      authUrl = "http://localhost:8080";
      log.info("Auth API started on port 8080");
    } catch (Exception e) {
      log.error("Failed to start Auth API", e);
      throw e;
    }
  }
}
