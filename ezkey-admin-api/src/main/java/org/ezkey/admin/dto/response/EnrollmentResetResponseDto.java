/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentResetResponseDto
 * Description: Response DTO for enrollment reset operation.
 */

package org.ezkey.admin.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for enrollment reset operation.
 *
 * <p>Contains the new enrollment credentials that must be used to bind a replacement device after
 * the enrollment has been reset.
 *
 * <p><b>Security:</b> The old device is now unbound and cannot be used for authentication. The
 * administrator must bind a new device using these credentials.
 *
 * <p><b>Usage Pattern:</b> Use factory methods {@link #success} for successful resets and {@link
 * #error} for error responses.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param success Indicates if the reset was successful
 * @param enrollmentId The enrollment ID (same as before, but credentials are new)
 * @param enrollmentProofToken New enrollment proof token for binding (null on error)
 * @param enrollmentChallenge New enrollment challenge code for verification (null on error)
 * @param integrationId Integration ID for reference (null on error)
 * @param message Response message
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(description = "Response for enrollment reset operation")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EnrollmentResetResponseDto(
    @Schema(description = "Indicates if the reset was successful", example = "true")
        Boolean success,
    @Schema(description = "The enrollment ID", example = "123") Integer enrollmentId,
    @Schema(
            description = "New enrollment proof token for binding",
            example = "ezkey_proof_a1b2c3d4e5f6g7h8i9j0")
        String enrollmentProofToken,
    @Schema(description = "New enrollment challenge code for verification", example = "123456")
        Integer enrollmentChallenge,
    @Schema(description = "Integration ID for reference", example = "456") Integer integrationId,
    @Schema(
            description = "Response message",
            example =
                "Enrollment reset successfully. Old device unbound. Use these credentials to bind"
                    + " new device.")
        String message) {

  /**
   * Factory method for successful enrollment reset.
   *
   * <p>Creates a success response with new enrollment credentials. The old device is unbound and
   * these credentials must be used to bind a new device.
   *
   * @param enrollmentId the enrollment ID
   * @param enrollmentProofToken the new proof token
   * @param enrollmentChallenge the new challenge code
   * @param integrationId the integration ID
   * @return a success response DTO
   */
  public static EnrollmentResetResponseDto success(
      Integer enrollmentId,
      String enrollmentProofToken,
      Integer enrollmentChallenge,
      Integer integrationId) {
    return new EnrollmentResetResponseDto(
        true,
        enrollmentId,
        enrollmentProofToken,
        enrollmentChallenge,
        integrationId,
        "Enrollment reset successfully. Old device unbound. Use these credentials to bind new"
            + " device.");
  }

  /**
   * Factory method for error enrollment reset.
   *
   * <p>Creates an error response with a descriptive message. No enrollment credentials are included
   * in error responses.
   *
   * @param message the error message
   * @return an error response DTO
   */
  public static EnrollmentResetResponseDto error(String message) {
    return new EnrollmentResetResponseDto(false, null, null, null, null, message);
  }

  /**
   * Returns a string representation of the response.
   *
   * <p>Note: Sensitive credentials (proof token and challenge) are masked for security.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "EnrollmentResetResponseDto{"
        + "success="
        + success
        + ", enrollmentId="
        + enrollmentId
        + ", enrollmentProofToken='"
        + (enrollmentProofToken != null ? "[PROTECTED]" : "null")
        + '\''
        + ", enrollmentChallenge="
        + (enrollmentChallenge != null ? "[PROTECTED]" : "null")
        + ", integrationId="
        + integrationId
        + ", message='"
        + message
        + '\''
        + '}';
  }
}
