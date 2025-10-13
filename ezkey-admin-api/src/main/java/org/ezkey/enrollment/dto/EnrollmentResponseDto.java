/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentResponseDto
 * Description: Response DTO for enrollment data in admin API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for enrollment data in admin API.
 *
 * <p>This DTO represents the complete enrollment information returned by the admin API for
 * administrative purposes. It provides comprehensive enrollment details including status,
 * configuration, and metadata while excluding sensitive cryptographic material for security
 * purposes.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return enrollment information to
 * administrators for monitoring and management purposes. Contains all non-sensitive data needed for
 * enrollment administration.
 *
 * <p><b>Core Identification Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentId:</b> Unique identifier for the enrollment
 *   <li><b>integrationId:</b> Integration identifier this enrollment belongs to
 *   <li><b>enrollmentName:</b> Human-readable name for the enrollment
 * </ul>
 *
 * <p><b>Status Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentStatus:</b> Enrollment lifecycle status (CREATED, BOUND, VERIFIED, INVALID)
 *   <li><b>enrollmentActive:</b> Flag indicating if the enrollment is currently active
 * </ul>
 *
 * <p><b>Authentication Configuration:</b>
 *
 * <ul>
 *   <li><b>enrollmentChallenge:</b> Challenge value for enrollment verification
 *   <li><b>enrollmentProofToken:</b> Unique code for enrollment verification
 *   <li><b>authAttemptChallengeRequired:</b> Flag indicating if authentication attempts require
 *       challenge
 * </ul>
 *
 * <p><b>Cryptographic Material (Public Keys Only):</b>
 *
 * <ul>
 *   <li><b>integrationPublicKey:</b> Public key for integration communication
 *   <li><b>devicePublicKey:</b> Public key for the device
 * </ul>
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive cryptographic keys and provides only the
 * information necessary for administrative operations. All private keys are deliberately excluded
 * for security purposes.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 * @see EnrollmentCreateRequestDto
 * @see EnrollmentCreateResponseDto
 */
@Schema(
    description =
        "Response DTO containing complete enrollment information for administrative purposes")
public class EnrollmentResponseDto {

  /** Unique identifier for the enrollment. Auto-generated primary key from the database. */
  @Schema(description = "Unique identifier for the enrollment", example = "123")
  private Integer enrollmentId;

  /**
   * Integration identifier this enrollment belongs to. Foreign key reference to the integration.
   */
  @Schema(description = "Integration identifier this enrollment belongs to", example = "1")
  private Integer integrationId;

  /** Human-readable name for the enrollment. Used for display purposes in user interfaces. */
  @Schema(description = "Human-readable name for the enrollment", example = "John's iPhone")
  private String enrollmentName;

  /** Enrollment lifecycle status. Indicates the current state of the enrollment process. */
  @Schema(
      description = "Enrollment lifecycle status",
      example = "VERIFIED",
      allowableValues = {"CREATED", "BOUND", "VERIFIED", "INVALID"})
  private String enrollmentStatus;

  /** Flag indicating if the enrollment is currently active. Used to enable/disable enrollment. */
  @Schema(description = "Flag indicating if the enrollment is currently active", example = "true")
  private Boolean enrollmentActive;

  /**
   * Challenge value for enrollment verification. Used in the enrollment challenge-response process.
   */
  @Schema(description = "Challenge value for enrollment verification", example = "123456")
  private Integer enrollmentChallenge;

  /** Unique code for enrollment verification. Used for enrollment verification step. */
  @Schema(description = "Unique code for enrollment verification", example = "EZK-ABC123-DEF456")
  private String enrollmentProofToken;

  /**
   * Flag indicating if authentication attempts require challenge. Used to configure authentication
   * behavior.
   */
  @Schema(
      description = "Flag indicating if authentication attempts require challenge",
      example = "false")
  private Boolean authAttemptChallengeRequired;

  /** Public key for integration communication. Used for verifying messages from the integration. */
  private String integrationPublicKey;

  /** Public key for the device. Used for device authentication verification. */
  private String devicePublicKey;

  /**
   * Gets the enrollment ID.
   *
   * @return the unique identifier for the enrollment
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the enrollment ID.
   *
   * @param enrollmentId the unique identifier for the enrollment to set
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  /**
   * Gets the integration ID.
   *
   * @return the integration identifier this enrollment belongs to
   */
  public Integer getIntegrationId() {
    return integrationId;
  }

  /**
   * Sets the integration ID.
   *
   * @param integrationId the integration identifier this enrollment belongs to
   */
  public void setIntegrationId(Integer integrationId) {
    this.integrationId = integrationId;
  }

  /**
   * Gets the enrollment name.
   *
   * @return the human-readable name for the enrollment
   */
  public String getEnrollmentName() {
    return enrollmentName;
  }

  /**
   * Sets the enrollment name.
   *
   * @param enrollmentName the human-readable name for the enrollment to set
   */
  public void setEnrollmentName(String enrollmentName) {
    this.enrollmentName = enrollmentName;
  }

  /**
   * Gets the enrollment lifecycle status.
   *
   * @return the enrollment status (CREATED, BOUND, VERIFIED, INVALID)
   */
  public String getEnrollmentStatus() {
    return enrollmentStatus;
  }

  /**
   * Sets the enrollment lifecycle status.
   *
   * @param enrollmentStatus the enrollment status to set
   */
  public void setEnrollmentStatus(String enrollmentStatus) {
    this.enrollmentStatus = enrollmentStatus;
  }

  /**
   * Gets the enrollment active status.
   *
   * @return true if the enrollment is currently active, false otherwise
   */
  public Boolean getEnrollmentActive() {
    return enrollmentActive;
  }

  /**
   * Sets the enrollment active status.
   *
   * @param enrollmentActive true if the enrollment is currently active, false otherwise
   */
  public void setEnrollmentActive(Boolean enrollmentActive) {
    this.enrollmentActive = enrollmentActive;
  }

  /**
   * Gets the enrollment challenge.
   *
   * @return the challenge value for enrollment verification
   */
  public Integer getEnrollmentChallenge() {
    return enrollmentChallenge;
  }

  /**
   * Sets the enrollment challenge.
   *
   * @param enrollmentChallenge the challenge value for enrollment verification to set
   */
  public void setEnrollmentChallenge(Integer enrollmentChallenge) {
    this.enrollmentChallenge = enrollmentChallenge;
  }

  /**
   * Gets the enrollment proof token.
   *
   * @return the unique code for enrollment verification
   */
  public String getEnrollmentProofToken() {
    return enrollmentProofToken;
  }

  /**
   * Sets the enrollment proof token.
   *
   * @param enrollmentProofToken the unique code for enrollment verification to set
   */
  public void setEnrollmentProofToken(String enrollmentProofToken) {
    this.enrollmentProofToken = enrollmentProofToken;
  }

  /**
   * Gets the authentication attempt challenge requirement status.
   *
   * @return true if authentication attempts require challenge, false otherwise
   */
  public Boolean getAuthAttemptChallengeRequired() {
    return authAttemptChallengeRequired;
  }

  /**
   * Sets the authentication attempt challenge requirement status.
   *
   * @param authAttemptChallengeRequired true if authentication attempts require challenge, false
   *     otherwise
   */
  public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
    this.authAttemptChallengeRequired = authAttemptChallengeRequired;
  }

  /**
   * Gets the integration public key.
   *
   * @return the public key for integration communication
   */
  public String getIntegrationPublicKey() {
    return integrationPublicKey;
  }

  /**
   * Sets the integration public key.
   *
   * @param integrationPublicKey the public key for integration communication to set
   */
  public void setIntegrationPublicKey(String integrationPublicKey) {
    this.integrationPublicKey = integrationPublicKey;
  }

  /**
   * Gets the device public key.
   *
   * @return the public key for the device
   */
  public String getDevicePublicKey() {
    return devicePublicKey;
  }

  /**
   * Sets the device public key.
   *
   * @param devicePublicKey the public key for the device to set
   */
  public void setDevicePublicKey(String devicePublicKey) {
    this.devicePublicKey = devicePublicKey;
  }
}
