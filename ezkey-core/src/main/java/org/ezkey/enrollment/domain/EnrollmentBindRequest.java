/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * verification, allowing devices to display appropriate integration context and user-friendly
 * information about the enrollment they are about to complete.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
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
   * Unique identifier of the enrollment to retrieve binding information for.
   *
   * <p>Must reference an existing enrollment. This ID is used to locate the specific enrollment and
   * gather related integration and application context for display to the user during the
   * enrollment process.
   */
  private Integer enrollmentId;

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
   * Gets the unique identifier of the enrollment.
   *
   * @return the enrollment ID
   */
  public Integer getEnrollmentId() {
    return enrollmentId;
  }

  /**
   * Sets the unique identifier of the enrollment.
   *
   * @param enrollmentId the enrollment ID to set
   */
  public void setEnrollmentId(Integer enrollmentId) {
    this.enrollmentId = enrollmentId;
  }

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
