/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentBindRequestDto
 * Description: Request DTO for enrollment binding initiation with proof token in auth API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for enrollment binding initiation with proof token in auth API.
 *
 * <p>This DTO represents the request data needed to initiate the enrollment binding process for
 * mobile devices. It requires both the enrollment ID and the enrollment proof token to prevent
 * enumeration attacks and ensure secure access to enrollment data.
 *
 * <p><b>Security Enhancement:</b> This DTO implements enumeration protection by requiring the
 * enrollment proof token in addition to the enrollment ID. This prevents attackers from
 * systematically testing enrollment IDs to discover valid enrollments and obtain sensitive
 * information.
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
 *   <li><b>enrollmentId:</b> The enrollment ID to bind to the mobile device
 *   <li><b>enrollmentProofToken:</b> The enrollment proof token for authentication
 *   <li><b>language:</b> Preferred language for internationalization
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentBindRequest
 * @see org.ezkey.enrollment.dto.EnrollmentBindResponseDto
 */
@Schema(description = "Request DTO for enrollment binding initiation with proof token")
public class EnrollmentBindRequestDto {

  /**
   * The enrollment ID to bind to the mobile device.
   *
   * <p>Must reference an existing enrollment created through the admin API. This ID is typically
   * obtained by the mobile device through QR code scanning or deep link navigation from the
   * integration website.
   */
  @Schema(
      description = "Enrollment ID to bind to the mobile device",
      example = "123",
      required = true)
  private Integer enrollmentId;

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
      required = true)
  private String enrollmentProofToken;

  /**
   * Preferred language for internationalization.
   *
   * <p>Language code (e.g., "en", "fr", "es") requested for localized fields such as integration
   * names and descriptions. Used to provide a localized user experience during enrollment binding
   * and device configuration.
   */
  @Schema(description = "Preferred language for i18n fields", example = "en")
  private String language;

  /**
   * Gets the enrollment ID.
   *
   * @return the enrollment ID
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the enrollment ID.
   *
   * @param enrollmentId the enrollment ID to set
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  /**
   * Gets the enrollment proof token.
   *
   * @return the enrollment proof token
   */
  public String getEnrollmentProofToken() {
    return enrollmentProofToken;
  }

  /**
   * Sets the enrollment proof token.
   *
   * @param enrollmentProofToken the enrollment proof token to set
   */
  public void setEnrollmentProofToken(String enrollmentProofToken) {
    this.enrollmentProofToken = enrollmentProofToken;
  }

  /**
   * Gets the preferred language.
   *
   * @return the language code
   */
  public String getLanguage() {
    return language;
  }

  /**
   * Sets the preferred language.
   *
   * @param language the language code to set
   */
  public void setLanguage(String language) {
    this.language = language;
  }
}
