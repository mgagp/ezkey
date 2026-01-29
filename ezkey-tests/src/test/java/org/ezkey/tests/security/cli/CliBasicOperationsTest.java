/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: CliBasicOperationsTest
 * Description: Validates basic CLI command execution against the Docker stack.
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
 * Basic CLI tests to validate the CLI container and execution pipeline.
 *
 * @since 2025
 */
@Tag(TestTags.CLI)
@Tag(TestTags.SLOW)
public class CliBasicOperationsTest extends AbstractSecurityTest {

  private CliTestHelper cliTestHelper;

  @BeforeEach
  void setUpCli() {
    cliTestHelper = new CliTestHelper(dockerStackConfig);
    cliTestHelper.configureDefault();
  }

  @Test
  void testCliVersion() {
    CliExecutionResult result = cliTestHelper.execute("--version");

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.exitCode()).isEqualTo(0);
    assertThat(result.stdout()).containsIgnoringCase("ezkey");
  }
}
