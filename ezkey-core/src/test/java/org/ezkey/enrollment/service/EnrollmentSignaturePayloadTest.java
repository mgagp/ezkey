/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for license information.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.text.Normalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EnrollmentSignaturePayload}. */
class EnrollmentSignaturePayloadTest {

  @Test
  @DisplayName("buildBindPayload uses NFC for user-facing segments and pipe separator")
  void buildBindPayload_NormalizesText() {
    String payload =
        EnrollmentSignaturePayload.buildBindPayload(
            "pt", 1, "integPk", "ed25519", "caf\u0301e", "desc", "name", 2, "tn", "td");
    assertTrue(payload.startsWith("pt|1|integPk|ed25519|"));
    assertTrue(payload.endsWith("|desc|name|2|tn|td"));
    assertTrue(payload.contains(Normalizer.normalize("caf\u0301e", Normalizer.Form.NFC)));
  }

  @Test
  @DisplayName("buildVerifyDevicePayload concatenates four segments")
  void buildVerifyDevicePayload_Format() {
    assertEquals(
        "tok|42|123456|spki",
        EnrollmentSignaturePayload.buildVerifyDevicePayload("tok", 42, 123456, "spki"));
  }

  @Test
  @DisplayName("buildVerifyResultPayload uses VERIFIED outcome and NFC message")
  void buildVerifyResultPayload_Format() {
    assertEquals(
        "tok|99|VERIFIED|ok",
        EnrollmentSignaturePayload.buildVerifyResultPayload(
            "tok", 99, EnrollmentSignaturePayload.EnrollmentVerificationOutcome.VERIFIED, "ok"));
  }

  @Test
  @DisplayName("buildInstanceInfoPayload uses INSTANCE_INFO purpose and NFC text")
  void buildInstanceInfoPayload_Format() {
    String payload =
        EnrollmentSignaturePayload.buildInstanceInfoPayload(
            "pt", 7, "https://auth.example", "caf\u0301e", "desc", "https://about.example");
    assertEquals(
        "pt|7|INSTANCE_INFO|https://auth.example|"
            + Normalizer.normalize("caf\u0301e", Normalizer.Form.NFC)
            + "|desc|https://about.example",
        payload);
  }

  @Test
  @DisplayName("buildInstanceInfoPayload maps null branding segments to empty strings")
  void buildInstanceInfoPayload_NullSegments() {
    assertEquals(
        "pt|1|INSTANCE_INFO||||",
        EnrollmentSignaturePayload.buildInstanceInfoPayload("pt", 1, null, null, null, null));
  }
}
