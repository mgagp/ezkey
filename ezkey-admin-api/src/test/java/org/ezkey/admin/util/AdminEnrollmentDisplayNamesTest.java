/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminEnrollmentDisplayNamesTest
 * Description: Person-first admin MFA enrollment names and uniqueness suffix.
 */

package org.ezkey.admin.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AdminEnrollmentDisplayNamesTest {

  @Test
  void personDisplayNameJoinsFirstAndLast() {
    assertThat(AdminEnrollmentDisplayNames.personDisplayName("Marie", "Dupont", "marie.dupont"))
        .isEqualTo("Marie Dupont");
  }

  @Test
  void personDisplayNameFallsBackToUsernameWhenNamesAreBlank() {
    assertThat(AdminEnrollmentDisplayNames.personDisplayName("  ", null, "jmartin"))
        .isEqualTo("jmartin");
  }

  @Test
  void uniqueEnrollmentNameKeepsPersonWhenAvailable() {
    assertThat(
            AdminEnrollmentDisplayNames.uniqueEnrollmentName("Marie Dupont", "marie.dupont", false))
        .isEqualTo("Marie Dupont");
  }

  @Test
  void uniqueEnrollmentNameSuffixesUsernameWhenPersonNameIsTaken() {
    assertThat(
            AdminEnrollmentDisplayNames.uniqueEnrollmentName("Marie Dupont", "marie.dupont", true))
        .isEqualTo("Marie Dupont (marie.dupont)");
  }

  @Test
  void uniqueEnrollmentNameDoesNotPrefixRole() {
    String name =
        AdminEnrollmentDisplayNames.uniqueEnrollmentName(
            AdminEnrollmentDisplayNames.personDisplayName("Jean", "Martin", "jmartin"),
            "jmartin",
            false);
    assertThat(name).isEqualTo("Jean Martin");
    assertThat(name).doesNotContain("Admin");
    assertThat(name).doesNotContain("MFA");
  }
}
