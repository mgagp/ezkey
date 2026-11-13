/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthServiceEvaluatorTempSupersedeTest
 * Description: SESSION mint supersedes EVALUATOR_TEMP even when rotation-on-login is off.
 */

package org.ezkey.admin.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.integration.domain.AdminTokenPurpose;
import org.ezkey.integration.domain.entity.AdminToken;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminLifecycleStatus;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Ensures temporary evaluator console tokens die when an opaque SESSION is minted.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminAuthService — EVALUATOR_TEMP supersede on SESSION mint")
class AdminAuthServiceEvaluatorTempSupersedeTest {

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  private AdminAuthService adminAuthService;

  @BeforeEach
  void setUp() {
    adminAuthService =
        new AdminAuthService(
            adminRepository,
            tokenRepository,
            rotationProperties,
            authAttemptService,
            authAttemptRepository,
            authAttemptTxHelper);
  }

  @Test
  @DisplayName(
      "authenticateAfterMfa deactivates EVALUATOR_TEMP even when rotation-on-login is disabled")
  void authenticateAfterMfa_supersedesEvaluatorTemp_whenRotationDisabled() {
    EzkeyAdmin admin = new EzkeyAdmin("bound.admin", AdminType.TENANT_ADMIN);
    admin.setAdminId(77);
    admin.setActive(true);
    admin.setLifecycleStatus(AdminLifecycleStatus.ACTIVE);

    when(rotationProperties.isRotationOnLoginEnabled()).thenReturn(false);
    when(rotationProperties.getExpirationHours()).thenReturn(2);
    when(tokenRepository.deactivateTokensForAdminByPurpose(77, AdminTokenPurpose.EVALUATOR_TEMP))
        .thenReturn(1);
    when(tokenRepository.save(any(AdminToken.class))).thenAnswer(inv -> inv.getArgument(0));
    when(adminRepository.save(any(EzkeyAdmin.class))).thenAnswer(inv -> inv.getArgument(0));

    AdminLoginResponseDto response = adminAuthService.authenticateAfterMfa(admin);

    assertThat(response.token()).isNotBlank();
    verify(tokenRepository, never()).deactivateAllTokensForAdmin(any());
    verify(tokenRepository).deactivateTokensForAdminByPurpose(77, AdminTokenPurpose.EVALUATOR_TEMP);

    ArgumentCaptor<AdminToken> captor = ArgumentCaptor.forClass(AdminToken.class);
    verify(tokenRepository).save(captor.capture());
    assertThat(captor.getValue().getTokenPurpose()).isEqualTo(AdminTokenPurpose.SESSION);
  }
}
