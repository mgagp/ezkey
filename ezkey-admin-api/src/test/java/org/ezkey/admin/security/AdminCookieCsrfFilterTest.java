/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 *
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.security.AdminAuthRequestAttributes.AuthSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Verifies CSRF enforcement for cookie-authenticated unsafe Admin API requests. */
class AdminCookieCsrfFilterTest {

  private AdminBrowserSessionCookieProperties properties;
  private AdminCookieCsrfFilter filter;

  @BeforeEach
  void setUp() {
    properties = new AdminBrowserSessionCookieProperties();
    properties.setBrowserSessionCookieEnabled(true);
    filter = new AdminCookieCsrfFilter(properties, new AdminCsrfTokenService());
  }

  @Test
  void unsafeCookieAuthenticatedRequestRequiresValidCsrfHeader() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/admins/1/activate");
    request.setAttribute(AdminAuthRequestAttributes.AUTH_SOURCE, AuthSource.COOKIE);
    request.setAttribute(AdminAuthRequestAttributes.PLAIN_TOKEN, "session-token");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(403, response.getStatus());
  }

  @Test
  void unsafeCookieAuthenticatedRequestWithValidCsrfContinues() throws Exception {
    AdminCsrfTokenService service = new AdminCsrfTokenService();
    filter = new AdminCookieCsrfFilter(properties, service);
    String csrfToken = service.createToken("session-token");
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/admins/1/activate");
    request.setAttribute(AdminAuthRequestAttributes.AUTH_SOURCE, AuthSource.COOKIE);
    request.setAttribute(AdminAuthRequestAttributes.PLAIN_TOKEN, "session-token");
    request.addHeader(properties.getBrowserCsrfHeaderName(), csrfToken);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(200, response.getStatus());
    assertNull(response.getErrorMessage());
  }

  @Test
  void unsafeBearerAuthenticatedRequestDoesNotRequireCsrf() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/api/v1/admins/1/activate");
    request.setAttribute(AdminAuthRequestAttributes.AUTH_SOURCE, AuthSource.BEARER);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(200, response.getStatus());
  }
}
