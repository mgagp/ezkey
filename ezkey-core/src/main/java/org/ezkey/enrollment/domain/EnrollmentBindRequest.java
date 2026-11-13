/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentBindRequest
 * Description: Domain request object for retrieving enrollment binding information.
 */

package org.ezkey.enrollment.domain;

/**
 * Domain request object for retrieving enrollment binding information.
 *
 * <p>This domain object represents the request data used by the service layer to fetch enrollment
 * binding details for device configuration and display.
 *
 * <p><b>Usage Context:</b> Used by the EnrollmentService when devices or applications request
 * enrollment binding information for display purposes. The service layer transforms API DTOs into
 * this domain object for business logic processing.
 *
 * <p><b>Enrollment Flow:</b> This request typically occurs after enrollment creation and before
 * verification. The proof token is the invitation secret. The enrollment id is not part of the
 * request; the service resolves the row from the token hash and returns the id in the response
 * (ADR-0019).
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.service.EnrollmentService
 * @see org.ezkey.enrollment.domain.EnrollmentBindResponse
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 */
public class EnrollmentBindRequest {

  /**
   * The enrollment proof token for authentication.
   *
   * <p>Unique cryptographic token generated during enrollment creation that must be provided to
   * access enrollment binding information. This token prevents enumeration attacks by ensuring only
   * parties with valid proof tokens can access enrollment data. The token must match the one
   * generated during enrollment creation.
   */
  private String enrollmentProofToken;

  /**
   * Gets the enrollment proof token for authentication.
   *
   * @return the enrollment proof token
   */
  public String getEnrollmentProofToken() {
    return enrollmentProofToken;
  }

  /**
   * Sets the enrollment proof token for authentication.
   *
   * @param enrollmentProofToken the enrollment proof token to set
   */
  public void setEnrollmentProofToken(String enrollmentProofToken) {
    this.enrollmentProofToken = enrollmentProofToken;
  }
}
