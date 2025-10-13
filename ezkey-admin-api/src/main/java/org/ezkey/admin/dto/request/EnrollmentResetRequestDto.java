/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentResetRequestDto
 * Description: Request DTO for resetting an enrollment after device loss.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for resetting an enrollment after device loss.
 *
 * <p>Used in the recovery workflow when an administrator has lost their device and needs to unbind
 * the old device and generate new credentials for binding a replacement device.
 *
 * <p><b>Authentication:</b> This endpoint requires a recovery token obtained from the /auth/recover
 * endpoint (not a regular bearer token).
 *
 * <p><b>Security:</b> The reset operation unbinds the old device (invalidates device_public_key)
 * and generates new enrollment credentials, preventing the lost/stolen device from being used for
 * authentication.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public class EnrollmentResetRequestDto {

  /**
   * The enrollment ID to reset.
   *
   * <p>This should be the administrator's MFA enrollment that needs to be reset due to device loss
   * or compromise.
   */
  @NotNull(message = "Enrollment ID is required")
  private Integer enrollmentId;

  /** Default constructor for JSON deserialization. */
  public EnrollmentResetRequestDto() {
    // Default constructor
  }

  /**
   * Constructs a new enrollment reset request.
   *
   * @param enrollmentId the enrollment ID to reset
   */
  public EnrollmentResetRequestDto(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

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
   * @param enrollmentId the enrollment ID
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

  /**
   * Returns a string representation of the enrollment reset request.
   *
   * @return string representation
   */
  @Override
  public String toString() {
    return "EnrollmentResetRequestDto{" + "enrollmentId=" + enrollmentId + '}';
  }
}
