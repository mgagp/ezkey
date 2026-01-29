/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CliCommandsTest
 * Description: Tests for basic CLI commands including help, config, and integration operations.
 */

// This file is UTF-8 without BOM.

package org.ezkey.tests.security.cli;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.CliExecutionResult;
import org.ezkey.tests.util.CliTestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * CLI tests for basic commands including help, version, and subcommand
 * discovery.
 *
 * <p>
 * This test suite validates:
 *
 * <ul>
 * <li>Help and version commands work correctly
 * <li>Available subcommands can be discovered (admin, auth, configure, etc.)
 * <li>Invalid commands produce appropriate error messages
 * <li>Subcommands provide their own help
 * </ul>
 *
 * @since 2025
 */
@Tag(TestTags.CLI)
@Tag(TestTags.SLOW)
public class CliCommandsTest extends AbstractSecurityTest {

    private CliTestHelper cliTestHelper;

    @BeforeEach
    void setUpCli() {
        cliTestHelper = new CliTestHelper(dockerStackConfig);
        cliTestHelper.configureDefault();
    }

    // ============================================================================
    // Category A: Help and Information Commands
    // ============================================================================

    /**
     * Tests that the help command executes successfully and outputs help text.
     */
    @Test
    void testHelpCommand() {
        CliExecutionResult result = cliTestHelper.execute("--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).containsIgnoringCase("usage");
    }

    /**
     * Tests that the version command executes successfully and outputs version
     * information.
     */
    @Test
    void testVersionCommand() {
        CliExecutionResult result = cliTestHelper.execute("--version");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).containsIgnoringCase("ezkey");
    }

    /**
     * Tests that invalid commands fail with appropriate error message.
     */
    @Test
    void testInvalidCommand() {
        CliExecutionResult result = cliTestHelper.execute("invalid-command-xyz");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isNotEqualTo(0);
        String stderr = result.stderr();
        assertThat(stderr)
                .satisfiesAnyOf(
                        s -> assertThat(s).containsIgnoringCase("unknown"),
                        s -> assertThat(s).containsIgnoringCase("invalid"),
                        s -> assertThat(s).containsIgnoringCase("error"));
    }

    // ============================================================================
    // Category B: Subcommand Discovery
    // ============================================================================

    /**
     * Tests that the admin subcommand is available and provides help.
     */
    @Test
    void testAdminCommandAvailable() {
        CliExecutionResult result = cliTestHelper.execute("admin", "--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).containsIgnoringCase("admin");
    }

    /**
     * Tests that the auth subcommand is available and provides help.
     */
    @Test
    void testAuthCommandAvailable() {
        CliExecutionResult result = cliTestHelper.execute("auth", "--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).containsIgnoringCase("auth");
    }

    /**
     * Tests that the configure subcommand is available and provides help.
     */
    @Test
    void testConfigureCommandAvailable() {
        CliExecutionResult result = cliTestHelper.execute("configure", "--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).containsIgnoringCase("configure");
    }

    /**
     * Tests that the crypto subcommand is available and provides help.
     */
    @Test
    void testCryptoCommandAvailable() {
        CliExecutionResult result = cliTestHelper.execute("crypto", "--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).containsIgnoringCase("crypto");
    }

    /**
     * Tests that the database subcommand is available and provides help.
     */
    @Test
    void testDatabaseCommandAvailable() {
        CliExecutionResult result = cliTestHelper.execute("database", "--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
        assertThat(result.stdout()).containsIgnoringCase("database");
    }

    // ============================================================================
    // Category C: Configuration and Verbose Mode
    // ============================================================================

    /**
     * Tests that the CLI accepts verbose flag and doesn't fail with it.
     */
    @Test
    void testVerboseFlag() {
        CliExecutionResult result = cliTestHelper.execute("--verbose", "--version");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
    }

    /**
     * Tests that command line options can override configuration.
     */
    @Test
    void testAdminUrlOption() {
        CliExecutionResult result = cliTestHelper.execute("--admin-url=http://test:9080", "admin", "--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
    }

    /**
     * Tests that timeout option is accepted by the CLI.
     */
    @Test
    void testTimeoutOption() {
        CliExecutionResult result = cliTestHelper.execute("--timeout=5000", "admin", "--help");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.exitCode()).isEqualTo(0);
    }

    /**
     * Tests that the CLI fails gracefully with invalid options.
     */
    @Test
    void testInvalidOption() {
        CliExecutionResult result = cliTestHelper.execute("--invalid-option=xyz", "--version");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.exitCode()).isNotEqualTo(0);
    }
}
