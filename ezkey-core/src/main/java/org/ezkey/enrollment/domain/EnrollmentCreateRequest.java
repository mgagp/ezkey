/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
}
