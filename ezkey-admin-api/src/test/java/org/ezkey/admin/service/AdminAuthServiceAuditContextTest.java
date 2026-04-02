/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.AdminAuthAuditContext;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceAuditContextTest {

  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  @InjectMocks private AdminAuthService adminAuthService;

  @Test
  void findAuditContextForAuthAttempt_returnsContextWhenLinked() {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setEnrollmentId(5);
    when(authAttemptRepository.findById(10)).thenReturn(Optional.of(attempt));

    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setUsername("u1");
    admin.setAdminId(99);
    Tenant tenant = new Tenant();
    tenant.setTenantId(3);
    admin.setTenant(tenant);
    when(adminRepository.findByEnrollmentId(5)).thenReturn(Optional.of(admin));

    Optional<AdminAuthAuditContext> ctx = adminAuthService.findAuditContextForAuthAttempt(10);

    assertThat(ctx).isPresent();
    assertThat(ctx.get().username()).isEqualTo("u1");
    assertThat(ctx.get().adminId()).isEqualTo(99);
    assertThat(ctx.get().tenantId()).isEqualTo(3);
  }

  @Test
  void findAuditContextForAuthAttempt_emptyWhenNoAttempt() {
    when(authAttemptRepository.findById(1)).thenReturn(Optional.empty());
    assertThat(adminAuthService.findAuditContextForAuthAttempt(1)).isEmpty();
  }
}
