/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentCreateRequest
 * Description: Domain request object for creating new device enrollments.
 */

package org.ezkey.enrollment.domain;

/**
 * Domain request object for creating new device enrollments.
 *
 * <p>This domain object represents the request data used by the service layer to initiate the
 * device enrollment process. It contains the basic enrollment configuration including integration
 * association, enrollment naming, and authentication policy settings for the new enrollment.
 *
 * <p><b>Usage Context:</b> Used by the EnrollmentService when creating new device enrollments
 * through the enrollment API. The service layer transforms API DTOs into this domain object for
 * business logic processing and enrollment entity creation.
 *
 * <p><b>Enrollment Flow:</b> This request initiates the enrollment process by providing the
 * foundational configuration for a new enrollment. After processing this request, the system
 * generates enrollment challenges and proof tokens that guide the device through the enrollment
 * verification and binding process.
 *
 * <p><b>Integration Binding:</b> Links the enrollment to a specific integration context, ensuring
 * that authentication attempts are properly scoped and validated against the correct application or
 * service integration.
 *
 * <p><b>Authentication Policy:</b> Allows configuration of challenge requirements for future
 * authentication attempts, enabling flexible security policies based on risk assessment and
 * integration requirements.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.service.EnrollmentService
 * @see org.ezkey.enrollment.domain.EnrollmentCreateResponse
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
public class EnrollmentCreateRequest {

  /**
   * Integration identifier for this enrollment.
   *
   * <p>Must reference an existing and active integration. This links the enrollment to a specific
   * application or service context, ensuring that authentication attempts are properly scoped and
   * validated against the correct integration's security policies and configuration.
   */
  private Integer integrationId;

  /**
   * Human-readable name for the enrollment.
   *
   * <p>Provides a descriptive label for the enrollment that can be displayed in user interfaces and
   * device management screens. This name helps users identify and manage multiple enrollments
   * across different integrations and devices.
   */
  private String name;

  /**
   * Flag indicating if authentication attempts require additional challenges.
   *
   * <p>When true, authentication attempts using this enrollment will require additional challenge
   * responses beyond the standard cryptographic proof. This may include biometric verification, PIN
   * entry, or other multi-factor authentication requirements as determined by the integration's
   * security policy and risk assessment.
   */
  private Boolean authAttemptChallengeRequired;

  /**
   * Optional contact email for the end-user (device owner). Used for incident response, revocation
   * notices, support. Provided by the integrating application at creation.
   */
  private String contactEmail;

  /**
   * Optional reference to the integrating app's user (username, user_id). Unique per integration
   * for lookup. Enables future auth attempt creation by userIdentifier (Phase 3).
   */
  private String userIdentifier;

  /**
   * Admin who created this enrollment. Set by the controller when request comes from admin (bearer
   * token); null when created via API key. For SOC 2 audit (CC6.1, CC7.2).
   */
  private Integer createdByAdminId;

  /**
   * Gets the integration identifier for this enrollment.
   *
   * @return the integration ID
   */
  public Integer getIntegrationId() {
    return integrationId;
  }

  /**
   * Sets the integration identifier for this enrollment.
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
  public String getName() {
    return name;
  }

  /**
   * Sets the human-readable name for the enrollment.
   *
   * @param name the enrollment name to set
   */
  public void setName(String name) {
    this.name = name;
  }

  /**
   * Gets the authentication challenge requirement flag.
   *
   * @return true if additional challenges are required for authentication attempts
   */
  public Boolean getAuthAttemptChallengeRequired() {
    return authAttemptChallengeRequired;
  }

  /**
   * Sets the authentication challenge requirement flag.
   *
   * @param authAttemptChallengeRequired true if additional challenges should be required
   */
  public void setAuthAttemptChallengeRequired(Boolean authAttemptChallengeRequired) {
    this.authAttemptChallengeRequired = authAttemptChallengeRequired;
  }

  /**
   * Gets the optional contact email for the end-user.
   *
   * @return the contact email, or null
   */
  public String getContactEmail() {
    return contactEmail;
  }

  /**
   * Sets the optional contact email for the end-user.
   *
   * @param contactEmail the contact email to set
   */
  public void setContactEmail(String contactEmail) {
    this.contactEmail = contactEmail;
  }

  /**
   * Gets the optional user identifier from the integrating app.
   *
   * @return the user identifier, or null
   */
  public String getUserIdentifier() {
    return userIdentifier;
  }

  /**
   * Sets the optional user identifier from the integrating app.
   *
   * @param userIdentifier the user identifier to set
   */
  public void setUserIdentifier(String userIdentifier) {
    this.userIdentifier = userIdentifier;
  }

  /**
   * Gets the admin ID who created this enrollment.
   *
   * @return the created-by admin ID, or null when created via API key
   */
  public Integer getCreatedByAdminId() {
    return createdByAdminId;
  }

  /**
   * Sets the admin ID who created this enrollment.
   *
   * @param createdByAdminId the created-by admin ID to set
   */
  public void setCreatedByAdminId(Integer createdByAdminId) {
    this.createdByAdminId = createdByAdminId;
  }
}
