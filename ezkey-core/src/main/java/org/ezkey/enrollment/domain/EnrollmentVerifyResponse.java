/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentVerifyResponse
 * Description: Domain response object for enrollment verification completion status.
 */

package org.ezkey.enrollment.domain;

/**
 * Domain response object for enrollment verification completion status.
 *
 * <p>This domain object represents the response data returned by the service layer after processing
 * enrollment verification requests. It provides a simple status indicator confirming whether the
 * enrollment has been successfully verified and activated for authentication operations.
 *
 * <p><b>Usage Context:</b> Returned by the EnrollmentService after processing enrollment
 * verification requests from devices. The service layer creates this response object to communicate
 * the verification outcome and enrollment activation status back through the API layers.
 *
 * <p><b>Enrollment Flow:</b> This response represents the final outcome of the enrollment
 * verification process. When active is true, it indicates that the device has successfully
 * completed enrollment and the enrollment is ready for authentication operations.
 *
 * <p><b>Activation Status:</b> The active flag provides clear feedback about whether the enrollment
 * verification was successful and the enrollment is now operational. This status determines whether
 * the device can proceed with authentication attempts using this enrollment.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.service.EnrollmentService
 * @see org.ezkey.enrollment.domain.EnrollmentVerifyRequest
 * @see org.ezkey.enrollment.domain.EnrollmentCreateResponse
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
public class EnrollmentVerifyResponse {

  /**
   * Flag indicating whether the enrollment is active after verification.
   *
   * <p>When true, indicates that the enrollment verification was successful and the enrollment is
   * now active and ready for authentication operations. When false, indicates that verification
   * failed or the enrollment could not be activated due to validation errors or security
   * constraints.
   */
  private boolean active;

  /** Message segment included in the integration-signed verify result payload. */
  private String enrollmentVerifyMessage;

  /**
   * Ed25519 signature over the canonical verify result payload (see {@code
   * docs/ENROLLMENT_SIGNATURE_PAYLOAD.md}).
   */
  private String enrollmentVerifyPayloadSignedByIntegration;

  /**
   * Default constructor.
   *
   * <p>Creates an enrollment verification response with default values. The active status should be
   * set explicitly using the setter method based on the verification outcome.
   */
  public EnrollmentVerifyResponse() {}

  /**
   * Gets the enrollment activation status after verification.
   *
   * @return true if the enrollment is active and ready for authentication, false otherwise
   */
  public boolean isActive() {
    return active;
  }

  /**
   * Sets the enrollment activation status after verification.
   *
   * @param active true if the enrollment should be marked as active, false otherwise
   */
  public void setActive(boolean active) {
    this.active = active;
  }

  /**
   * Gets the user-facing verify completion message (also part of the signed payload).
   *
   * @return message text
   */
  public String getEnrollmentVerifyMessage() {
    return enrollmentVerifyMessage;
  }

  /**
   * Sets the user-facing verify completion message.
   *
   * @param enrollmentVerifyMessage message text
   */
  public void setEnrollmentVerifyMessage(String enrollmentVerifyMessage) {
    this.enrollmentVerifyMessage = enrollmentVerifyMessage;
  }

  /**
   * Gets the integration signature over the verify result payload.
   *
   * @return Base64URL Ed25519 signature
   */
  public String getEnrollmentVerifyPayloadSignedByIntegration() {
    return enrollmentVerifyPayloadSignedByIntegration;
  }

  /**
   * Sets the integration signature over the verify result payload.
   *
   * @param enrollmentVerifyPayloadSignedByIntegration signed canonical payload
   */
  public void setEnrollmentVerifyPayloadSignedByIntegration(
      String enrollmentVerifyPayloadSignedByIntegration) {
    this.enrollmentVerifyPayloadSignedByIntegration = enrollmentVerifyPayloadSignedByIntegration;
  }
}
