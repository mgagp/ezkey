/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

/** Verifies browser session and CSRF cookie attributes. */
class AdminSessionCookieServiceTest {

  @Test
  void sessionAndCsrfCookiesUseStrictSameSiteByDefault() {
    AdminBrowserSessionCookieProperties properties = new AdminBrowserSessionCookieProperties();
    properties.setBrowserSessionCookieEnabled(true);
    AdminSessionCookieService service = new AdminSessionCookieService(properties);
    MockHttpServletResponse response = new MockHttpServletResponse();

    service.addSessionCookie(response, "session-token", OffsetDateTime.now().plusHours(2));
    service.addCsrfCookie(response, "csrf-token", OffsetDateTime.now().plusHours(2));

    String setCookie = String.join("\n", response.getHeaders(HttpHeaders.SET_COOKIE));
    assertTrue(setCookie.contains("EZKEY_ADMIN_SESSION=session-token"));
    assertTrue(setCookie.contains("EZKEY_ADMIN_CSRF=csrf-token"));
    assertTrue(setCookie.contains("SameSite=Strict"));
    assertTrue(setCookie.contains("HttpOnly"));
    assertTrue(setCookie.toLowerCase().contains("secure"));
  }
}
