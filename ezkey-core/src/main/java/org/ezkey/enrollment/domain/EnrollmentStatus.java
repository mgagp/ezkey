/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentStatus
 * Description: Enumeration representing the lifecycle status of an enrollment.
 */

package org.ezkey.enrollment.domain;

/**
 * Enumeration representing the lifecycle status of an enrollment.
 *
 * <p>This enum defines the possible states an enrollment can be in during its lifecycle, from
 * initial creation through device binding, verification, and final validation. Each status
 * represents a specific stage in the enrollment process and determines what operations are allowed
 * on the enrollment.
 *
 * <p><b>Enrollment Lifecycle:</b>
 *
 * <ul>
 *   <li><b>CREATED:</b> Initial state when enrollment is first created
 *   <li><b>BOUND:</b> Device has claimed the enrollment during binding process
 *   <li><b>VERIFIED:</b> Device has completed cryptographic verification successfully
 *   <li><b>INVALID:</b> Verification failed or enrollment is compromised
 * </ul>
 *
 * <p><b>Security Considerations:</b> The status progression ensures that enrollments can only
 * advance through verified cryptographic steps, preventing unauthorized state changes and
 * maintaining the integrity of the enrollment process.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
public enum EnrollmentStatus {

  /**
   * Initial state when enrollment is first created.
   *
   * <p>The enrollment exists in the system but has not been claimed by any device. This is the
   * default state for new enrollments.
   */
  CREATED("CREATED"),

  /**
   * Device has claimed the enrollment during binding process.
   *
   * <p>The mobile device has successfully retrieved the enrollment information and is ready to
   * proceed with cryptographic verification.
   */
  BOUND("BOUND"),

  /**
   * Device has completed cryptographic verification successfully.
   *
   * <p>The enrollment has passed all verification steps and is cryptographically sound. This
   * enrollment can be used for authentication attempts when combined with the active flag.
   */
  VERIFIED("VERIFIED"),

  /**
   * Verification failed or enrollment is compromised.
   *
   * <p>The enrollment failed verification or has been marked as invalid due to security concerns.
   * This enrollment cannot be used for authentication and may require re-enrollment.
   */
  INVALID("INVALID"),

  /**
   * Enrollment has been permanently revoked by an administrator.
   *
   * <p>Semantically distinct from {@link #INVALID}: revocation is <em>admin-initiated</em> (a
   * deliberate security decision), whereas INVALID is <em>system-initiated</em> (verification
   * failure or cryptographic compromise). A revoked enrollment cannot be reactivated. The
   * associated cryptographic credentials are considered destroyed from a security standpoint.
   *
   * <p>rapid access removal — removal of access.
   */
  REVOKED("REVOKED"),

  /**
   * Pending enrollment expired before device completed bind/verify.
   *
   * <p>Set when a CREATED (or BOUND) enrollment has an {@code expires_at} in the past. Time-based
   * expiration limits proof-token exposure and aligns with industry practice (e.g. Duo/Okta
   * enrollment link validity). Distinct from {@link #REVOKED} (admin-initiated) and {@link
   * #INVALID} (verification failure).
   */
  EXPIRED("EXPIRED");

  private final String value;

  /**
   * Constructs an enrollment status with the specified string value.
   *
   * @param value the string representation of this status
   */
  EnrollmentStatus(String value) {
    this.value = value;
  }

  /**
   * Returns the string value of this enrollment status.
   *
   * <p>This value corresponds to the database representation and is used for persistence and API
   * serialization.
   *
   * @return the string value of this status
   */
  public String getValue() {
    return value;
  }

  /**
   * Returns the enrollment status corresponding to the specified string value.
   *
   * <p>This method is used for deserialization from database values and API requests. It performs
   * case-sensitive matching.
   *
   * @param value the string value to convert
   * @return the corresponding enrollment status
   * @throws IllegalArgumentException if the value does not correspond to any status
   */
  public static EnrollmentStatus fromValue(String value) {
    for (EnrollmentStatus status : values()) {
      if (status.value.equals(value)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Invalid enrollment status value: " + value);
  }
}
