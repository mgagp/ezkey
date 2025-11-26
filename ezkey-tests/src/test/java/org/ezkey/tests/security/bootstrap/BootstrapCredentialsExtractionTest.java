/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: BootstrapCredentialsExtractionTest
 * Description: Test utility for extracting bootstrap credentials from Docker logs
 */

package org.ezkey.tests.security.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Paths;
import org.ezkey.tests.util.BootstrapCredentialsExtractor;
import org.ezkey.tests.util.BootstrapCredentialsExtractor.BootstrapCredentials;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Test utility for extracting bootstrap credentials from Docker container logs.
 *
 * <p>This test can be run manually after Docker stack startup to extract and save bootstrap
 * credentials for use in other tests. It reads the Admin API container logs and extracts:
 *
 * <ul>
 *   <li>Enrollment ID
 *   <li>Enrollment Proof Token
 *   <li>Enrollment Challenge Code
 *   <li>Recovery Codes
 * </ul>
 *
 * <p><b>Usage:</b>
 *
 * <pre>
 * # After Docker stack startup
 * mvn test -pl ezkey-tests -Dtest=BootstrapCredentialsExtractionTest
 * </pre>
 *
 * <p>Credentials are saved to `.ezkey-test/bootstrap-credentials.json` for reuse in other tests.
 *
 * @since 2025
 */
@DisplayName("Bootstrap Credentials Extraction Test")
public class BootstrapCredentialsExtractionTest {

  @Test
  @DisplayName("Extract bootstrap credentials from Docker logs")
  public void testExtractBootstrapCredentials() {
    // Check if credentials file already exists (bootstrap already done)
    String credentialsFilePath = ".ezkey-test/bootstrap-credentials.json";
    boolean credentialsFileExists = Files.exists(Paths.get(credentialsFilePath));

    // If credentials already exist, skip this test (bootstrap already completed)
    Assumptions.assumeTrue(
        !credentialsFileExists,
        "Bootstrap credentials file already exists ("
            + credentialsFilePath
            + "). "
            + "Bootstrap has already been completed. Skipping extraction test.");

    // If we reach here, credentials file doesn't exist, so extract from logs
    BootstrapCredentialsExtractor extractor = new BootstrapCredentialsExtractor();

    BootstrapCredentials credentials = extractor.extractCredentials();

    // Validate extracted credentials
    assertThat(credentials.enrollmentId()).isNotNull().isPositive();
    assertThat(credentials.enrollmentProofToken()).isNotNull().isNotEmpty();
    assertThat(credentials.enrollmentChallengeCode()).isNotNull();
    assertThat(credentials.enrollmentChallengeCode()).isBetween(100000, 999999); // 6 digits
    assertThat(credentials.recoveryCodes()).isNotNull();
    // Recovery codes may be empty if enrollment already bound

    System.out.println("\n✅ Bootstrap credentials extracted successfully:");
    System.out.println("   Enrollment ID: " + credentials.enrollmentId());
    System.out.println("   Enrollment Proof Token: " + credentials.enrollmentProofToken());
    System.out.println("   Enrollment Challenge Code: " + credentials.enrollmentChallengeCode());
    System.out.println("   Recovery Codes: " + credentials.recoveryCodes().size() + " codes");
    System.out.println("\n📁 Credentials saved to: .ezkey-test/bootstrap-credentials.json");
  }

  @Test
  @DisplayName("Load bootstrap credentials from file if available")
  public void testLoadCredentialsFromFile() {
    BootstrapCredentialsExtractor extractor = new BootstrapCredentialsExtractor();

    BootstrapCredentials credentials = extractor.loadOrExtractCredentials();

    // Validate credentials
    assertThat(credentials.enrollmentId()).isNotNull().isPositive();
    assertThat(credentials.enrollmentProofToken()).isNotNull().isNotEmpty();
    assertThat(credentials.enrollmentChallengeCode()).isNotNull();
  }
}
