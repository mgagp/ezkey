/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: CliExecutionResult
 * Description: Result wrapper for CLI command execution in tests.
 */

// This file is UTF-8 without BOM.

package org.ezkey.tests.util;

/**
 * Result wrapper for CLI command execution.
 *
 * <p>Captures exit code, stdout, and stderr for assertions and diagnostics.
 *
 * @param exitCode process exit code
 * @param stdout standard output
 * @param stderr standard error
 * @since 2025
 */
public record CliExecutionResult(int exitCode, String stdout, String stderr) {

  /**
   * Returns true when the exit code is zero.
   *
   * @return true when successful
   */
  public boolean isSuccess() {
    return exitCode == 0;
  }
}
