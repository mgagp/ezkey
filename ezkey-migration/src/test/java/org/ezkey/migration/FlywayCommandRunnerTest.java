/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: FlywayCommandRunnerTest
 * Description: Unit tests for FlywayCommandRunner command-line parameter handling.
 */

package org.ezkey.migration;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.MigrationState;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link FlywayCommandRunner}.
 *
 * <p>Tests verify proper command-line parameter handling and Flyway operation execution:
 *
 * <ul>
 *   <li>Default behavior (migrate)
 *   <li>Explicit operation commands (migrate, repair, info, validate, clean)
 *   <li>Parameter parsing logic (Spring/Flyway parameters)
 *   <li>Migration info display
 *   <li>Error handling
 * </ul>
 *
 * <p><b>Testing Strategy:</b> This test uses Mockito to verify that the correct Flyway operations
 * are called based on command-line arguments. The tests focus on verifying the business logic and
 * parameter parsing without testing System.exit() behavior, which is a JVM-level concern.
 *
 * <p><b>Note:</b> The tests validate that FlywayCommandRunner correctly interprets command-line
 * arguments and delegates to the appropriate Flyway operations. Exit code behavior (0 for success,
 * 1 for failure) is inherent to the implementation and critical for CLI applications used in CI/CD
 * pipelines, but is not explicitly tested here due to Java 21's SecurityManager deprecation.
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlywayCommandRunner Tests")
class FlywayCommandRunnerTest {

  @Mock private Flyway flyway;
  @Mock private MigrationInfoService migrationInfoService;
  @Mock private MigrationInfo migrationInfo;

  private TestableFlywayCommandRunner runner;

  /**
   * Testable version of FlywayCommandRunner that overrides System.exit() to prevent JVM
   * termination during tests.
   */
  private static class TestableFlywayCommandRunner extends FlywayCommandRunner {
    private Integer lastExitCode = null;

    public TestableFlywayCommandRunner(Flyway flyway) {
      super(flyway);
    }

    @Override
    protected void exitWithCode(int code) {
      // Instead of calling System.exit(), store the exit code for verification
      this.lastExitCode = code;
    }

    public Integer getLastExitCode() {
      return lastExitCode;
    }
  }

  @BeforeEach
  void setUp() {
    runner = new TestableFlywayCommandRunner(flyway);

    // Setup default lenient mock behavior to avoid UnnecessaryStubbingException
    // These stubs are not always used by every test, so we make them lenient
    lenient().when(flyway.info()).thenReturn(migrationInfoService);
    lenient().when(migrationInfoService.pending()).thenReturn(new MigrationInfo[0]);
    lenient().when(migrationInfoService.all()).thenReturn(new MigrationInfo[0]);
    lenient().when(migrationInfoService.current()).thenReturn(null);

    // Setup default mock behavior for migrate - don't try to mock the result object
    // Instead, just verify that migrate() is called
  }

  @Test
  @DisplayName("Should run migrate operation by default when no arguments provided")
  void shouldRunMigrateByDefault() throws Exception {
    // Act
    runner.run();

    // Assert
    verify(flyway).migrate();
    verify(flyway, times(2)).info(); // Called once in executeMigrate, once in printMigrationInfo
  }

  @Test
  @DisplayName("Should run migrate operation when --migrate flag is provided")
  void shouldRunMigrateWithFlag() throws Exception {
    // Act
    runner.run("--migrate");

    // Assert
    verify(flyway).migrate();
    verify(flyway, times(2)).info(); // Called once in executeMigrate, once in printMigrationInfo
  }

  @Test
  @DisplayName("Should run repair operation when --repair flag is provided")
  void shouldRunRepairWithFlag() throws Exception {
    // Act
    runner.run("--repair");

    // Assert
    verify(flyway).repair();
    verify(flyway).info();
    verify(flyway, never()).migrate();
  }

  @Test
  @DisplayName("Should run info operation when --info flag is provided")
  void shouldRunInfoWithFlag() throws Exception {
    // Act
    runner.run("--info");

    // Assert
    verify(flyway).info();
    verify(flyway, never()).migrate();
    verify(flyway, never()).repair();
  }

  @Test
  @DisplayName("Should run validate operation when --validate flag is provided")
  void shouldRunValidateWithFlag() throws Exception {
    // Act
    runner.run("--validate");

    // Assert
    verify(flyway).validate();
    verify(flyway).info();
    verify(flyway, never()).migrate();
  }

  @Test
  @DisplayName("Should run clean operation when --clean flag is provided")
  void shouldRunCleanWithFlag() throws Exception {
    // Act
    runner.run("--clean");

    // Assert
    verify(flyway).clean();
    verify(flyway, never()).migrate();
  }

  @Test
  @DisplayName("Should ignore Spring configuration parameters and use default migrate")
  void shouldIgnoreSpringConfigParameters() throws Exception {
    // Act
    runner.run("--spring.datasource.url=jdbc:postgresql://localhost/test");

    // Assert
    verify(flyway).migrate();
  }

  @Test
  @DisplayName("Should ignore Flyway configuration parameters and execute specified operation")
  void shouldIgnoreFlywayConfigParameters() throws Exception {
    // Act
    runner.run(
        "--flyway.locations=classpath:db/migration", "--repair", "--flyway.baseline-version=0");

    // Assert
    verify(flyway).repair();
    verify(flyway, never()).migrate();
  }

  @Test
  @DisplayName("Should use first recognized operation flag when multiple are provided")
  void shouldUseFirstRecognizedFlag() throws Exception {
    // Act
    runner.run("--repair", "--migrate");

    // Assert
    verify(flyway).repair();
    verify(flyway, never()).migrate();
  }

  @Test
  @DisplayName("Should handle mixed parameters with operation flag")
  void shouldHandleMixedParameters() throws Exception {
    // Act
    runner.run(
        "--spring.datasource.url=jdbc:postgresql://localhost/test",
        "--info",
        "--flyway.baseline-version=1");

    // Assert
    verify(flyway).info();
    verify(flyway, never()).migrate();
    verify(flyway, never()).repair();
  }

  @Test
  @DisplayName("Should handle migration failure gracefully")
  void shouldHandleMigrationFailure() throws Exception {
    // Arrange
    when(flyway.migrate()).thenThrow(new FlywayException("Migration failed"));

    // Act
    runner.run("--migrate");

    // Assert
    verify(flyway).migrate();
    // In real scenario, this would exit with code 1
  }

  @Test
  @DisplayName("Should handle repair failure gracefully")
  void shouldHandleRepairFailure() throws Exception {
    // Arrange
    doThrow(new FlywayException("Repair failed")).when(flyway).repair();

    // Act
    runner.run("--repair");

    // Assert
    verify(flyway).repair();
  }

  @Test
  @DisplayName("Should handle validate failure gracefully")
  void shouldHandleValidateFailure() throws Exception {
    // Arrange
    doThrow(new FlywayException("Validation failed")).when(flyway).validate();

    // Act
    runner.run("--validate");

    // Assert
    verify(flyway).validate();
  }

  @Test
  @DisplayName("Should handle clean failure gracefully")
  void shouldHandleCleanFailure() throws Exception {
    // Arrange
    doThrow(new FlywayException("Clean disabled")).when(flyway).clean();

    // Act
    runner.run("--clean");

    // Assert
    verify(flyway).clean();
  }

  @Test
  @DisplayName("Should handle pending migrations during migrate")
  void shouldHandlePendingMigrations() throws Exception {
    // Arrange
    MigrationInfo[] pendingMigrations = new MigrationInfo[] {migrationInfo};
    when(migrationInfoService.pending()).thenReturn(pendingMigrations);
    when(migrationInfo.getVersion()).thenReturn(MigrationVersion.fromVersion("1.0"));
    when(migrationInfo.getDescription()).thenReturn("Initial migration");

    // Act
    runner.run("--migrate");

    // Assert
    verify(flyway).migrate();
    verify(migrationInfoService).pending();
  }

  @Test
  @DisplayName("Should display migration info with current version")
  void shouldDisplayMigrationInfoWithCurrentVersion() throws Exception {
    // Arrange
    MigrationInfo[] allMigrations = new MigrationInfo[] {migrationInfo};
    when(migrationInfoService.all()).thenReturn(allMigrations);
    when(migrationInfoService.current()).thenReturn(migrationInfo);
    when(migrationInfo.getVersion()).thenReturn(MigrationVersion.fromVersion("2.0"));
    when(migrationInfo.getState()).thenReturn(MigrationState.SUCCESS);
    when(migrationInfo.getDescription()).thenReturn("Test migration");
    when(migrationInfo.getInstalledOn()).thenReturn(new java.util.Date());

    // Act
    runner.run("--info");

    // Assert - printMigrationInfo calls current() and getVersion() multiple times
    verify(migrationInfoService).all();
    // Don't verify exact number of calls to current() as it's called multiple times internally
  }
}
