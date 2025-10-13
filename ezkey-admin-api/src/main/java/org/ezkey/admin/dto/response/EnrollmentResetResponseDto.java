/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentResetResponseDto
 * Description: Response DTO for enrollment reset operation.
 */

package org.ezkey.admin.dto.response;

/**
 * Response DTO for enrollment reset operation.
 *
 * <p>Contains the new enrollment credentials that must be used to bind a replacement device after
 * the enrollment has been reset.
 *
 * <p><b>Security:</b> The old device is now unbound and cannot be used for authentication. The
 * administrator must bind a new device using these credentials.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class EnrollmentResetResponseDto {

  /** Indicates if the reset was successful. */
  private Boolean success;

  /** The enrollment ID (same as before, but credentials are new). */
  private Integer enrollmentId;

  /** New enrollment proof token for binding. */
  private String enrollmentProofToken;

  /** New enrollment challenge code for verification. */
  private Integer enrollmentChallenge;

  /** Integration ID (for reference). */
  private Integer integrationId;

  /** Response message. */
  private String message;

  /** Default constructor for JSON serialization. */
  public EnrollmentResetResponseDto() {
    // Default constructor
  }

  /**
   * Constructs a successful enrollment reset response.
   *
   * @param enrollmentId the enrollment ID
   * @param enrollmentProofToken the new proof token
   * @param enrollmentChallenge the new challenge code
   * @param integrationId the integration ID
   */
  public EnrollmentResetResponseDto(
      Integer enrollmentId,
      String enrollmentProofToken,
      Integer enrollmentChallenge,
      Integer integrationId) {
    this.success = true;
    this.enrollmentId = enrollmentId;
    this.enrollmentProofToken = enrollmentProofToken;
    this.enrollmentChallenge = enrollmentChallenge;
    this.integrationId = integrationId;
    this.message =
        "Enrollment reset successfully. Old device unbound. Use these credentials to bind new device.";
  }

  /**
   * Constructs an error enrollment reset response.
   *
   * @param message the error message
   */
  public EnrollmentResetResponseDto(String message) {
    this.success = false;
    this.message = message;
  }

  // Getters and setters

  public Boolean getSuccess() {
    return success;
  }

  public void setSuccess(Boolean success) {
    this.success = success;
  }

  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  public String getEnrollmentProofToken() {
    return enrollmentProofToken;
  }

  public void setEnrollmentProofToken(String enrollmentProofToken) {
    this.enrollmentProofToken = enrollmentProofToken;
  }

  public Integer getEnrollmentChallenge() {
    return enrollmentChallenge;
  }

  public void setEnrollmentChallenge(Integer enrollmentChallenge) {
    this.enrollmentChallenge = enrollmentChallenge;
  }

  public Integer getIntegrationId() {
    return integrationId;
  }

  public void setIntegrationId(Integer integrationId) {
    this.integrationId = integrationId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

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
