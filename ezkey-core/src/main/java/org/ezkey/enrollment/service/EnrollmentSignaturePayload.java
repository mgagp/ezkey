/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for license information.
 *
 * Canonical payload builder for enrollment bind (integration-signed), verify request (device-signed),
 * and verify response (integration-signed). See docs/ENROLLMENT_SIGNATURE_PAYLOAD.md.
 */

package org.ezkey.enrollment.service;

import java.text.Normalizer;
import org.ezkey.authattempt.service.AuthAttemptSignaturePayload;

/**
 * Builds canonical signature payloads for enrollment bind and verify flows.
 *
 * <p>Uses pipe-separated UTF-8 strings. User-facing text uses NFC normalization per UAX #15.
 */
public final class EnrollmentSignaturePayload {

  private static final String SEP = "|";

  /**
   * Outcome literal included in the verify-result payload when enrollment completes successfully.
   */
  public enum EnrollmentVerificationOutcome {
    VERIFIED
  }

  private static final String VERIFY_SUCCESS_MESSAGE = "Enrollment verified successfully";

  private EnrollmentSignaturePayload() {}

  /**
   * Default user-facing message for a successful verify response (NFC-normalized in {@link
   * #buildVerifyResultPayload}).
   */
  public static String defaultVerifySuccessMessage() {
    return VERIFY_SUCCESS_MESSAGE;
  }

  /**
   * Builds the payload signed by the integration in the bind response.
   *
   * <p>Format: {@code
   * enrollmentProofToken|enrollmentId|integrationPublicKey|algorithm|integrationName|integrationDescription|enrollmentName|tenantId|tenantName|tenantDescription}
   */
  public static String buildBindPayload(
      String enrollmentProofToken,
      Integer enrollmentId,
      String integrationPublicKeyNormalized,
      String integrationKeyAlgorithm,
      String integrationName,
      String integrationDescription,
      String enrollmentName,
      Integer tenantId,
      String tenantName,
      String tenantDescription) {
    String pt = enrollmentProofToken != null ? enrollmentProofToken : "";
    String idStr = enrollmentId != null ? String.valueOf(enrollmentId) : "";
    String integPk = integrationPublicKeyNormalized != null ? integrationPublicKeyNormalized : "";
    String algo = integrationKeyAlgorithm != null ? integrationKeyAlgorithm : "";
    String tenantIdStr = tenantId != null ? String.valueOf(tenantId) : "";
    return pt
        + SEP
        + idStr
        + SEP
        + integPk
        + SEP
        + algo
        + SEP
        + nfcOrEmpty(integrationName)
        + SEP
        + nfcOrEmpty(integrationDescription)
        + SEP
        + nfcOrEmpty(enrollmentName)
        + SEP
        + tenantIdStr
        + SEP
        + nfcOrEmpty(tenantName)
        + SEP
        + nfcOrEmpty(tenantDescription);
  }

  /**
   * Builds the payload the device signs on verify.
   *
   * <p>Format: {@code enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey}
   */
  public static String buildVerifyDevicePayload(
      String enrollmentProofToken,
      Integer enrollmentId,
      Integer challengeResponse,
      String devicePublicKey) {
    String pt = enrollmentProofToken != null ? enrollmentProofToken : "";
    String idStr = enrollmentId != null ? String.valueOf(enrollmentId) : "";
    String ch = challengeResponse != null ? String.valueOf(challengeResponse) : "";
    String pk = devicePublicKey != null ? devicePublicKey : "";
    return pt + SEP + idStr + SEP + ch + SEP + pk;
  }

  /**
   * Builds the payload the integration signs on successful verify HTTP response.
   *
   * <p>Format: {@code enrollmentProofToken|enrollmentId|outcome|message}
   */
  public static String buildVerifyResultPayload(
      String enrollmentProofToken,
      Integer enrollmentId,
      EnrollmentVerificationOutcome outcome,
      String message) {
    String pt = enrollmentProofToken != null ? enrollmentProofToken : "";
    String idStr = enrollmentId != null ? String.valueOf(enrollmentId) : "";
    String oc = outcome != null ? outcome.name() : "";
    String msg = AuthAttemptSignaturePayload.nfcOrEmpty(message);
    return pt + SEP + idStr + SEP + oc + SEP + msg;
  }

  private static String nfcOrEmpty(String s) {
    if (s == null) {
      return "";
    }
    return Normalizer.normalize(s, Normalizer.Form.NFC);
  }
}
