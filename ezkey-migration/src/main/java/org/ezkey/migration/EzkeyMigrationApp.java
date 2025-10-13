/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Application: EzkeyMigrationApp
 * Description: Spring Boot application dedicated to database migrations using Flyway.
 */

package org.ezkey.migration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
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
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 *
 * <ul>
 *   <li><b>Run Migrations:</b> java -jar ezkey-migration.jar
 *   <li><b>Show Migration Info:</b> java -jar ezkey-migration.jar --info
 *   <li><b>Repair Migrations:</b> java -jar ezkey-migration.jar --repair
 * </ul>
 *
 * <p><b>Configuration:</b> The application uses standard Spring Boot configuration properties for
 * database connection and Flyway settings. Configuration can be provided via
 * application.properties, environment variables, or command-line arguments.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.springframework.boot.SpringApplication
 * @see org.springframework.boot.autoconfigure.SpringBootApplication
 */
@SpringBootApplication
@EnableJpaRepositories(basePackages = "org.ezkey")
@EntityScan(basePackages = "org.ezkey")
public class EzkeyMigrationApp {

  /**
   * Main entry point for the Ezkey migration application.
   *
   * <p>This method starts the Spring Boot application context and runs the database migrations
   * using Flyway. The application will automatically detect and execute any pending migrations.
   *
   * @param args command line arguments
   *     <ul>
   *       <li><b>--info:</b> Show migration information without executing
   *       <li><b>--repair:</b> Repair migration history table
   *       <li><b>--spring.datasource.*:</b> Database connection properties
   *     </ul>
   */
  public static void main(String[] args) {
    SpringApplication.run(EzkeyMigrationApp.class, args);
  }
}
