/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: AuthAttemptPendingRequest
 * Description: Domain request object for retrieving pending authentication attempts.
 */

package org.ezkey.authattempt.domain;

/**
 * Domain request object for retrieving pending authentication attempts. Updated to include
 * enrollmentProofToken for secure enrollment identification.
 *
 * <p>This domain object represents the request data used internally by the service layer to check
 * for pending authentication attempts for a specific enrollment. It contains device authentication
 * credentials needed to validate device identity and retrieve appropriate pending authentication
 * requests.
 *
 * <p><b>Usage Context:</b> Used by the AuthAttemptService when mobile devices poll for pending
 * authentication requests. The service layer transforms API DTOs into this domain object for
 * business logic processing and device validation.
 *
 * <p><b>Authentication Flow:</b> Mobile devices use this request to periodically check for
 * authentication attempts that require user approval. The device must provide cryptographic proof
 * of identity through device proof tokens to access pending authentication challenges.
 *
 * <p><b>Security Model:</b> Contains cryptographic signatures that validate the device's identity
 * and ensure only legitimate enrolled devices can access pending authentication attempts. This
 * prevents unauthorized access to authentication challenges.
 *
 * <p><b>Security Enhancement:</b> This domain object now includes enrollmentProofToken to prevent
 * enumeration attacks by removing the enrollment ID from the URL path. The enrollmentProofToken
 * provides cryptographic proof of enrollment ownership while maintaining the security of the
 * authentication flow.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.authattempt.service.AuthAttemptService
 * @see org.ezkey.authattempt.domain.AuthAttemptPendingResponse
 * @see org.ezkey.authattempt.domain.entity.AuthAttempt
 */
public class AuthAttemptPendingRequest {

  /**
   * The enrollment ID for internal processing. Note: This field is validated against the
   * enrollmentProofToken for security.
   *
   * <p>Must reference an existing and active enrollment. Used to identify which device enrollment
   * is requesting pending authentication attempts and to filter authentication requests to the
   * appropriate device. This field is validated against the enrollmentProofToken to prevent
   * enumeration attacks and ensure enrollment ownership.
   */
  private Integer enrollmentId;

  /**
   * Cryptographic proof token that authenticates the enrollment.
   *
   * <p>This token replaces URL-based enrollment identification to prevent enumeration attacks. It
   * provides cryptographic proof that the requesting device owns the enrollment and prevents
   * unauthorized access to pending authentication attempts.
   */
  private String enrollmentProofToken;

  /**
   * The device's proof token for authentication.
   *
   * <p>Contains the device-specific proof token used to identify and authenticate the requesting
   * device during the authentication flow. Generated during enrollment and unique to each device.
   * This token is validated against the enrolled device's stored credentials.
   */
  private String deviceProofToken;

  /**
   * Cryptographically signed device proof token.
   *
   * <p>Contains the signed version of the device proof token, providing cryptographic proof of
   * device authenticity and preventing request forgery or unauthorized access to pending
   * authentication attempts. The signature is verified using the device's public key.
   */
  private String deviceProofTokenSigned;

  /**
   * Gets the enrollment ID for this pending authentication request.
   *
   * @return the enrollment ID
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the enrollment ID for this pending authentication request.
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
   * Gets the device proof token.
   *
   * @return the device proof token
   */
  public String getDeviceProofToken() {
    return deviceProofToken;
  }

  /**
   * Sets the device proof token.
   *
   * @param deviceProofToken the device proof token to set
   */
  public void setDeviceProofToken(String deviceProofToken) {
    this.deviceProofToken = deviceProofToken;
  }

  /**
   * Gets the cryptographically signed device proof token.
   *
   * @return the signed device proof token
   */
  public String getDeviceProofTokenSigned() {
    return deviceProofTokenSigned;
  }

  /**
   * Sets the cryptographically signed device proof token.
   *
   * @param deviceProofTokenSigned the signed device proof token to set
   */
  public void setDeviceProofTokenSigned(String deviceProofTokenSigned) {
    this.deviceProofTokenSigned = deviceProofTokenSigned;
  }
}
