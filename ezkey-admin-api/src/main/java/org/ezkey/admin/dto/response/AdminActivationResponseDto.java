/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminActivationResponseDto
 * Description: Response DTO for first-time administrator activation.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for first-time administrator activation.
 *
 * <p>Contains the first enrollment binding credentials returned when the pending administrator is
 * activated. No recovery codes are created at activation; the admin issues an initial set after the
 * first authenticated sign-in (#1477).
 *
 * @param success Indicates whether activation succeeded
 * @param message Response message
 * @param username Username of the activated administrator
 * @param enrollmentId Enrollment ID created during activation
 * @param enrollmentProofToken Enrollment proof token shown once for device binding
 * @param enrollmentChallenge Enrollment challenge code shown once for device binding
 * @param recoveryCodes Always null: no recovery codes exist at activation (#1477)
 * @param enrollmentCode The exact JSON of the enrollment QR code, for an authenticator that cannot
 *     scan (#1494); null without a proof token or without a configured Auth API address
 * @since 2025
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Response for first-time administrator activation")
public record AdminActivationResponseDto(
    @Schema(description = "Indicates whether activation succeeded", example = "true")
        Boolean success,
    @Schema(description = "Response message") String message,
    @Schema(description = "Activated administrator username", example = "pending.admin")
        String username,
    @Schema(description = "Enrollment ID created during activation", example = "123")
        Integer enrollmentId,
    @Schema(
            description = "Enrollment proof token shown once for binding",
            example = "ezkey_proof_a1b2c3d4e5f6g7h8i9j0")
        String enrollmentProofToken,
    @Schema(description = "Enrollment challenge code shown once for binding", example = "123456")
        Integer enrollmentChallenge,
    @Schema(
            description =
                "Always null: no recovery codes exist at activation. The administrator issues"
                    + " the initial set after the first sign-in (POST"
                    + " /api/v1/admins/{id}/recovery-codes/issue-initial) (#1477)",
            nullable = true)
        List<String> recoveryCodes,
    @Schema(
            description =
                "Enrollment code: the exact JSON the enrollment QR code holds"
                    + " ({enrollmentProofToken, authUrl}), for an authenticator that cannot scan."
                    + " Present only with the proof token and when ezkey.qr.auth-base-url is"
                    + " configured; never contains the challenge code (#1494)",
            nullable = true)
        String enrollmentCode) {

  public static AdminActivationResponseDto success(
      String username,
      Integer enrollmentId,
      String enrollmentProofToken,
      Integer enrollmentChallenge,
      List<String> recoveryCodes,
      String enrollmentCode) {
    return new AdminActivationResponseDto(
        true,
        "Activation successful. Bind the first device now. Recovery codes remain deferred.",
        username,
        enrollmentId,
        enrollmentProofToken,
        enrollmentChallenge,
        recoveryCodes,
        enrollmentCode);
  }

  public static AdminActivationResponseDto error(String message) {
    return new AdminActivationResponseDto(false, message, null, null, null, null, null, null);
  }

  /**
   * Same layout as the default record {@code toString()}, with secret values masked (#756).
   *
   * @return string representation without secret values
   */
  @Override
  public String toString() {
    return "AdminActivationResponseDto["
        + "success="
        + success
        + ", message="
        + message
        + ", username="
        + username
        + ", enrollmentId="
        + enrollmentId
        + ", enrollmentProofToken="
        + (enrollmentProofToken != null ? "[PROTECTED]" : "null")
        + ", enrollmentChallenge="
        + (enrollmentChallenge != null ? "[PROTECTED]" : "null")
        + ", recoveryCodes="
        + (recoveryCodes != null ? "[PROTECTED]" : "null")
        + ", enrollmentCode="
        + (enrollmentCode != null ? "[PROTECTED]" : "null")
        + ']';
  }
}
