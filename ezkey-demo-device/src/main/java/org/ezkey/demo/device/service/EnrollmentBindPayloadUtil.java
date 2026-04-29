package org.ezkey.demo.device.service;

import java.text.Normalizer;

/**
 * Canonical bind payload builder for integration signature verification.
 *
 * <p>Must stay aligned with {@code org.ezkey.enrollment.service.EnrollmentSignaturePayload} and
 * {@code docs/ENROLLMENT_SIGNATURE_PAYLOAD.md}.
 */
public final class EnrollmentBindPayloadUtil {

  private static final String SEP = "|";

  private EnrollmentBindPayloadUtil() {}

  /**
   * Format: {@code
   * enrollmentProofToken|enrollmentId|integrationPublicKey|integrationKeyAlgorithm|integrationName|integrationDescription|enrollmentName|tenantId|tenantName|tenantDescription|authAttemptChallengeRequiredByPolicy}.
   */
  public static String buildBindPayload(
      String enrollmentProofToken,
      Integer enrollmentId,
      String integrationPublicKey,
      String integrationKeyAlgorithm,
      String integrationName,
      String integrationDescription,
      String enrollmentName,
      Integer tenantId,
      String tenantName,
      String tenantDescription,
      Boolean authAttemptChallengeRequiredByPolicy) {
    String pt = enrollmentProofToken != null ? enrollmentProofToken : "";
    String idStr = enrollmentId != null ? String.valueOf(enrollmentId) : "";
    String integPk = integrationPublicKey != null ? integrationPublicKey : "";
    String algo = integrationKeyAlgorithm != null ? integrationKeyAlgorithm : "";
    String tenantIdStr = tenantId != null ? String.valueOf(tenantId) : "";
    String challengeByPolicy =
        Boolean.TRUE.equals(authAttemptChallengeRequiredByPolicy) ? "true" : "false";
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
        + nfcOrEmpty(tenantDescription)
        + SEP
        + challengeByPolicy;
  }

  private static String nfcOrEmpty(String value) {
    if (value == null) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFC);
  }
}
