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
 * activated. Recovery codes are generated server-side at first activation but are not revealed in
 * this unauthenticated bootstrap response.
 *
 * @param success Indicates whether activation succeeded
 * @param message Response message
 * @param username Username of the activated administrator
 * @param enrollmentId Enrollment ID created during activation
 * @param enrollmentProofToken Enrollment proof token shown once for device binding
 * @param enrollmentChallenge Enrollment challenge code shown once for device binding
 * @param recoveryCodes Recovery codes are intentionally omitted from this bootstrap response
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
                "Recovery codes are intentionally omitted from this unauthenticated activation"
                    + " response and must be revealed later through an authenticated recovery-code"
                    + " management flow")
        List<String> recoveryCodes) {

  public static AdminActivationResponseDto success(
      String username,
      Integer enrollmentId,
      String enrollmentProofToken,
      Integer enrollmentChallenge,
      List<String> recoveryCodes) {
    return new AdminActivationResponseDto(
        true,
        "Activation successful. Bind the first device now. Recovery codes remain deferred.",
        username,
        enrollmentId,
        enrollmentProofToken,
        enrollmentChallenge,
        recoveryCodes);
  }

  public static AdminActivationResponseDto error(String message) {
    return new AdminActivationResponseDto(false, message, null, null, null, null, null);
  }
}
