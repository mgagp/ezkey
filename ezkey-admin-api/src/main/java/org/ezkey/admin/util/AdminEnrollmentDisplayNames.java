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

/**
 * Builds the authenticator-facing {@code enrollmentName} for Global and Tenant Admin MFA.
 *
 * <p>The mobile app treats {@code enrollmentName} as the person hero. Role, username, and the word
 * {@code MFA} belong in Admin UI composition ({@code adminType}), not in this string.
 *
 * <p>If two enrollments on one phone cannot be told apart, do not encode the role here. Unpark
 * {@code I-2026-09-15-mobile-admin-enrollment-account-label} and add a dedicated bind field.
 *
 * @since 2026
 */
public final class AdminEnrollmentDisplayNames {

  private AdminEnrollmentDisplayNames() {
    // Utility class - no instantiation
  }

  /**
   * Person-first display name: {@code first last}, else whichever part exists, else username.
   *
   * @param firstName admin first name, may be blank
   * @param lastName admin last name, may be blank
   * @param username unique admin username, used only when no person name exists
   * @return trimmed non-blank display name
   */
  public static String personDisplayName(String firstName, String lastName, String username) {
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
   * already taken by a VERIFIED enrollment on the system integration.
   *
   * @param personDisplayName result of {@link #personDisplayName}
   * @param username unique admin username used only as a uniqueness suffix
   * @param personNameTaken true when a VERIFIED enrollment already uses {@code personDisplayName}
   * @return name to persist on the enrollment
   */
  public static String uniqueEnrollmentName(
      String personDisplayName, String username, boolean personNameTaken) {
    if (!personNameTaken) {
      return personDisplayName;
    }
    String user = normalize(username);
    if (user == null || personDisplayName.equalsIgnoreCase(user)) {
      return personDisplayName;
    }
    return personDisplayName + " (" + user + ")";
  }

  private static String normalize(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
