/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import org.ezkey.admin.config.AdminApiKeyAuthAttemptsProperties;
import org.ezkey.exception.AdminApiProblemCatalog;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link ApiKeyAuthAttemptsAcceptanceFilter}.
 *
 * @since 2026
 */
class ApiKeyAuthAttemptsAcceptanceFilterTest {

  private AdminApiKeyAuthAttemptsProperties properties;
  private ApiKeyAuthAttemptsAcceptanceFilter filter;

  @BeforeEach
  void setUp() {
    properties = new AdminApiKeyAuthAttemptsProperties();
    filter = new ApiKeyAuthAttemptsAcceptanceFilter(properties);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void rejectsApiKeyWhenDisabledByDefault() throws Exception {
    setApiKeyAuthentication();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth-attempts");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(403, response.getStatus());
    assertTrue(
        response.getContentType() != null
            && response.getContentType().contains("application/problem+json"));
    String body = response.getContentAsString();
    assertTrue(body.contains(AdminApiProblemCatalog.TYPE_API_KEY_AUTH_ATTEMPTS_DISABLED));
    assertTrue(body.contains(AdminApiProblemCatalog.TITLE_API_KEY_AUTH_ATTEMPTS_DISABLED));
  }

  @Test
  void allowsApiKeyWhenOptInEnabled() throws Exception {
    properties.setApiKeyAuthAttemptsEnabled(true);
    setApiKeyAuthentication();
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth-attempts");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(200, response.getStatus());
  }

  @Test
  void doesNotAffectBearerAdminAuthentication() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "admin",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/integrations");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(200, response.getStatus());
  }

  @Test
  void passesThroughWhenUnauthenticated() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/api/v1/public/instance-info");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    assertEquals(200, response.getStatus());
  }

  private static void setApiKeyAuthentication() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "integration",
                null,
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_API_KEY"))));
  }
}
