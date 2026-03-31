/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.security.SensitiveDataHasher;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link RecoveryAuditDetails}. */
class RecoveryAuditDetailsTest {

  @Test
  void recoveryTokenFingerprint_isFirst16HexCharsOfSha256() {
    String token = "ezkey_recovery_testtoken123456789";
    String full = SensitiveDataHasher.sha256Hex(token);
    assertThat(full).isNotNull().hasSize(64);
    assertThat(RecoveryAuditDetails.recoveryTokenFingerprint(token))
        .isEqualTo(full.substring(0, 16));
  }

  @Test
  void recoveryCodeValidatedSuccess_producesValidJsonWithSchemaVersion() {
    String json =
        RecoveryAuditDetails.recoveryCodeValidatedSuccess(
            "alice", 1, 2, 9, 100, "aabbccddeeff0011");
    assertThat(json)
        .contains("\"schema_version\":1")
        .contains("\"flow\":\"admin_recovery\"")
        .contains("\"step\":\"recovery_code_validated\"")
        .contains("\"recovery_token_fingerprint\":\"aabbccddeeff0011\"")
        .contains("\"mfa_enrollment_id\":100");
  }

  @Test
  void recoveryRejectionReasonCode_mapsKnownMessages() {
    assertThat(RecoveryAuditDetails.recoveryRejectionReasonCode("Invalid credentials"))
        .isEqualTo("unknown_user");
    assertThat(RecoveryAuditDetails.recoveryRejectionReasonCode("Invalid recovery code"))
        .isEqualTo("invalid_code");
  }
}
