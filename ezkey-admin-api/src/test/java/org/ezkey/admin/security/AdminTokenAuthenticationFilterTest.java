/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminTokenAuthenticationFilterTest
 * Description: Verifies authenticated requests reuse the loaded AdminToken for last-used updates
 *     (JavaMelody A1 / JM-001) and still set request auth attributes.
 */

package org.ezkey.admin.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminBrowserSessionCookieProperties;
import org.ezkey.admin.security.AdminAuthRequestAttributes.AuthSource;
import org.ezkey.admin.service.AdminTokenValidationService;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Unit tests for {@link AdminTokenAuthenticationFilter} hot-path token reuse (hygiene A1).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminTokenAuthenticationFilter — token reuse (A1)")
class AdminTokenAuthenticationFilterTest {

  private static final String PLAIN_TOKEN = "ezkey_session_token";

  @Mock private AdminTokenValidationService tokenValidationService;

  @Mock private AdminToken adminToken;

  @Mock private EzkeyAdmin admin;

  private AdminBrowserSessionCookieProperties browserSessionCookieProperties;
  private AdminTokenAuthenticationFilter filter;

  @BeforeEach
  void setUp() {
    browserSessionCookieProperties = new AdminBrowserSessionCookieProperties();
    browserSessionCookieProperties.setBrowserSessionCookieEnabled(false);
    filter =
        new AdminTokenAuthenticationFilter(tokenValidationService, browserSessionCookieProperties);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName(
      "After validate present, updateTokenLastUsed receives the loaded entity (no String"
          + " re-lookup)")
  void authenticatesWithBearer_updatesLastUsedWithLoadedEntity_andSetsRequestAttributes()
      throws Exception {
    OffsetDateTime updatedExpiresAt = OffsetDateTime.now().plusHours(2);
    when(admin.getAdminId()).thenReturn(42);
    when(admin.getAdminType()).thenReturn(AdminType.GLOBAL_ADMIN);
    when(admin.getUsername()).thenReturn("global.admin");
    when(adminToken.getAdmin()).thenReturn(admin);
    when(tokenValidationService.validateTokenWithRelations(PLAIN_TOKEN))
        .thenReturn(Optional.of(adminToken));
    when(tokenValidationService.updateTokenLastUsed(adminToken))
        .thenReturn(Optional.of(updatedExpiresAt));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth-attempts/1");
    request.addHeader("Authorization", "Bearer " + PLAIN_TOKEN);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, new MockFilterChain());

    verify(tokenValidationService).validateTokenWithRelations(PLAIN_TOKEN);
    verify(tokenValidationService).updateTokenLastUsed(adminToken);
    verify(tokenValidationService, never()).updateTokenLastUsed(anyString());

    assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    assertThat(request.getAttribute(AdminAuthRequestAttributes.AUTH_SOURCE))
        .isEqualTo(AuthSource.BEARER);
    assertThat(request.getAttribute(AdminAuthRequestAttributes.PLAIN_TOKEN)).isEqualTo(PLAIN_TOKEN);
    assertThat(request.getAttribute(AdminAuthRequestAttributes.EXPIRES_AT))
        .isEqualTo(updatedExpiresAt);
    assertThat(request.getAttribute(AdminAuthRequestAttributes.USERNAME)).isEqualTo("global.admin");
  }
}
