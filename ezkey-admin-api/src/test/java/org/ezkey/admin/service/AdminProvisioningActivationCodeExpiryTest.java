/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminSecurityProperties;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.integration.domain.repository.TenantRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The admin record shows when a pending administrator's activation code expires (#1313). */
@DisplayName("Activation code expiry on the admin record (#1313)")
class AdminProvisioningActivationCodeExpiryTest {

  private AdminTokenRepository tokenRepository;
  private AdminProvisioningService service;

  @BeforeEach
  void setUp() {
    tokenRepository = mock(AdminTokenRepository.class);
    service =
        new AdminProvisioningService(
            mock(TenantRepository.class),
            mock(EzkeyAdminRepository.class),
            mock(IntegrationRepository.class),
            mock(EnrollmentRepository.class),
            mock(AdminRecoveryService.class),
            mock(SignatureService.class),
            mock(AdminSecurityProperties.class),
            tokenRepository);
  }

  private static EzkeyAdmin admin(AdminLifecycleStatus status) {
    EzkeyAdmin admin = new EzkeyAdmin();
    admin.setAdminId(5);
    admin.setLifecycleStatus(status);
    return admin;
  }

  @Test
  @DisplayName("Pending administrator with an unexpired code: the latest expiry is returned")
  void pendingWithCode_returnsExpiry() {
    OffsetDateTime expiry = OffsetDateTime.now().plusDays(6);
    when(tokenRepository.findLatestActiveExpiryForAdminByPurpose(
            eq(5), eq(AdminTokenPurpose.ACTIVATION), any(OffsetDateTime.class)))
        .thenReturn(Optional.of(expiry));

    assertThat(service.findActivationCodeExpiresAt(admin(AdminLifecycleStatus.PENDING_ACTIVATION)))
        .isEqualTo(expiry);
  }

  @Test
  @DisplayName("Pending administrator with no usable code: null")
  void pendingWithoutCode_returnsNull() {
    when(tokenRepository.findLatestActiveExpiryForAdminByPurpose(
            eq(5), eq(AdminTokenPurpose.ACTIVATION), any(OffsetDateTime.class)))
        .thenReturn(Optional.empty());

    assertThat(service.findActivationCodeExpiresAt(admin(AdminLifecycleStatus.PENDING_ACTIVATION)))
        .isNull();
  }

  @Test
  @DisplayName("An active administrator has no activation code to show and no query is made")
  void activeAdmin_returnsNullWithoutQuery() {
    assertThat(service.findActivationCodeExpiresAt(admin(AdminLifecycleStatus.ACTIVE))).isNull();
    verify(tokenRepository, never()).findLatestActiveExpiryForAdminByPurpose(any(), any(), any());
  }
}
