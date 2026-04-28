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
    ResponseCookie cookie =
        buildCookie(
            properties.getBrowserSessionCookieName(),
            plainToken,
            maxAgeSeconds,
            true,
            properties.getBrowserSessionCookieSameSite());
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  /** Adds Set-Cookie that clears the session cookie. No-op if cookie mode is disabled. */
  public void clearSessionCookie(HttpServletResponse response) {
    if (!properties.isBrowserSessionCookieEnabled()) {
      return;
    }
    ResponseCookie sessionCookie =
        buildCookie(
            properties.getBrowserSessionCookieName(),
            "",
            0,
            true,
            properties.getBrowserSessionCookieSameSite());
    ResponseCookie csrfCookie =
        buildCookie(
            properties.getBrowserCsrfCookieName(),
            "",
            0,
            false,
            properties.getBrowserSessionCookieSameSite());
    response.addHeader(HttpHeaders.SET_COOKIE, sessionCookie.toString());
    response.addHeader(HttpHeaders.SET_COOKIE, csrfCookie.toString());
  }

  /**
   * Adds a readable CSRF cookie. The token is non-secret and is still validated against the
   * HttpOnly session cookie on unsafe requests.
   *
   * @param response servlet response
   * @param csrfToken signed CSRF token bound to the session token
   * @param expiresAt token expiry (UTC)
   */
  public void addCsrfCookie(
      HttpServletResponse response, String csrfToken, OffsetDateTime expiresAt) {
    if (!properties.isBrowserSessionCookieEnabled()) {
      return;
    }
    long maxAgeSeconds =
        Duration.between(OffsetDateTime.now(ZoneOffset.UTC), expiresAt).getSeconds();
    if (maxAgeSeconds < 0) {
      maxAgeSeconds = 0;
    }
    ResponseCookie cookie =
        buildCookie(
            properties.getBrowserCsrfCookieName(),
            csrfToken,
            maxAgeSeconds,
            false,
            properties.getBrowserSessionCookieSameSite());
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private ResponseCookie buildCookie(
      String name, String value, long maxAgeSeconds, boolean httpOnly, String sameSite) {
    return ResponseCookie.from(name, value)
        .path("/")
        .httpOnly(httpOnly)
        .secure(properties.isBrowserSessionCookieSecure())
        .sameSite(sameSite)
        .maxAge(maxAgeSeconds)
        .build();
  }
}
