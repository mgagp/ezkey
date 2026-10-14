/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Helper: SessionHelpers
 * Description: Shared HTTP session invalidate-and-recreate for credential-source switches.
 */

package org.ezkey.demo.acme.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Shared session helpers for the ACME demo.
 *
 * @author Ezkey contributors
 * @since 2026
 */
public final class SessionHelpers {

  private SessionHelpers() {}

  /**
   * Invalidates any existing session and returns a new one.
   *
   * <p>Used when switching credential source (access-code slot ↔ pasted API keys) so the previous
   * slot or paste cannot linger on the same session id.
   *
   * @param request the current HTTP request
   * @return a newly created session
   */
  public static HttpSession invalidateAndCreate(HttpServletRequest request) {
    HttpSession existing = request.getSession(false);
    if (existing != null) {
      existing.invalidate();
    }
    return request.getSession(true);
  }
}
