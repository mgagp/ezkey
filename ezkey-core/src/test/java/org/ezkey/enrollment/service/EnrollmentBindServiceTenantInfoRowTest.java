/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EnrollmentBindServiceTenantInfoRowTest
 * Description: Unwraps native tenant-info rows that Spring Data may nest.
 */

package org.ezkey.enrollment.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link EnrollmentBindService#unwrapTenantInfoRow(Object[])}. */
class EnrollmentBindServiceTenantInfoRowTest {

  @Test
  @DisplayName("unwrapTenantInfoRow keeps a flat three-column row")
  void unwrapKeepsFlatRow() {
    Object[] row = {3, "Unicorn farm accountability", "Ops"};

    assertSame(row, EnrollmentBindService.unwrapTenantInfoRow(row));
  }

  @Test
  @DisplayName("unwrapTenantInfoRow flattens a nested three-column row")
  void unwrapFlattensNestedRow() {
    Object[] nested = {3, "Unicorn farm accountability", "Ops"};
    Object[] wrapped = {nested};

    assertArrayEquals(nested, EnrollmentBindService.unwrapTenantInfoRow(wrapped));
  }
}
