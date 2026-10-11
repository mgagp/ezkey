/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ShedLockTestHelperMappingTest
 * Description: Unit tests for compose-name → Admin API replica mapping (no Docker).
 */

package org.ezkey.tests.security.scheduler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure mapping tests for {@link ShedLockTestHelper#mapComposeNameToAdminReplica(String)}.
 *
 * @since 2026
 */
class ShedLockTestHelperMappingTest {

  @Test
  @DisplayName("maps compose / inspect names to admin-api replicas")
  void mapsComposeNames() {
    assertThat(ShedLockTestHelper.mapComposeNameToAdminReplica("ezkey-admin-api-1"))
        .isEqualTo(ShedLockTestHelper.ADMIN_API_1);
    assertThat(ShedLockTestHelper.mapComposeNameToAdminReplica("/ezkey-admin-api-2"))
        .isEqualTo(ShedLockTestHelper.ADMIN_API_2);
    assertThat(ShedLockTestHelper.mapComposeNameToAdminReplica("project-admin-api-1-1"))
        .isEqualTo(ShedLockTestHelper.ADMIN_API_1);
  }

  @Test
  @DisplayName("rejects unrelated names and blank")
  void rejectsUnrelated() {
    assertThat(ShedLockTestHelper.mapComposeNameToAdminReplica("74d78fe655b3")).isNull();
    assertThat(ShedLockTestHelper.mapComposeNameToAdminReplica("ezkey-auth-api-1")).isNull();
    assertThat(ShedLockTestHelper.mapComposeNameToAdminReplica("")).isNull();
    assertThat(ShedLockTestHelper.mapComposeNameToAdminReplica(null)).isNull();
  }
}
