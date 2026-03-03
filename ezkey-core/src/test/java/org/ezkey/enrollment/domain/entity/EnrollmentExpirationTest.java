/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentExpirationTest
 * Description: Unit tests for enrollment expiration predicate (isExpired).
 */

package org.ezkey.enrollment.domain.entity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Enrollment#isExpired(OffsetDateTime)}.
 *
 * <p>Ensures the same criterion is used by bind/verify and the cleanup job.
 */
@DisplayName("Enrollment expiration predicate")
class EnrollmentExpirationTest {

  @Test
  @DisplayName("isExpired returns false when expiresAt is null")
  void isExpired_WhenExpiresAtNull_ReturnsFalse() {
    Enrollment e = new Enrollment();
    e.setExpiresAt(null);
    assertFalse(e.isExpired(OffsetDateTime.now()));
  }

  @Test
  @DisplayName("isExpired returns true when now is after expiresAt")
  void isExpired_WhenNowAfterExpiresAt_ReturnsTrue() {
    Enrollment e = new Enrollment();
    OffsetDateTime past = OffsetDateTime.now().minusMinutes(1);
    e.setExpiresAt(past);
    assertTrue(e.isExpired(OffsetDateTime.now()));
  }

  @Test
  @DisplayName("isExpired returns false when now is before expiresAt")
  void isExpired_WhenNowBeforeExpiresAt_ReturnsFalse() {
    Enrollment e = new Enrollment();
    OffsetDateTime future = OffsetDateTime.now().plusDays(1);
    e.setExpiresAt(future);
    assertFalse(e.isExpired(OffsetDateTime.now()));
  }

  @Test
  @DisplayName("isExpired returns true when now equals expiresAt")
  void isExpired_WhenNowEqualsExpiresAt_ReturnsTrue() {
    Enrollment e = new Enrollment();
    OffsetDateTime now = OffsetDateTime.now();
    e.setExpiresAt(now);
    assertTrue(e.isExpired(now));
  }
}
