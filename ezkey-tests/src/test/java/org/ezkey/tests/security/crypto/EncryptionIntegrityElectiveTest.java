/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test Class: EncryptionIntegrityElectiveTest
 * Description: Elective on-demand spot check for encryption-at-rest integrity of enrollment proof
 *     tokens. Ensures no enrollment has a plaintext proof token in the database.
 */

package org.ezkey.tests.security.crypto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.ezkey.tests.security.AbstractSecurityTest;
import org.ezkey.tests.tags.TestTags;
import org.ezkey.tests.util.DatabaseHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Elective on-demand spot check for encryption-at-rest integrity of enrollment data.
 *
 * <p>These tests are <b>never run automatically</b>. They are invoked explicitly (e.g. after a full
 * functional test run) to enforce the <b>norm</b>: every enrollment proof token stored in the
 * database must be encrypted (format {@code ENC:keyId:base64...}). If any row has a plaintext proof
 * token, the test fails — regardless of whether the cause is test code (raw SQL INSERT),
 * application code (listener not encrypting), or encryption service unavailable at persist time.
 *
 * <p><b>What is verified:</b>
 *
 * <ul>
 *   <li>No enrollment has {@code enrollment_proof_token} stored as plaintext. All must start with
 *       {@code ENC:}. Any plaintext token is an encryption-at-rest violation.
 * </ul>
 *
 * <p><b>Invocation:</b>
 *
 * <pre>{@code
 * mvn test -pl ezkey-tests -P elective-tests
 * }</pre>
 *
 * <p>If this test fails, run the diagnostic query documented in {@link
 * org.ezkey.tests.util.DatabaseHelper#getEnrollmentIdsWithPlaintextProofToken} to inspect
 * enrollment_id, integration_id, enrollment_name, has_hash, token_preview, created_at and determine
 * the source of the violation (test vs application).
 *
 * @since 2025
 */
@Tag(TestTags.ELECTIVE)
@Tag(TestTags.ENCRYPTION)
@Tag(TestTags.DATABASE)
@DisplayName("Encryption Integrity Elective Spot Check")
public class EncryptionIntegrityElectiveTest extends AbstractSecurityTest {

  private static final Logger logger =
      LoggerFactory.getLogger(EncryptionIntegrityElectiveTest.class);

  private DatabaseHelper databaseHelper;

  @BeforeEach
  @Override
  public void setUp() {
    super.setUp();
    databaseHelper = new DatabaseHelper();
  }

  @Test
  @DisplayName("No enrollment shall have plaintext proof token (all must be ENC:...)")
  void noEnrollmentWithPlaintextProofToken() {
    logger.info("=== Elective: Enrollment Proof Token Encryption Integrity ===");

    List<Integer> plaintextIds = databaseHelper.getEnrollmentIdsWithPlaintextProofToken();

    logger.info(
        "Enrollments with plaintext proof token: {} (count={})", plaintextIds, plaintextIds.size());

    assertThat(plaintextIds)
        .as(
            "Enrollments with unencrypted proof token (encryption-at-rest norm: all must be"
                + " ENC:...). Violating IDs: %s. Run the diagnostic query in DatabaseHelper to"
                + " inspect.",
            plaintextIds)
        .isEmpty();

    logger.info("✅ PASS: All enrollments have encrypted proof tokens (ENC:... format)");
  }
}
