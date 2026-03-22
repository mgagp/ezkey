/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.ezkey.authattempt.domain.AuthenticationResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link AuthAttemptSignaturePayload} canonical payload building (NFC + format). */
@DisplayName("AuthAttemptSignaturePayload")
class AuthAttemptSignaturePayloadTest {

  @Nested
  @DisplayName("buildPendingPayload")
  class BuildPendingPayload {

    @Test
    void formats_with_all_fields() {
      String payload =
          AuthAttemptSignaturePayload.buildPendingPayload("token123", true, "Title", "Message");
      assertThat(payload).isEqualTo("token123|true|Title|Message");
    }

    @Test
    void null_title_and_message_become_empty() {
      String payload = AuthAttemptSignaturePayload.buildPendingPayload("t", false, null, null);
      assertThat(payload).isEqualTo("t|false||");
    }

    @Test
    void preserves_accented_text_in_payload() {
      String payload =
          AuthAttemptSignaturePayload.buildPendingPayload("t", true, "Virement", "Payé");
      assertThat(payload).isEqualTo("t|true|Virement|Payé");
    }
  }

  @Nested
  @DisplayName("buildRespondPayload")
  class BuildRespondPayload {

    @Test
    void accepted_true() {
      String payload = AuthAttemptSignaturePayload.buildRespondPayload("token", true);
      assertThat(payload).isEqualTo("token|true");
    }

    @Test
    void accepted_false() {
      String payload = AuthAttemptSignaturePayload.buildRespondPayload("token", false);
      assertThat(payload).isEqualTo("token|false");
    }
  }

  @Nested
  @DisplayName("buildRespondResultPayload")
  class BuildRespondResultPayload {

    @Test
    void approved_with_all_fields() {
      String payload =
          AuthAttemptSignaturePayload.buildRespondResultPayload(
              "pt", 99, AuthenticationResult.APPROVED, "Done");
      assertThat(payload).isEqualTo("pt|99|APPROVED|Done");
    }

    @Test
    void failed_empty_proof_token_and_null_id_use_empty_segments() {
      String payload =
          AuthAttemptSignaturePayload.buildRespondResultPayload(
              null, null, AuthenticationResult.FAILED, "Auth attempt record not found");
      assertThat(payload).isEqualTo("||FAILED|Auth attempt record not found");
    }

    @Test
    void nfc_normalizes_message() {
      String payload =
          AuthAttemptSignaturePayload.buildRespondResultPayload(
              "", 1, AuthenticationResult.DENIED, "café");
      assertThat(payload).isEqualTo("|1|DENIED|café");
    }
  }

  @Nested
  @DisplayName("nfcOrEmpty")
  class NfcOrEmpty {

    @Test
    void null_returns_empty() {
      assertThat(AuthAttemptSignaturePayload.nfcOrEmpty(null)).isEmpty();
    }

    @Test
    void non_null_returns_nfc_normalized() {
      String s = "café";
      assertThat(AuthAttemptSignaturePayload.nfcOrEmpty(s)).isEqualTo("café");
    }
  }
}
