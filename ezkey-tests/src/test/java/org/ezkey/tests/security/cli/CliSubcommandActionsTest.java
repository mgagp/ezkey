/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CliSubcommandActionsTest
 * Description: Tests for specific subcommand actions and their behaviors.
 */

// This file is UTF-8 without BOM.

package org.ezkey.tests.security.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CliExecutionResult;
import org.ezkey.tests.util.CliTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive tests for specific CLI subcommand actions.
 *
 * <p>This test suite validates:
 *
 * <ul>
 *   <li>Admin subcommand capabilities and options
 *   <li>Auth subcommand operations
 *   <li>Configure subcommand functionality
 *   <li>Crypto subcommand operations
 *   <li>Database subcommand capabilities
 * </ul>
 *
 * <p>Uses nested test classes to organize tests by subcommand.
 *
 * @since 2025
 */
@Tag(TestTags.CLI)
@Tag(TestTags.SLOW)
public class CliSubcommandActionsTest extends AbstractSecurityTest {

  private CliTestHelper cliTestHelper;

  @BeforeEach
  void setUpCli() {
    cliTestHelper = new CliTestHelper(dockerStackConfig);
    cliTestHelper.configureDefault();
  }

  // ============================================================================
  // Admin Subcommand Tests
  // ============================================================================

  /** Nested test class for admin subcommand operations. */
  @Nested
  class AdminSubcommandTests {

    /** Tests that admin subcommand exists and provides help. */
    @Test
    void testAdminHelpAvailable() {
      CliExecutionResult result = cliTestHelper.execute("admin", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).containsIgnoringCase("admin");
    }

    /** Tests that admin subcommand without arguments shows usage. */
    @Test
    void testAdminWithoutArgumentsShowsUsage() {
      CliExecutionResult result = cliTestHelper.execute("admin");

      assertThat(result.exitCode()).isNotEqualTo(0);
      String output = result.stdout() + result.stderr();
      assertThat(output)
          .satisfiesAnyOf(
              s -> assertThat(s).containsIgnoringCase("usage"),
              s -> assertThat(s).containsIgnoringCase("command"));
    }

    /** Tests that admin accepts the standard options like timeout. */
    @Test
    void testAdminWithTimeoutOption() {
      CliExecutionResult result = cliTestHelper.execute("--timeout=3000", "admin", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
    }

    /** Tests that admin accepts verbose flag. */
    @Test
    void testAdminWithVerboseFlag() {
      CliExecutionResult result = cliTestHelper.execute("--verbose", "admin", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
    }
  }

  // ============================================================================
  // Auth Subcommand Tests
  // ============================================================================

  /** Nested test class for auth subcommand operations. */
  @Nested
  class AuthSubcommandTests {

    /** Tests that auth subcommand exists and provides help. */
    @Test
    void testAuthHelpAvailable() {
      CliExecutionResult result = cliTestHelper.execute("auth", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).containsIgnoringCase("auth");
    }

    /** Tests that auth subcommand without arguments shows usage. */
    @Test
    void testAuthWithoutArgumentsShowsUsage() {
      CliExecutionResult result = cliTestHelper.execute("auth");

      assertThat(result.exitCode()).isNotEqualTo(0);
      String output = result.stdout() + result.stderr();
      assertThat(output)
          .satisfiesAnyOf(
              s -> assertThat(s).containsIgnoringCase("usage"),
              s -> assertThat(s).containsIgnoringCase("command"));
    }

    /** Tests that auth accepts the standard options. */
    @Test
    void testAuthWithOptions() {
      CliExecutionResult result =
          cliTestHelper.execute("--auth-url=http://localhost:8080", "auth", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
    }
  }

  // ============================================================================
  // Configure Subcommand Tests
  // ============================================================================

  /** Nested test class for configure subcommand operations. */
  @Nested
  class ConfigureSubcommandTests {

    /** Tests that configure subcommand exists and provides help. */
    @Test
    void testConfigureHelpAvailable() {
      CliExecutionResult result = cliTestHelper.execute("configure", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).containsIgnoringCase("configure");
    }

    /** Tests that configure subcommand without arguments shows usage. */
    @Test
    void testConfigureWithoutArgumentsShowsUsage() {
      CliExecutionResult result = cliTestHelper.execute("configure");

      assertThat(result.exitCode()).isNotEqualTo(0);
      String output = result.stdout() + result.stderr();
      assertThat(output)
          .satisfiesAnyOf(
              s -> assertThat(s).containsIgnoringCase("usage"),
              s -> assertThat(s).containsIgnoringCase("command"));
    }

    /** Tests that configure accepts verbose flag. */
    @Test
    void testConfigureWithVerbose() {
      CliExecutionResult result = cliTestHelper.execute("--verbose", "configure", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
    }
  }

  // ============================================================================
  // Crypto Subcommand Tests
  // ============================================================================

  /** Nested test class for crypto subcommand operations. */
  @Nested
  class CryptoSubcommandTests {

    /** Tests that crypto subcommand exists and provides help. */
    @Test
    void testCryptoHelpAvailable() {
      CliExecutionResult result = cliTestHelper.execute("crypto", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).containsIgnoringCase("crypto");
    }

    /** Tests that crypto subcommand without arguments shows usage. */
    @Test
    void testCryptoWithoutArgumentsShowsUsage() {
      CliExecutionResult result = cliTestHelper.execute("crypto");

      assertThat(result.exitCode()).isNotEqualTo(0);
      String output = result.stdout() + result.stderr();
      assertThat(output)
          .satisfiesAnyOf(
              s -> assertThat(s).containsIgnoringCase("usage"),
              s -> assertThat(s).containsIgnoringCase("command"));
    }

    /** Tests that crypto accepts the crypto API URL option. */
    @Test
    void testCryptoWithCryptoUrlOption() {
      CliExecutionResult result =
          cliTestHelper.execute("--crypto-url=http://localhost:9090", "crypto", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
    }
  }

  // ============================================================================
  // Database Subcommand Tests
  // ============================================================================

  /** Nested test class for database subcommand operations. */
  @Nested
  class DatabaseSubcommandTests {

    /** Tests that database subcommand exists and provides help. */
    @Test
    void testDatabaseHelpAvailable() {
      CliExecutionResult result = cliTestHelper.execute("database", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
      assertThat(result.stdout()).containsIgnoringCase("database");
    }

    /**
     * Tests that database subcommand without arguments shows usage or succeeds with defaults.
     *
     * <p>Note: Some subcommands like 'database' may have default actions when called without
     * arguments, unlike others that strictly require subcommands.
     */
    @Test
    void testDatabaseWithoutArgumentsShowsUsageOrDefaults() {
      CliExecutionResult result = cliTestHelper.execute("database");

      // Database command either shows usage (fails) or has a default action
      // (succeeds)
      // We just verify it executes and produces output
      String output = result.stdout() + result.stderr();
      assertThat(output).isNotEmpty();
    }

    /** Tests that database accepts verbose flag. */
    @Test
    void testDatabaseWithVerbose() {
      CliExecutionResult result = cliTestHelper.execute("--verbose", "database", "--help");

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.exitCode()).isEqualTo(0);
    }
  }

  // ============================================================================
  // Openapi Subcommand Tests
  // ============================================================================

  /** Tests for the openapi subcommand (bonus coverage). */
  @Test
  void testOpenApiHelpAvailable() {
    CliExecutionResult result = cliTestHelper.execute("openapi", "--help");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).containsIgnoringCase("openapi");
  }

  // ============================================================================
  // Cross-subcommand Integration Tests
  // ============================================================================

  /**
   * Tests that all subcommands accept the same global options.
   *
   * <p>This ensures consistency across the CLI interface.
   */
  @Test
  void testAllSubcommandsAcceptVerboseFlag() {
    String[] subcommands = {"admin", "auth", "configure", "crypto", "database"};

    for (String subcommand : subcommands) {
      CliExecutionResult result = cliTestHelper.execute("--verbose", subcommand, "--help");
      assertThat(result.isSuccess())
          .as("Subcommand '%s' should accept --verbose flag", subcommand)
          .isTrue();
    }
  }

  /** Tests that all subcommands accept timeout option. */
  @Test
  void testAllSubcommandsAcceptTimeoutOption() {
    String[] subcommands = {"admin", "auth", "configure", "crypto", "database"};

    for (String subcommand : subcommands) {
      CliExecutionResult result = cliTestHelper.execute("--timeout=5000", subcommand, "--help");
      assertThat(result.isSuccess())
          .as("Subcommand '%s' should accept --timeout option", subcommand)
          .isTrue();
    }
  }

  /** Tests that help flag works consistently across all subcommands. */
  @Test
  void testAllSubcommandsProvideHelp() {
    String[] subcommands = {"admin", "auth", "configure", "crypto", "database", "openapi"};

    for (String subcommand : subcommands) {
      CliExecutionResult result = cliTestHelper.execute(subcommand, "--help");
      assertThat(result.isSuccess())
          .as("Subcommand '%s' should provide help via --help flag", subcommand)
          .isTrue();
      assertThat(result.stdout())
          .as("Subcommand '%s' help should mention the command name", subcommand)
          .containsIgnoringCase(subcommand);
    }
  }
}
