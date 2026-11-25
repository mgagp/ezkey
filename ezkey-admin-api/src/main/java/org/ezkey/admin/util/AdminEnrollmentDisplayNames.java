/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Utility: AdminEnrollmentDisplayNames
 * Description: Person-first enrollmentName for admin MFA bind/display.
 */

package org.ezkey.admin.util;

import java.text.BreakIterator;
import java.util.Locale;

/**
 * Builds the authenticator-facing {@code enrollmentName} for Global and Tenant Admin MFA.
 *
 * <p>The mobile app treats {@code enrollmentName} as the person hero. Role, username, and the word
 * {@code MFA} belong in Admin UI composition ({@code adminType}), not in this string.
 *
 * <p>If two enrollments on one phone cannot be told apart, do not encode the role here. Unpark
 * {@code I-2026-09-15-mobile-admin-enrollment-account-label} and add a dedicated bind field.
 *
 * <p>Results never exceed {@link #MAX_ENROLLMENT_NAME_LENGTH} characters ({@code
 * ezkey_enrollment.enrollment_name} is {@code VARCHAR(64)}, while first and last names accept 100
 * each). The person part is shortened; the {@code (username)} suffix is kept whole (#1520).
 *
 * @since 2026
 */
public final class AdminEnrollmentDisplayNames {

  /**
   * Limit of {@code ezkey_enrollment.enrollment_name} ({@code VARCHAR(64)}), applied as UTF-16
   * units like the enrollment edit validation.
   */
  public static final int MAX_ENROLLMENT_NAME_LENGTH = 64;

  private AdminEnrollmentDisplayNames() {
    // Utility class - no instantiation
  }

  /**
   * Person-first display name: {@code first last}, else whichever part exists, else username.
   * Truncated to {@link #MAX_ENROLLMENT_NAME_LENGTH} characters.
   *
   * @param firstName admin first name, may be blank
   * @param lastName admin last name, may be blank
   * @param username unique admin username, used only when no person name exists
   * @return trimmed non-blank display name of at most {@link #MAX_ENROLLMENT_NAME_LENGTH}
   *     characters
   */
  public static String personDisplayName(String firstName, String lastName, String username) {
    return truncate(composePersonName(firstName, lastName, username), MAX_ENROLLMENT_NAME_LENGTH);
  }

  private static String composePersonName(String firstName, String lastName, String username) {
    String first = normalize(firstName);
    String last = normalize(lastName);
    if (first != null && last != null) {
      return first + " " + last;
    }
    if (first != null) {
      return first;
    }
    if (last != null) {
      return last;
    }
    String user = normalize(username);
    return user != null ? user : "Administrator";
  }

  /**
   * Returns {@code personDisplayName}, or {@code personDisplayName (username)} when that name is
   * already taken by a VERIFIED enrollment on the system integration. When the suffixed form would
   * exceed {@link #MAX_ENROLLMENT_NAME_LENGTH}, the person part is shortened and the suffix is kept
   * whole.
   *
   * @param personDisplayName result of {@link #personDisplayName}
   * @param username unique admin username used only as a uniqueness suffix
   * @param personNameTaken true when a VERIFIED enrollment already uses {@code personDisplayName}
   * @return name to persist on the enrollment, at most {@link #MAX_ENROLLMENT_NAME_LENGTH}
   *     characters
   * @throws IllegalArgumentException if the suffix alone leaves no room for the person name
   */
  public static String uniqueEnrollmentName(
      String personDisplayName, String username, boolean personNameTaken) {
    String person = truncate(personDisplayName, MAX_ENROLLMENT_NAME_LENGTH);
    if (!personNameTaken) {
      return person;
    }
    String user = normalize(username);
    if (user == null || person.equalsIgnoreCase(user)) {
      return person;
    }
    String suffix = " (" + user + ")";
    int personBudget = MAX_ENROLLMENT_NAME_LENGTH - suffix.length();
    if (personBudget < 1) {
      // Unreachable with the 50-character username limit (V1 VARCHAR(50), API 3-50).
      throw new IllegalArgumentException("Username too long for an enrollment name suffix");
    }
    return truncate(person, personBudget) + suffix;
  }

  /**
   * Keeps at most {@code maxChars} UTF-16 units, cut on a grapheme boundary (no split surrogate
   * pair, accent or emoji sequence), then trims trailing whitespace left at the cut.
   *
   * <p>UTF-16 units, not code points: {@code EnrollmentUpdateRequestDto} and the Admin UI count
   * {@code String.length()}, so a generated name must pass them when sent back unchanged. A value
   * within 64 UTF-16 units is also within PostgreSQL's 64-character column.
   */
  private static String truncate(String value, int maxChars) {
    if (value.length() <= maxChars) {
      return value;
    }
    BreakIterator graphemes = BreakIterator.getCharacterInstance(Locale.ROOT);
    graphemes.setText(value);
    int end = graphemes.preceding(maxChars + 1);
    if (end <= 0) {
      // One grapheme longer than the budget: fall back to a surrogate-safe cut.
      end = Character.isLowSurrogate(value.charAt(maxChars)) ? maxChars - 1 : maxChars;
    }
    return value.substring(0, end).stripTrailing();
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
