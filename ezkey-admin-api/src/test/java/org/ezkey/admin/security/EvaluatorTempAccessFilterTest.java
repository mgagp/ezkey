/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EvaluatorTempAccessFilterTest
 * Description: Deny-list + navigable pass-through + self-only QR for EVALUATOR_TEMP.
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Craft steer coverage: navigable ≠ bridled Path B; IDOR QR; deny GLOBAL / SESSION mint /
 * bootstrap.
 *
 * @since 2026
 */
class EvaluatorTempAccessFilterTest {

  private EvaluatorTempAccessFilter filter;

  @BeforeEach
  void setUp() {
    filter = new EvaluatorTempAccessFilter();
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("TEMP allows navigable dashboard (not bridled finish-enrollment-only)")
  void temp_allowsDashboard() throws Exception {
    authenticateTemp(42);
    MockHttpServletResponse response = filterGet("/api/v1/dashboard/summary");
    assertEquals(200, response.getStatus());
  }

  @Test
  @DisplayName("TEMP allows tenant-scoped list reads (navigable lab CRUD surface)")
  void temp_allowsEnrollmentList() throws Exception {
    authenticateTemp(42);
    MockHttpServletResponse response = filterGet("/api/v1/enrollments");
    assertEquals(200, response.getStatus());
  }

  @Test
  @DisplayName("TEMP denies GLOBAL tenants surface")
  void temp_deniesTenants() throws Exception {
    authenticateTemp(42);
    MockHttpServletResponse response = filterGet("/api/v1/tenants");
    assertEquals(403, response.getStatus());
  }

  @Test
  @DisplayName("TEMP denies passwordless login (mint SESSION without bind)")
  void temp_deniesPasswordlessLogin() throws Exception {
    authenticateTemp(42);
    MockHttpServletResponse response = filterPost("/api/v1/admin/auth/login");
    assertEquals(403, response.getStatus());
  }

  @Test
  @DisplayName("TEMP denies peer tenant-admin bootstrap")
  void temp_deniesTenantAdminBootstrap() throws Exception {
    authenticateTemp(42);
    MockHttpServletResponse response = filterPost("/api/v1/admins/tenant");
    assertEquals(403, response.getStatus());
  }

  @Test
  @DisplayName("TEMP allows self onboarding QR")
  void temp_allowsSelfOnboardingQr() throws Exception {
    authenticateTemp(42);
    MockHttpServletResponse response = filterGet("/api/v1/admins/42/onboarding/qrcode");
    assertEquals(200, response.getStatus());
  }

  @Test
  @DisplayName("TEMP denies cross-admin onboarding QR (IDOR)")
  void temp_deniesOtherAdminOnboardingQr() throws Exception {
    authenticateTemp(42);
    MockHttpServletResponse response = filterGet("/api/v1/admins/99/onboarding/qrcode");
    assertEquals(403, response.getStatus());
  }

  @Test
  @DisplayName("SESSION purpose is not constrained by TEMP deny-list")
  void session_notConstrained() throws Exception {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(42, AdminType.TENANT_ADMIN, 3, null, AdminTokenPurpose.SESSION),
                null));
    MockHttpServletResponse response = filterGet("/api/v1/tenants");
    assertEquals(200, response.getStatus());
  }

  private void authenticateTemp(int adminId) {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AdminPrincipal(
                    adminId, AdminType.TENANT_ADMIN, 3, null, AdminTokenPurpose.EVALUATOR_TEMP),
                null));
  }

  private MockHttpServletResponse filterGet(String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }

  private MockHttpServletResponse filterPost(String path) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRequestURI(path);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilter(request, response, new MockFilterChain());
    return response;
  }
}
