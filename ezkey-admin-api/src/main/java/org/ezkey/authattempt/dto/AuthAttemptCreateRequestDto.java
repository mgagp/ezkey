/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuthAttemptCreateRequestDto
 * Description: Request DTO for creating authorization attempts in admin API.
 */

package org.ezkey.authattempt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for creating authorization attempts in admin API.
 *
 * <p>This DTO represents the request data needed to create a new authorization attempt through the
 * admin API. It contains enrollment information and challenge settings required to initiate an MFA
 * authentication request.
 *
 * <p><b>Usage Context:</b> Used by administrators or integrating applications to create
 * authentication requests that will be consumed by mobile devices through auth-api.
 *
 * <p><b>Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentId:</b> Target enrollment for the authentication request
 *   <li><b>challengeRequested:</b> Whether a challenge is required for this attempt
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.domain.AuthAttemptCreateRequest
 * @see AuthAttemptCreateResponseDto
 */
@Schema(description = "Request DTO for creating new authentication attempts")
public class AuthAttemptCreateRequestDto {

  /**
   * The enrollment ID for which the authentication attempt is requested. Must reference an existing
   * and active enrollment.
   */
  @Schema(
      description = "The enrollment ID for which the authentication attempt is requested",
      example = "123",
      required = true)
  private Integer enrollmentId;

  /**
   * Indicates whether a challenge is requested for this authentication attempt. When true,
   * additional challenge data will be generated for verification.
   */
  @Schema(
      description = "Indicates whether a challenge is requested for this authentication attempt",
      example = "false",
      required = true)
  private Boolean challengeRequested;

  /**
   * Gets the enrollment ID for the authentication attempt.
   *
   * @return the enrollment ID
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the enrollment ID for the authentication attempt.
   *
   * @param enrollmentId the enrollment ID to set
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  /**
   * Gets whether a challenge is requested.
   *
   * @return true if challenge is requested, false otherwise
   */
  public Boolean getChallengeRequested() {
    return challengeRequested;
  }

  /**
   * Sets whether a challenge is requested.
   *
   * @param challengeRequested true if challenge is requested, false otherwise
   */
  public void setChallengeRequested(Boolean challengeRequested) {
    this.challengeRequested = challengeRequested;
  }
}
