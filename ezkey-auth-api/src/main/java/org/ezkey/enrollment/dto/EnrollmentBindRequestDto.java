/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentBindRequestDto
 * Description: Request DTO for enrollment binding initiation with proof token in auth API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Request DTO for enrollment binding initiation with proof token in auth API.
 *
 * <p>This DTO represents the request data needed to initiate the enrollment binding process for
 * mobile devices. The enrollment proof token is the invitation secret. The enrollment id is not
 * sent; the server resolves the row from the token hash and returns the id (ADR-0019).
 *
 * <p><b>Security:</b> The proof token is the bearer secret for a {@code CREATED} enrollment. A
 * sequential enrollment id is not a second secret and is not part of this request.
 *
 * <p><b>Usage Context:</b> Used by mobile devices to start the enrollment binding process with the
 * auth-api. The enrollment proof token must be obtained from the enrollment creation process
 * (admin-api) and provided in this request.
 *
 * <p><b>Enrollment Flow:</b> This request initiates the binding process which will return
 * enrollment details, integration information, and cryptographic data needed for the mobile device
 * to complete enrollment verification.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentProofToken:</b> The enrollment proof token for authentication
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentBindRequest
 * @see org.ezkey.enrollment.dto.EnrollmentBindResponseDto
 */
@Schema(description = "Request DTO for enrollment binding initiation with proof token")
public record EnrollmentBindRequestDto(
    /**
     * The enrollment proof token for authentication.
     *
     * <p>Must match the proof token generated during enrollment creation. This token prevents
     * enumeration attacks by ensuring only parties with valid proof tokens can access enrollment
     * data.
     */
    @Schema(
            description = "Enrollment proof token for authentication",
            example = "abc123-def456-ghi789",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentProofToken) {

  /**
   * Returns a string representation with the proof token masked.
   *
   * @return string representation safe for logs
   */
  @Override
  public String toString() {
    return "EnrollmentBindRequestDto{enrollmentProofToken="
        + (enrollmentProofToken != null ? "[PROTECTED]" : "null")
        + '}';
  }
}
