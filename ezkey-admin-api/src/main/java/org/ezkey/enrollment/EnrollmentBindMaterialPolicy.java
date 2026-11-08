/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Single rule for when the Admin API may hand out enrollment bind material (#1180, #1266).
 */

package org.ezkey.enrollment;

import org.ezkey.enrollment.domain.EnrollmentStatus;

/**
 * When an operator may obtain the bind material of an enrollment, for every Admin API exit of that
 * material: JSON, QR image, QR delivery and bootstrap export.
 *
 * <p>The proof token, and any QR encoding it, is available only while {@code CREATED}. The
 * challenge is also available while {@code BOUND}, because verify follows bind and the operator may
 * still have to give the code. Any other status, or no status, exposes nothing.
 *
 * <p>The show-once {@code POST} activation and reset responses are outside this rule: they return
 * material the same request has just issued.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class EnrollmentBindMaterialPolicy {

  private EnrollmentBindMaterialPolicy() {}

  /**
   * Whether the proof token, or a QR encoding it, may leave the Admin API.
   *
   * @param status the enrollment status, possibly null
   * @return true only while {@code CREATED}
   */
  public static boolean proofTokenAvailable(EnrollmentStatus status) {
    return status == EnrollmentStatus.CREATED;
  }

  /**
   * Whether the enrollment challenge may leave the Admin API.
   *
   * @param status the enrollment status, possibly null
   * @return true only while {@code CREATED} or {@code BOUND}
   */
  public static boolean challengeAvailable(EnrollmentStatus status) {
    return status == EnrollmentStatus.CREATED || status == EnrollmentStatus.BOUND;
  }
}
