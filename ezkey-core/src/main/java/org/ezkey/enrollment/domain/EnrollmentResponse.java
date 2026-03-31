/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentResponse
 * Description: Domain response object containing comprehensive enrollment data.
 */

package org.ezkey.enrollment.domain;

import java.time.OffsetDateTime;

/**
 * Domain response object containing comprehensive enrollment data.
 *
 * <p>This domain object represents the complete enrollment information returned by the service
 * layer for enrollment queries and management operations. It provides a comprehensive view of
 * enrollment state, configuration, cryptographic keys, and metadata necessary for enrollment
 * management and authentication operations.
 *
 * <p><b>Usage Context:</b> Used by the EnrollmentService to return detailed enrollment information
 * for administrative operations, enrollment status queries, and integration with other system
 * components. This response provides a clean separation between domain entities and API responses.
 *
 * <p><b>Enrollment Lifecycle:</b> Contains all enrollment state flags including read status,
 * validity, and activation state that track the enrollment through its complete lifecycle from
 * creation to active authentication operations or deactivation.
 *
 * <p><b>Cryptographic Identity:</b> Includes both integration and device public keys that establish
 * the cryptographic relationship between the integration system and enrolled device. These keys
 * enable secure authentication operations and signature verification.
 *
 * <p><b>Challenge Configuration:</b> Provides enrollment challenge data and authentication policy
 * settings that control how future authentication attempts will be processed for this specific
 * enrollment.
 *
 * <p><b>Administrative Data:</b> Includes creation timestamps and identification information
 * necessary for enrollment management, audit trails, and administrative operations.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.service.EnrollmentService
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 * @see org.ezkey.enrollment.domain.EnrollmentCreateRequest
 */
public class EnrollmentResponse {

  /**
   * Unique identifier for the enrollment.
   *
   * <p>Auto-generated primary key from the database that uniquely identifies this enrollment within
   * the system. Used for all enrollment operations and references from other system components.
   */
  private Integer enrollmentId;

  /**
   * Integration identifier this enrollment belongs to.
   *
   * <p>Foreign key reference to the integration that created this enrollment. Links the enrollment
   * to its parent integration context for proper scoping and security validation during
   * authentication operations.
   */
  private Integer integrationId;

  /**
   * Human-readable name for the enrollment.
   *
   * <p>Descriptive label for the enrollment used for display purposes in user interfaces and
   * administrative screens. Helps users identify and manage multiple enrollments across different
   * integrations and devices.
   */
  private String enrollmentName;

  /**
   * Enrollment lifecycle status.
   *
   * <p>Indicates the current state of the enrollment process from creation through verification.
   * Used to monitor enrollment lifecycle and determine what operations are allowed on the
   * enrollment.
   */
  private String enrollmentStatus;

  /**
   * Flag indicating if the enrollment is currently active.
   *
   * <p>Controls whether the enrollment can be used for authentication operations. Inactive
   * enrollments are disabled but preserved for audit purposes and can be reactivated if needed.
   */
  private Boolean enrollmentActive;

  /**
   * Challenge value for enrollment verification.
   *
   * <p>Numeric challenge token used during the enrollment verification process. This value is
   * validated during device verification to ensure legitimate enrollment completion and prevent
   * unauthorized enrollment hijacking.
   */
  private Integer enrollmentChallenge;

  /**
   * Unique code for enrollment verification.
   *
   * <p>Cryptographic proof token used during enrollment verification step to establish secure
   * binding between device and enrollment. This token must be signed by the device to complete
   * enrollment and prove cryptographic capability.
   */
  private String enrollmentProofToken;

  /**
   * Flag indicating if authentication attempts require additional challenges.
   *
   * <p>Configuration setting that determines whether authentication attempts using this enrollment
   * will require additional challenge responses beyond standard cryptographic proof. Enables
   * flexible security policies based on risk assessment and integration requirements.
   */
  private Boolean authAttemptChallengeRequired;

  /**
   * Public key for integration communication.
   *
   * <p>Cryptographic public key of the integration used for verifying signatures and validating
   * messages from the integration system. Enables the device to cryptographically verify the
   * authenticity of authentication challenges and integration communications.
   */
  private String integrationPublicKey;

  /**
   * Public key for the enrolled device.
   *
   * <p>Cryptographic public key of the device established during enrollment verification. Used by
   * the system to verify signatures from the device during authentication attempts and ensure only
   * the legitimate enrolled device can respond to authentication challenges.
   */
  private String devicePublicKey;

  /**
   * Timestamp when the enrollment was created.
   *
   * <p>Records the exact time when the enrollment was initially created in the system. Used for
   * audit trails, lifecycle management, and sorting enrollments by creation date in administrative
   * interfaces.
   */
  private OffsetDateTime createdAt;

  /**
   * Gets the unique identifier for the enrollment.
   *
   * @return the enrollment ID
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the unique identifier for the enrollment.
   *
   * @param enrollmentId the enrollment ID to set
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  /**
   * Gets the integration identifier this enrollment belongs to.
   *
   * @return the integration ID
   */
  public Integer getIntegrationId() {
    return integrationId;
  }

  /**
   * Sets the integration identifier this enrollment belongs to.
   *
   * @param integrationId the integration ID to set
   */
  public void setIntegrationId(Integer integrationId) {
    this.integrationId = integrationId;
  }

  /**
   * Gets the human-readable name for the enrollment.
   *
   * @return the enrollment name
   */
  public String getEnrollmentName() {
    return enrollmentName;
  }

  /**
   * Sets the human-readable name for the enrollment.
   *
   * @param enrollmentName the enrollment name to set
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
   * Gets the enrollment active status flag.
   *
   * @return true if the enrollment is currently active, false otherwise
   */
  public Boolean getEnrollmentActive() {
    return enrollmentActive;
  }

  /**
   * Sets the enrollment active status flag.
   *
   * @param enrollmentActive true if the enrollment should be active
   */
  public void setEnrollmentActive(Boolean enrollmentActive) {
    this.enrollmentActive = enrollmentActive;
  }

  /**
   * Gets the challenge value for enrollment verification.
   *
   * @return the enrollment challenge value
   */
  public Integer getEnrollmentChallenge() {
    return enrollmentChallenge;
  }

  /**
   * Sets the challenge value for enrollment verification.
   *
   * @param enrollmentChallenge the enrollment challenge value to set
   */
  public void setEnrollmentChallenge(Integer enrollmentChallenge) {
    this.enrollmentChallenge = enrollmentChallenge;
  }

  /**
   * Gets the unique code for enrollment verification.
   *
   * @return the enrollment proof token
   */
  public String getEnrollmentProofToken() {
    return enrollmentProofToken;
  }

  /**
   * Sets the unique code for enrollment verification.
   *
   * @param enrollmentProofToken the enrollment proof token to set
   */
  public void setEnrollmentProofToken(String enrollmentProofToken) {
    this.enrollmentProofToken = enrollmentProofToken;
  }

  /**
   * Gets the authentication challenge requirement flag.
   *
   * @return true if authentication attempts require additional challenges, false otherwise
   */
  public Boolean getAuthAttemptChallengeRequired() {
    return authAttemptChallengeRequired;
  }

  /**
   * Sets the authentication challenge requirement flag.
   *
   * @param authAttemptChallengeRequired true if authentication attempts should require additional
   *     challenges
   */
  public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
    this.authAttemptChallengeRequired = authAttemptChallengeRequired;
  }

  /**
   * Gets the public key for integration communication.
   *
   * @return the integration public key
   */
  public String getIntegrationPublicKey() {
    return integrationPublicKey;
  }

  /**
   * Sets the public key for integration communication.
   *
   * @param integrationPublicKey the integration public key to set
   */
  public void setIntegrationPublicKey(String integrationPublicKey) {
    this.integrationPublicKey = integrationPublicKey;
  }

  /**
   * Gets the public key for the enrolled device.
   *
   * @return the device public key
   */
  public String getDevicePublicKey() {
    return devicePublicKey;
  }

  /**
   * Sets the public key for the enrolled device.
   *
   * @param devicePublicKey the device public key to set
   */
  public void setDevicePublicKey(String devicePublicKey) {
    this.devicePublicKey = devicePublicKey;
  }

  /**
   * Gets the timestamp when the enrollment was created.
   *
   * @return the creation timestamp
   */
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Sets the timestamp when the enrollment was created.
   *
   * @param createdAt the creation timestamp to set
   */
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }
}
