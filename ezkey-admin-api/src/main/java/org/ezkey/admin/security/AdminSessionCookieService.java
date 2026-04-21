/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Service: AdminSessionCookieService
 * Description: Sets and clears the browser HttpOnly admin session cookie.
 */

package org.ezkey.admin.security;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Writes and clears the administrator session cookie when {@link
 * AdminBrowserSessionCookieProperties#isBrowserSessionCookieEnabled()} is true.
 */
@Component
public class AdminSessionCookieService {

  private final AdminBrowserSessionCookieProperties properties;

  public AdminSessionCookieService(AdminBrowserSessionCookieProperties properties) {
    this.properties = properties;
  }

  /**
   * Adds Set-Cookie for an active session. No-op if cookie mode is disabled.
   *
   * @param response servlet response
   * @param plainToken opaque bearer value (same as stored hashed in DB)
   * @param expiresAt token expiry (UTC)
   */
  public void addSessionCookie(
      HttpServletResponse response, String plainToken, OffsetDateTime expiresAt) {
    if (!properties.isBrowserSessionCookieEnabled()) {
      return;
    }
    long maxAgeSeconds =
        Duration.between(OffsetDateTime.now(ZoneOffset.UTC), expiresAt).getSeconds();
    if (maxAgeSeconds < 0) {
      maxAgeSeconds = 0;
    }
    ResponseCookie cookie = buildCookie(plainToken, maxAgeSeconds);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  /** Adds Set-Cookie that clears the session cookie. No-op if cookie mode is disabled. */
  public void clearSessionCookie(HttpServletResponse response) {
    if (!properties.isBrowserSessionCookieEnabled()) {
      return;
    }
    ResponseCookie cookie = buildCookie("", 0);
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
    return ResponseCookie.from(properties.getBrowserSessionCookieName(), value)
        .path("/")
        .httpOnly(true)
        .secure(properties.isBrowserSessionCookieSecure())
        .sameSite("Lax")
        .maxAge(maxAgeSeconds)
        .build();
  }
}
