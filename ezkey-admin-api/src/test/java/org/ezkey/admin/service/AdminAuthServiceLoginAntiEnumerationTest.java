/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthServiceLoginAntiEnumerationTest
 * Description: Verifies pre-authentication login failures use a generic HTTP 401 message (SEC-006).
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
import org.ezkey.admin.exception.AdminAuthenticationException;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.entity.Tenant;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceLoginAntiEnumerationTest {

  private static final String GENERIC_LOGIN_FAILURE = "Invalid username or password";

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  @InjectMocks private AdminAuthService adminAuthService;

  @Test
  @DisplayName("authenticate returns generic 401 message for unknown username")
  void authenticateRejectsUnknownUsernameWithGenericMessage() {
    when(adminRepository.findByUsernameWithEnrollment("ghost")).thenReturn(Optional.empty());

    AdminAuthenticationException exception =
        assertThrows(
            AdminAuthenticationException.class,
            () -> adminAuthService.authenticate(new AdminLoginRequestDto("ghost", false, false)));

    assertEquals(GENERIC_LOGIN_FAILURE, exception.getMessage());
    verify(authAttemptTxHelper, never()).createAuthAttempt(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("authenticate returns generic 401 message for deactivated account")
  void authenticateRejectsInactiveAccountWithGenericMessage() {
    EzkeyAdmin admin = activeAdmin("inactive.admin");
    admin.setActive(false);
    when(adminRepository.findByUsernameWithEnrollment("inactive.admin"))
        .thenReturn(Optional.of(admin));

    AdminAuthenticationException exception =
        assertThrows(
            AdminAuthenticationException.class,
            () ->
                adminAuthService.authenticate(
                    new AdminLoginRequestDto("inactive.admin", false, false)));

    assertEquals(GENERIC_LOGIN_FAILURE, exception.getMessage());
    verify(authAttemptTxHelper, never()).createAuthAttempt(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("authenticate returns generic 401 message for tenant with inactive parent")
  void authenticateRejectsInactiveTenantWithGenericMessage() {
    EzkeyAdmin admin = activeAdmin("tenant.blocked");
    Tenant tenant = new Tenant();
    tenant.setActive(false);
    admin.setTenant(tenant);
    when(adminRepository.findByUsernameWithEnrollment("tenant.blocked"))
        .thenReturn(Optional.of(admin));

    AdminAuthenticationException exception =
        assertThrows(
            AdminAuthenticationException.class,
            () ->
                adminAuthService.authenticate(
                    new AdminLoginRequestDto("tenant.blocked", false, false)));

    assertEquals(GENERIC_LOGIN_FAILURE, exception.getMessage());
    verify(authAttemptTxHelper, never()).createAuthAttempt(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("authenticate returns generic 401 message when no enrollment is bound")
  void authenticateRejectsMissingEnrollmentWithGenericMessage() {
    EzkeyAdmin admin = activeAdmin("no.device");
    admin.setEnrollment(null);
    when(adminRepository.findByUsernameWithEnrollment("no.device")).thenReturn(Optional.of(admin));

    AdminAuthenticationException exception =
        assertThrows(
            AdminAuthenticationException.class,
            () ->
                adminAuthService.authenticate(new AdminLoginRequestDto("no.device", false, false)));

    assertEquals(GENERIC_LOGIN_FAILURE, exception.getMessage());
    verify(authAttemptTxHelper, never()).createAuthAttempt(org.mockito.ArgumentMatchers.any());
  }

  @Test
  @DisplayName("authenticate returns generic 401 message when enrollment has no device public key")
  void authenticateRejectsUnboundEnrollmentWithGenericMessage() {
    EzkeyAdmin admin = activeAdmin("unbound.device");
    Enrollment enrollment = new Enrollment();
    enrollment.setDevicePublicKey(null);
    admin.setEnrollment(enrollment);
    when(adminRepository.findByUsernameWithEnrollment("unbound.device"))
        .thenReturn(Optional.of(admin));

    AdminAuthenticationException exception =
        assertThrows(
            AdminAuthenticationException.class,
            () ->
                adminAuthService.authenticate(
                    new AdminLoginRequestDto("unbound.device", false, false)));

    assertEquals(GENERIC_LOGIN_FAILURE, exception.getMessage());
    verify(authAttemptTxHelper, never()).createAuthAttempt(org.mockito.ArgumentMatchers.any());
  }

  private static EzkeyAdmin activeAdmin(String username) {
    EzkeyAdmin admin = new EzkeyAdmin(username, AdminType.TENANT_ADMIN);
    admin.setActive(true);
    Enrollment enrollment = new Enrollment();
    enrollment.setDevicePublicKey("device-public-key");
    admin.setEnrollment(enrollment);
    return admin;
  }
}
