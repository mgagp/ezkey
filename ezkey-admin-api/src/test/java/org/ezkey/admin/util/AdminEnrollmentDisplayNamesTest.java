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

  private static final int MAX = AdminEnrollmentDisplayNames.MAX_ENROLLMENT_NAME_LENGTH;

  @Test
  void personDisplayNameKeepsExactly64Characters() {
    String first = "A".repeat(31);
    String last = "B".repeat(32);
    String name = AdminEnrollmentDisplayNames.personDisplayName(first, last, "user");
    assertThat(name).hasSize(MAX).isEqualTo(first + " " + last);
  }

  @Test
  void personDisplayNameTruncates65CharactersTo64() {
    String name =
        AdminEnrollmentDisplayNames.personDisplayName("A".repeat(32), "B".repeat(32), "user");
    assertThat(name).hasSize(MAX).isEqualTo("A".repeat(32) + " " + "B".repeat(31));
  }

  @Test
  void personDisplayNameTruncatesHundredPlusHundred() {
    String name =
        AdminEnrollmentDisplayNames.personDisplayName("A".repeat(100), "B".repeat(100), "user");
    assertThat(name).isEqualTo("A".repeat(MAX));
  }

  @Test
  void personDisplayNameDropsTrailingSpaceLeftAtTheCut() {
    String name = AdminEnrollmentDisplayNames.personDisplayName("A".repeat(64), "Dupont", "user");
    assertThat(name).isEqualTo("A".repeat(64));
    String cutBeforeSpace =
        AdminEnrollmentDisplayNames.personDisplayName("A".repeat(63), "Dupont", "user");
    assertThat(cutBeforeSpace).isEqualTo("A".repeat(63));
  }

  @Test
  void personDisplayNameNeverSplitsSurrogatePair() {
    // U+1F600 is one character for PostgreSQL VARCHAR, two UTF-16 units in Java.
    String emoji = "\uD83D\uDE00";
    String name =
        AdminEnrollmentDisplayNames.personDisplayName("A".repeat(63) + emoji + "Z", null, "u");
    assertThat(name).isEqualTo("A".repeat(63) + emoji);
    assertThat(name.codePointCount(0, name.length())).isEqualTo(MAX);
  }

  @Test
  void uniqueEnrollmentNameKeepsUsernameSuffixWholeWhenTruncating() {
    String username = "u".repeat(50);
    String person =
        AdminEnrollmentDisplayNames.personDisplayName("A".repeat(100), "B".repeat(100), username);
    String name = AdminEnrollmentDisplayNames.uniqueEnrollmentName(person, username, true);
    assertThat(name).hasSize(MAX).endsWith(" (" + username + ")");
    assertThat(name).isEqualTo("A".repeat(MAX - 53) + " (" + username + ")");
  }

  @Test
  void uniqueEnrollmentNameDropsTrailingSpaceBeforeSuffix() {
    String username = "marie.dupont";
    // Budget for the person part is 64 - 15 = 49: the cut lands right after "A..A " (49 chars).
    String person = "A".repeat(48) + " Dupont";
    String name = AdminEnrollmentDisplayNames.uniqueEnrollmentName(person, username, true);
    assertThat(name).isEqualTo("A".repeat(48) + " (marie.dupont)");
  }

  @Test
  void uniqueEnrollmentNameCapsUnsuffixedOverlongInput() {
    String name =
        AdminEnrollmentDisplayNames.uniqueEnrollmentName("A".repeat(80), "marie.dupont", false);
    assertThat(name).isEqualTo("A".repeat(MAX));
  }
}
