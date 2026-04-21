/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthServicePendingActivationTest
 * Description: Verifies passwordless login rejection for pending admin activation state.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.request.AdminLoginRequestDto;
import org.ezkey.admin.exception.AdminAccountInactiveException;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthServicePendingActivationTest {

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  @InjectMocks private AdminAuthService adminAuthService;

  @Test
  @DisplayName("authenticate rejects admins whose activation is still pending")
  void authenticateRejectsPendingActivationAdmin() {
    EzkeyAdmin admin = new EzkeyAdmin("pending.admin", AdminType.TENANT_ADMIN);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.PENDING_ACTIVATION);

    when(adminRepository.findByUsernameWithEnrollment("pending.admin"))
        .thenReturn(Optional.of(admin));

    AdminAccountInactiveException exception =
        assertThrows(
            AdminAccountInactiveException.class,
            () ->
                adminAuthService.authenticate(
                    new AdminLoginRequestDto("pending.admin", false, false)));

    assertEquals(
        "Account activation is still pending. Complete first-time setup before logging in.",
        exception.getMessage());
    verify(adminRepository).findByUsernameWithEnrollment("pending.admin");
    verify(authAttemptTxHelper, never()).createAuthAttempt(org.mockito.ArgumentMatchers.any());
  }
}
