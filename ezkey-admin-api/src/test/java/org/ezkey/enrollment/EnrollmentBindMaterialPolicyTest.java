/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.enrollment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The bind material rule across every {@link EnrollmentStatus} (#1180, #1266). */
class EnrollmentBindMaterialPolicyTest {

  @ParameterizedTest(name = "{0}")
  @EnumSource(EnrollmentStatus.class)
  @DisplayName("Proof token only while CREATED")
  void proofToken(EnrollmentStatus status) {
    assertEquals(
        status == EnrollmentStatus.CREATED,
        EnrollmentBindMaterialPolicy.proofTokenAvailable(status));
  }

  @ParameterizedTest(name = "{0}")
  @EnumSource(EnrollmentStatus.class)
  @DisplayName("Challenge only while CREATED or BOUND")
  void challenge(EnrollmentStatus status) {
    assertEquals(
        status == EnrollmentStatus.CREATED || status == EnrollmentStatus.BOUND,
        EnrollmentBindMaterialPolicy.challengeAvailable(status));
  }

  @Test
  @DisplayName("No status exposes nothing")
  void nullStatus() {
    assertFalse(EnrollmentBindMaterialPolicy.proofTokenAvailable(null));
    assertFalse(EnrollmentBindMaterialPolicy.challengeAvailable(null));
  }
}
