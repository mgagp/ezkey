/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Description: Single rule for when the Admin API may hand out enrollment bind material (#1180, #1266, #1313).
 */

package org.ezkey.enrollment;

import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.integration.domain.entity.Integration;

/**
 * When an operator may obtain the bind material of an enrollment, for every Admin API exit of that
 * material: JSON, QR image, QR delivery and bootstrap export.
 *
 * <p>The proof token, and any QR encoding it, is available only while {@code CREATED}. The
 * challenge is also available while {@code BOUND}, because verify follows bind and the operator may
 * still have to give the code. Any other status, or no status, exposes nothing.
 *
 * <p><b>Administrator enrollments (#1313).</b> An administrator's enrollment sits on the system
 * integration. Admin sign-in is passwordless, so its bind material is the whole administrator
 * identity until a device binds. It has one source only: {@code /admins/{id}/onboarding*}, where
 * each reveal is audited. The {@code /enrollments} endpoints never return it, in any status: the
 * record and the list show it with null token and challenge, the QR answers {@code 409
 * admin-enrollment}, and QR delivery refuses it.
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

  /**
   * Whether an enrollment is an administrator's MFA enrollment: one that sits on the system
   * integration. The same test is used by QR delivery, the {@code contactEmail} write paths and the
   * {@code /enrollments} read paths.
   *
   * @param integration the enrollment's integration, possibly null
   * @return true when the integration is the system integration
   */
  public static boolean isAdminEnrollment(Integration integration) {
    return integration != null && Boolean.TRUE.equals(integration.getIsSystemIntegration());
  }

  /**
   * Whether the {@code /enrollments} endpoints may return this enrollment's bind material.
   * Administrator enrollments: never; their single source is {@code /admins/{id}/onboarding*}.
   *
   * @param integration the enrollment's integration, possibly null
   * @return false for an administrator enrollment
   */
  public static boolean servedByEnrollmentApi(Integration integration) {
    return !isAdminEnrollment(integration);
  }
}
