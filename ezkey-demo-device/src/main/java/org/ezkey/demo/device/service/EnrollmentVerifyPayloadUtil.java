package org.ezkey.demo.device.service;

/**
 * Canonical string the device signs on enrollment verify. Must stay aligned with {@code
 * org.ezkey.enrollment.service.EnrollmentSignaturePayload#buildVerifyDevicePayload} and {@code
 * docs/ENROLLMENT_SIGNATURE_PAYLOAD.md} (demo-device intentionally does not depend on ezkey-core).
 */
public final class EnrollmentVerifyPayloadUtil {

  private static final String SEP = "|";

  private EnrollmentVerifyPayloadUtil() {}

  /**
   * Format: {@code enrollmentProofToken|enrollmentId|challengeResponse|devicePublicKey}.
   *
   * @param enrollmentProofToken proof token from bind response
   * @param enrollmentId enrollment id
   * @param challengeResponse numeric challenge from operator
   * @param devicePublicKey device SPKI Base64
   * @return UTF-8 string to sign with the device private key
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
}
