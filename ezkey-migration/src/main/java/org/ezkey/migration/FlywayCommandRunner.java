/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: FlywayCommandRunner Description: Command-line runner for executing Flyway operations with
 * parameter support.
 */

package org.ezkey.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Command-line runner for Flyway database migration operations.
 *
 * <p>This component executes Flyway operations based on command-line arguments:
 *
 * <ul>
 *   <li><b>Default (no args):</b> Runs migrate operation
 *   <li><b>--migrate:</b> Explicitly runs migrate operation
 *   <li><b>--repair:</b> Repairs the Flyway schema history table
 *   <li><b>--info:</b> Displays migration information without executing
 *   <li><b>--validate:</b> Validates applied migrations against available ones
 *   <li><b>--clean:</b> Drops all objects in configured schemas (disabled by default)
 * </ul>
 *
 * <p><b>Usage Examples:</b>
 *
 * <pre>
 * # Run migrations (default)
 * java -jar ezkey-migration.jar
 *
 * # Run migrations explicitly
 * java -jar ezkey-migration.jar --migrate
 *
 * # Repair migration history
 * java -jar ezkey-migration.jar --repair
 *
 * # Show migration information
 * java -jar ezkey-migration.jar --info
 *
 * # Validate migrations
 * java -jar ezkey-migration.jar --validate
 * </pre>
 *
 * <p><b>Security Note:</b> The clean operation is disabled by default in application.properties to
 * prevent accidental data loss.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.flywaydb.core.Flyway
 * @see org.springframework.boot.CommandLineRunner
 */
@Component
@Profile("!test")
public class FlywayCommandRunner implements CommandLineRunner {

  private static final Logger logger = LoggerFactory.getLogger(FlywayCommandRunner.class);

  private final Flyway flyway;

  /**
   * Constructs a new FlywayCommandRunner with the configured Flyway instance.
   *
   * @param flyway the Flyway instance configured by Spring Boot
   */
  public FlywayCommandRunner(Flyway flyway) {
    this.flyway = flyway;
  }

  /**
   * Executes Flyway operations based on command-line arguments.
   *
   * <p>If no arguments are provided, defaults to running migrations. Supports multiple operation
   * modes through command-line flags.
   *
   * @param args command-line arguments
   *     <ul>
   *       <li><b>--migrate:</b> Run database migrations
   *       <li><b>--repair:</b> Repair Flyway schema history
   *       <li><b>--info:</b> Display migration information
   *       <li><b>--validate:</b> Validate migrations
   *       <li><b>--clean:</b> Clean database (if enabled)
   *     </ul>
   *
   * @throws Exception if Flyway operation fails
   */
  @Override
  public void run(String... args) throws Exception {
    logger.info("Ezkey Migration Application starting...");

    // Determine operation from command-line arguments
    String operation = determineOperation(args);

    logger.info("Executing Flyway operation: {}", operation);

    try {
      switch (operation) {
        case "migrate" -> executeMigrate();
        case "repair" -> executeRepair();
        case "info" -> executeInfo();
        case "validate" -> executeValidate();
        case "clean" -> executeClean();
        default -> {
          logger.error("Unknown operation: {}", operation);
          printUsage();
          exitWithCode(1);
        }
      }

      logger.info("Flyway operation '{}' completed successfully", operation);
      exitWithCode(0);

    } catch (Exception e) {
      logger.error("Flyway operation '{}' failed: {}", operation, e.getMessage(), e);
      exitWithCode(1);
    }
  }

  /**
   * Exits the application with the specified exit code.
   *
   * <p>This method is extracted to allow test subclasses to override the exit behavior without
   * actually terminating the JVM during unit tests.
   *
   * @param code the exit code (0 for success, non-zero for failure)
   */
  protected void exitWithCode(int code) {
    System.exit(code);
  }

  /**
   * Determines the Flyway operation to execute from command-line arguments.
   *
   * @param args command-line arguments
   * @return the operation to execute (defaults to "migrate")
   */
  private String determineOperation(String... args) {
    if (args == null || args.length == 0) {
      return "migrate"; // Default operation
    }

    for (String arg : args) {
      if (arg.equals("--migrate")) {
        return "migrate";
      } else if (arg.equals("--repair")) {
        return "repair";
      } else if (arg.equals("--info")) {
        return "info";
      } else if (arg.equals("--validate")) {
        return "validate";
      } else if (arg.equals("--clean")) {
        return "clean";
      } else if (arg.startsWith("--spring.") || arg.startsWith("--flyway.")) {
        // Skip Spring/Flyway configuration parameters
        continue;
      } else if (!arg.startsWith("-")) {
        // Skip non-option arguments
        continue;
      }
    }

    return "migrate"; // Default if no recognized operation flag
  }

  /**
   * Executes Flyway migrate operation.
   *
   * <p>Migrates the database to the latest version. Pending migrations are applied in order.
   */
  private void executeMigrate() {
    logger.info("Starting database migration...");
    MigrationInfoService infoService = flyway.info();
    MigrationInfo[] pending = infoService.pending();

    if (pending.length == 0) {
      logger.info("Database is up to date. No pending migrations.");
    } else {
      logger.info("Found {} pending migration(s)", pending.length);
      for (MigrationInfo info : pending) {
        logger.info("  - {} : {}", info.getVersion(), info.getDescription());
      }
    }

    var result = flyway.migrate();
    int migrationsApplied = result != null ? result.migrationsExecuted : 0;
    logger.info("Successfully applied {} migration(s)", migrationsApplied);

    printMigrationInfo();
  }

  /**
   * Executes Flyway repair operation.
   *
   * <p>Repairs the Flyway schema history table by:
   *
   * <ul>
   *   <li>Removing failed migration entries
   *   <li>Realigning checksums of applied migrations
   * </ul>
   */
  private void executeRepair() {
    logger.info("Starting Flyway repair operation...");
    logger.info(
        "This will remove failed migration entries and realign checksums in the schema history");

    flyway.repair();
    logger.info("Flyway repair completed successfully");

    printMigrationInfo();
  }

  /**
   * Executes Flyway info operation.
   *
   * <p>Displays detailed information about all migrations without executing any changes.
   */
  private void executeInfo() {
    logger.info("Displaying migration information...");
    printMigrationInfo();
  }

  /**
   * Executes Flyway validate operation.
   *
   * <p>Validates applied migrations against available ones. Fails if:
   *
   * <ul>
   *   <li>Applied migrations have been modified
   *   <li>Applied migrations are missing from classpath
   * </ul>
   */
  private void executeValidate() {
    logger.info("Validating migrations...");
    flyway.validate();
    logger.info("Migration validation successful");
    printMigrationInfo();
  }

  /**
   * Executes Flyway clean operation.
   *
   * <p><b>WARNING:</b> This operation drops all objects in the configured schemas. It is disabled
   * by default in application.properties for safety.
   */
  private void executeClean() {
    logger.warn("Clean operation requested - this will DROP ALL database objects!");
    logger.warn("This operation is typically disabled in production environments");

    try {
      flyway.clean();
      logger.info("Database clean completed successfully");
    } catch (Exception e) {
      logger.error(
          "Clean operation failed. It may be disabled in configuration for safety: {}",
          e.getMessage());
      throw e;
    }
  }

  /**
   * Prints detailed migration information to the log.
   *
   * <p>Displays all migrations with their status (Pending, Success, Failed, etc.).
   */
  private void printMigrationInfo() {
    MigrationInfoService infoService = flyway.info();
    MigrationInfo[] all = infoService.all();

    logger.info("=== Migration Status ===");
    logger.info(
        "Schema version: {}",
        infoService.current() != null
            ? infoService.current().getVersion()
            : "No migrations applied");

    if (all.length == 0) {
      logger.info("No migrations found");
      return;
    }

    logger.info("Total migrations: {}", all.length);
    for (MigrationInfo info : all) {
      logger.info(
          "  {} | {} | {} | {}",
          info.getState(),
          info.getVersion() != null ? info.getVersion() : "N/A",
          info.getDescription(),
          info.getInstalledOn() != null ? info.getInstalledOn() : "Not installed");
    }
    logger.info("========================");
  }

  /** Prints usage information to the log. */
  private void printUsage() {
    logger.info("Usage: java -jar ezkey-migration.jar [OPTION]");
    logger.info("Options:");
    logger.info("  (no option)     Run database migrations (default)");
    logger.info("  --migrate       Explicitly run database migrations");
    logger.info("  --repair        Repair Flyway schema history table");
    logger.info("  --info          Display migration information");
    logger.info("  --validate      Validate applied migrations");
    logger.info("  --clean         Clean database (disabled by default)");
    logger.info("");
    logger.info("Spring Boot properties can be overridden:");
    logger.info("  --spring.datasource.url=jdbc:postgresql://host:port/db");
    logger.info("  --spring.datasource.username=user");
    logger.info("  --spring.datasource.password=pass");
  }
}
