/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EzkeyClientAuthAttemptLogTest
 * Description: Create-auth-attempt INFO log must not include challenge values.
 */

package org.ezkey.sdk;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class EzkeyClientAuthAttemptLogTest {

  @Test
  void createAuthAttemptInfoLogOmitsChallengeValue() throws IOException {
    Path source = resolveEzkeyClientSource();
    String content = Files.readString(source, StandardCharsets.UTF_8);
    assertTrue(content.contains("Auth attempt created: authAttemptId={0}"));
    assertFalse(content.contains("challenge={1}"));
    assertFalse(content.contains("challenge={"));
  }

  private static Path resolveEzkeyClientSource() {
    Path fromModule =
        Path.of("src", "main", "java", "org", "ezkey", "sdk", "EzkeyClient.java")
            .toAbsolutePath()
            .normalize();
    if (Files.exists(fromModule)) {
      return fromModule;
    }
    Path fromRepoRoot =
        Path.of(
                "ezkey-sdk",
                "java",
                "src",
                "main",
                "java",
                "org",
                "ezkey",
                "sdk",
                "EzkeyClient.java")
            .toAbsolutePath()
            .normalize();
    assertTrue(Files.exists(fromRepoRoot), "EzkeyClient.java not found");
    return fromRepoRoot;
  }
}
