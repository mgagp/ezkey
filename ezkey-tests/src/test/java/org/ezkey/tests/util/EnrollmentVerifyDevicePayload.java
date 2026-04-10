/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for license information.
 *
 * Test helper: canonical UTF-8 string for enrollment verify device signature. Must stay aligned with
 * org.ezkey.enrollment.service.EnrollmentSignaturePayload#buildVerifyDevicePayload (ezkey-core).
 */
package org.ezkey.tests.util;

/** Builds the device-signed payload for POST /api/v1/enrollments/verify. */
public final class EnrollmentVerifyDevicePayload {

  private static final String SEP = "|";

  private EnrollmentVerifyDevicePayload() {}

  /**
   * Canonical verify payload: {@code
   * enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey}.
   */
  public static String build(
      String enrollmentProofToken,
      int enrollmentId,
      int challengeResponse,
      String devicePublicKey) {
    String pt = enrollmentProofToken != null ? enrollmentProofToken : "";
    String pk = devicePublicKey != null ? devicePublicKey : "";
    return pt + SEP + enrollmentId + SEP + challengeResponse + SEP + pk;
  }
}
