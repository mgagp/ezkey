/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Helper: LinkEntryMarker
 * Description: Stateless entry=link marker for tenant-link login layout (no cookie).
 */

package org.ezkey.demo.acme.web;

/**
 * Stateless layout marker for the Play access-link path.
 *
 * <p>Value {@code link} on query param or form field {@code entry} selects the tenant-link login
 * layout when no slot remains in session. It never carries a code or slot id and grants nothing.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class LinkEntryMarker {

  /** Query / form parameter name. */
  public static final String PARAM = "entry";

  /** Marker value meaning the browser arrived via a tenant access link. */
  public static final String VALUE = "link";

  private LinkEntryMarker() {}

  /**
   * Whether the request carries the tenant-link layout marker.
   *
   * @param entry raw {@code entry} query or form value (may be null)
   * @return true when equal to {@link #VALUE}
   */
  public static boolean isLink(String entry) {
    return VALUE.equals(entry);
  }

  /**
   * Appends {@code entry=link} to a login redirect path that already has a query string, or adds
   * {@code ?entry=link} when it does not.
   *
   * @param loginPath path such as {@code /login?error=expired} or {@code /login}
   * @return path with the marker
   */
  public static String withMarker(String loginPath) {
    if (loginPath == null || loginPath.isBlank()) {
      return "/login?" + PARAM + "=" + VALUE;
    }
    if (loginPath.contains(PARAM + "=" + VALUE)) {
      return loginPath;
    }
    return loginPath.contains("?")
        ? loginPath + "&" + PARAM + "=" + VALUE
        : loginPath + "?" + PARAM + "=" + VALUE;
  }
}
