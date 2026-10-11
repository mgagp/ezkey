/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: ShedLockTestHelperMappingTest
 * Description: Unit tests for compose-name and Docker ID-prefix → Admin API replica mapping.
 */

package org.ezkey.tests.security.scheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pure mapping tests for {@link ShedLockTestHelper#mapComposeNameToAdminReplica(String)} and {@link
 * ShedLockTestHelper#selectReplicaByIdPrefix(String, String, String)}.
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

  @Test
  @DisplayName("selects replica by unambiguous Docker ID prefix")
  void selectsByIdPrefix() {
    String id1 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    String id2 = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
    assertThat(ShedLockTestHelper.selectReplicaByIdPrefix("aaaaaaaaaaaa", id1, id2))
        .isEqualTo(ShedLockTestHelper.ADMIN_API_1);
    assertThat(ShedLockTestHelper.selectReplicaByIdPrefix("bbbbbbbbbbbb", id1, id2))
        .isEqualTo(ShedLockTestHelper.ADMIN_API_2);
    assertThat(ShedLockTestHelper.selectReplicaByIdPrefix("cccccccccccc", id1, id2)).isNull();
    assertThat(ShedLockTestHelper.selectReplicaByIdPrefix("short", id1, id2)).isNull();
  }

  @Test
  @DisplayName("fails when both replica IDs share the locked_by prefix")
  void failsOnAmbiguousIdPrefix() {
    String shared = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789";
    String other = "abcdef012345ffffeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee";
    assertThatThrownBy(
            () -> ShedLockTestHelper.selectReplicaByIdPrefix("abcdef012345", shared, other))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("Ambiguous ShedLock locked_by");
  }
}
