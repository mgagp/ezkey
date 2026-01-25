/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: EzkeyMigrationApp
 *
 * Description: Spring Boot application dedicated to database migrations using Flyway.
 */

package org.ezkey.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayMigrationInitializer;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot application for Ezkey database migrations.
 *
 * <p>This application is dedicated to running database migrations using Flyway. It provides a clean
 * separation between the core library functionality and the migration operations, following best
 * practices for modular Spring Boot applications.
 *
 * <p><b>Key Features:</b>
 *
 * <ul>
 *   <li><b>Dedicated Migration App:</b> Separate from core library functionality
 *   <li><b>Flyway Integration:</b> Automated database schema management
 *   <li><b>Spring Boot CLI:</b> Command-line interface for migration operations
 *   <li><b>Standalone JAR:</b> Can be run independently for migration tasks
 *   <li><b>Non-Web Application:</b> Runs as CLI without web server (web-application-type=none)
 *   <li><b>Parameter Support:</b> Supports multiple Flyway operations via command-line
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 *
 * <ul>
 *   <li><b>Run Migrations (default):</b> java -jar ezkey-migration.jar
 *   <li><b>Run Migrations (explicit):</b> java -jar ezkey-migration.jar --migrate
 *   <li><b>Repair Migration History:</b> java -jar ezkey-migration.jar --repair
 *   <li><b>Show Migration Info:</b> java -jar ezkey-migration.jar --info
 *   <li><b>Validate Migrations:</b> java -jar ezkey-migration.jar --validate
 * </ul>
 *
 * <p><b>Configuration:</b> The application uses standard Spring Boot configuration properties for
 * database connection and Flyway settings. Configuration can be provided via
 * application.properties, environment variables, or command-line arguments.
 *
 * <p><b>Security Note:</b> Web and security auto-configurations are disabled via
 * application.properties since this is a CLI-only application.
 *
 * <p><b>Important:</b> Flyway's automatic migration on startup is disabled. All Flyway operations
 * are controlled by the {@link FlywayCommandRunner} to allow command-line parameter handling.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 * @see FlywayCommandRunner
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "org.ezkey")
@EntityScan(basePackages = "org.ezkey")
public class EzkeyMigrationApp {

  /**
   * Main entry point for the Ezkey migration application.
   *
   * <p>This method starts the Spring Boot application context and executes Flyway operations based
   * on command-line arguments. By default, runs database migrations if no arguments are provided.
   *
   * @param args command line arguments
   *     <ul>
   *       <li><b>--migrate:</b> Run database migrations (default if no args)
   *       <li><b>--repair:</b> Repair Flyway schema history table
   *       <li><b>--info:</b> Show migration information without executing
   *       <li><b>--validate:</b> Validate applied migrations against available ones
   *       <li><b>--spring.datasource.*:</b> Override database connection properties
   *       <li><b>--flyway.*:</b> Override Flyway configuration properties
   *     </ul>
   */
  public static void main(String[] args) {
    SpringApplication.run(EzkeyMigrationApp.class, args);
  }

  /**
   * Disables automatic Flyway migration on startup.
   *
   * <p>This bean override prevents Spring Boot's auto-configuration from running Flyway migrations
   * automatically at startup. Instead, the {@link FlywayCommandRunner} takes full control of when
   * and how Flyway operations are executed based on command-line arguments.
   *
   * <p>Without this, Spring Boot would validate/migrate the database before the CommandLineRunner
   * executes, preventing operations like repair from working when there are validation errors.
   *
   * @param flyway the Flyway instance
   * @return a no-op FlywayMigrationInitializer
   */
  @Bean
  public FlywayMigrationInitializer flywayInitializer(org.flywaydb.core.Flyway flyway) {
    // Return a custom initializer that does nothing
    // This prevents automatic migration/validation at startup
    return new FlywayMigrationInitializer(flyway, null);
  }
}
