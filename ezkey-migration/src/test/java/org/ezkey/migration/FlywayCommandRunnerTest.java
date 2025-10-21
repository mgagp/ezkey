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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationInfoService;
import org.flywaydb.core.api.output.MigrateResult;
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
 *   <li>Explicit migrate command
 *   <li>Repair operation
 *   <li>Info operation
 *   <li>Validate operation
 * </ul>
 *
 * @author Ezkey contributors
 * @since 2025
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FlywayCommandRunner Tests")
class FlywayCommandRunnerTest {

  @Mock private Flyway flyway;

  @Mock private MigrationInfoService migrationInfoService;

  @Mock private MigrateResult migrateResult;

  private FlywayCommandRunner runner;

  @BeforeEach
  void setUp() {
    runner = new FlywayCommandRunner(flyway);

    // Setup default mock behavior
    when(flyway.info()).thenReturn(migrationInfoService);
    when(migrationInfoService.pending()).thenReturn(new MigrationInfo[0]);
    when(migrationInfoService.all()).thenReturn(new MigrationInfo[0]);
    when(migrationInfoService.current()).thenReturn(null);
    when(flyway.migrate()).thenReturn(migrateResult);
    when(migrateResult.migrationsExecuted).thenReturn(0);
  }

  @Test
  @DisplayName("Should run migrate operation by default when no arguments provided")
  void shouldRunMigrateByDefault() throws Exception {
    // Act
    runner.run();

    // Assert
    verify(flyway).migrate();
    verify(flyway).info();
  }

  @Test
  @DisplayName("Should run migrate operation when --migrate flag is provided")
  void shouldRunMigrateWithFlag() throws Exception {
    // Act
    runner.run("--migrate");

    // Assert
    verify(flyway).migrate();
    verify(flyway).info();
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
  @DisplayName("Should ignore Spring configuration parameters and use default migrate")
  void shouldIgnoreSpringConfigParameters() throws Exception {
    // Act
    runner.run("--spring.datasource.url=jdbc:postgresql://localhost/test");

    // Assert
    verify(flyway).migrate();
  }

  @Test
  @DisplayName("Should use first recognized operation flag when multiple are provided")
  void shouldUseFirstRecognizedFlag() throws Exception {
    // Act - repair should be executed as it comes first
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
        "--repair",
        "--flyway.baseline-version=1");

    // Assert
    verify(flyway).repair();
    verify(flyway, never()).migrate();
  }
}
