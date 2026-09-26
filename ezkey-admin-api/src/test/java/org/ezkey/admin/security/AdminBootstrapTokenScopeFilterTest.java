/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminBootstrapTokenScopeFilterTest
 */

package org.ezkey.admin.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminBootstrapTokenScopeFilter allowlist")
class AdminBootstrapTokenScopeFilterTest {

  @Test
  @DisplayName("allows activate, me, logout, onboarding, enrollment get")
  void allowlistedPaths() {
    assertTrue(AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/admin/auth/me"));
    assertTrue(AdminBootstrapTokenScopeFilter.isAllowlisted("POST", "/api/v1/admin/auth/activate"));
    assertTrue(AdminBootstrapTokenScopeFilter.isAllowlisted("POST", "/api/v1/admin/auth/logout"));
    assertTrue(AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/admins/42/onboarding"));
    assertTrue(
        AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/admins/42/onboarding/qrcode"));
    assertTrue(AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/enrollments/7"));
  }

  @Test
  @DisplayName("allows permitAll auth family so sticky BOOTSTRAP cookie does not 403 login")
  void allowlistsPermitAllAuthFamily() {
    assertTrue(AdminBootstrapTokenScopeFilter.isAllowlisted("POST", "/api/v1/admin/auth/login"));
    assertTrue(
        AdminBootstrapTokenScopeFilter.isAllowlisted(
            "POST", "/api/v1/admin/auth/passwordless-wait"));
    assertTrue(AdminBootstrapTokenScopeFilter.isAllowlisted("POST", "/api/v1/admin/auth/recover"));
    assertTrue(
        AdminBootstrapTokenScopeFilter.isAllowlisted(
            "POST", "/api/v1/admin/auth/onboarding-resume"));
  }

  @Test
  @DisplayName("denies full Admin API surfaces")
  void deniesFullApi() {
    assertFalse(AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/tenants"));
    assertFalse(AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/dashboard/overview"));
    assertFalse(AdminBootstrapTokenScopeFilter.isAllowlisted("POST", "/api/v1/integrations"));
    assertFalse(AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/admins"));
    assertFalse(AdminBootstrapTokenScopeFilter.isAllowlisted("DELETE", "/api/v1/enrollments/7"));
    assertFalse(AdminBootstrapTokenScopeFilter.isAllowlisted("GET", "/api/v1/admin/auth/login"));
    assertFalse(AdminBootstrapTokenScopeFilter.isAllowlisted("POST", "/api/v1/admin/mfa/verify"));
  }
}
