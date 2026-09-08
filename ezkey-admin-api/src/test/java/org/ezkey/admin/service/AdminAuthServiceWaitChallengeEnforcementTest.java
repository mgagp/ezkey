/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthServiceWaitChallengeEnforcementTest
 * Description: Verifies challenge enforcement rules for /passwordless-wait flows.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.exception.AdminAuthenticationException;
import org.ezkey.admin.exception.AdminAuthenticationExpiredException;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.authattempt.service.AuthAttemptService;
import org.ezkey.integration.domain.entity.EzkeyAdmin;
import org.ezkey.integration.domain.entity.EzkeyAdmin.AdminType;
import org.ezkey.integration.domain.repository.AdminTokenRepository;
import org.ezkey.integration.domain.repository.EzkeyAdminRepository;
import org.ezkey.security.SensitiveDataHasher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminAuthServiceWaitChallengeEnforcementTest {

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  @InjectMocks private AdminAuthService adminAuthService;

  private static final String VALID_SECRET = "valid-waiter-secret-capability-token-12345678";
  private static final String VALID_SECRET_HASH = SensitiveDataHasher.sha256Hex(VALID_SECRET);

  @Test
  @DisplayName("waitForPasswordlessAuth rejects missing challenge when attempt is challenge-backed")
  void waitRejectsMissingChallengeForChallengeBackedAttempt() {
    AuthAttempt attempt = buildAttempt(15, 8, 123456);
    when(authAttemptRepository.findById(15)).thenReturn(Optional.of(attempt));

    AdminAuthenticationException exception =
        assertThrows(
            AdminAuthenticationException.class,
            () -> adminAuthService.waitForPasswordlessAuth(15, null, VALID_SECRET));

    assertEquals("Invalid challenge code - authentication failed", exception.getMessage());
    verify(authAttemptService, never()).waitForResponse(any(), any());
  }

  @Test
  @DisplayName("waitForPasswordlessAuth rejects mismatched challenge for challenge-backed attempt")
  void waitRejectsMismatchedChallengeForChallengeBackedAttempt() {
    AuthAttempt attempt = buildAttempt(15, 8, 123456);
    when(authAttemptRepository.findById(15)).thenReturn(Optional.of(attempt));

    AdminAuthenticationException exception =
        assertThrows(
            AdminAuthenticationException.class,
            () -> adminAuthService.waitForPasswordlessAuth(15, 654321, VALID_SECRET));

    assertEquals("Invalid challenge code - authentication failed", exception.getMessage());
    verify(authAttemptService, never()).waitForResponse(any(), any());
  }

  @Test
  @DisplayName("waitForPasswordlessAuth allows null challenge when attempt has no stored challenge")
  void waitAllowsNullChallengeForNonChallengeAttempt() {
    AuthAttempt attempt = buildAttempt(16, 9, null);
    when(authAttemptRepository.findById(16)).thenReturn(Optional.of(attempt));
    when(adminRepository.findByEnrollmentId(9)).thenReturn(Optional.of(buildAdmin()));
    when(authAttemptService.waitForResponse(eq(16), any()))
        .thenReturn(buildWaitResponse(attempt, "EXPIRED"));

    assertThrows(
        AdminAuthenticationExpiredException.class,
        () -> adminAuthService.waitForPasswordlessAuth(16, null, VALID_SECRET));

    verify(authAttemptService).waitForResponse(eq(16), any());
  }

  @Test
  @DisplayName("waitForPasswordlessAuth allows matching challenge for challenge-backed attempt")
  void waitAllowsMatchingChallengeForChallengeBackedAttempt() {
    AuthAttempt attempt = buildAttempt(17, 10, 222333);
    when(authAttemptRepository.findById(17)).thenReturn(Optional.of(attempt));
    when(adminRepository.findByEnrollmentId(10)).thenReturn(Optional.of(buildAdmin()));
    when(authAttemptService.waitForResponse(eq(17), any()))
        .thenReturn(buildWaitResponse(attempt, "EXPIRED"));

    assertThrows(
        AdminAuthenticationExpiredException.class,
        () -> adminAuthService.waitForPasswordlessAuth(17, 222333, VALID_SECRET));

    verify(authAttemptService).waitForResponse(eq(17), any());
  }

  private AuthAttempt buildAttempt(Integer id, Integer enrollmentId, Integer challenge) {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(id);
    attempt.setEnrollmentId(enrollmentId);
    attempt.setAuthAttemptChallenge(challenge);
    attempt.setWaiterSecretHash(VALID_SECRET_HASH);
    attempt.setExpiresAt(OffsetDateTime.now().plusMinutes(2));
    return attempt;
  }

  private EzkeyAdmin buildAdmin() {
    EzkeyAdmin admin = new EzkeyAdmin("admin", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(42);
    return admin;
  }

  private AuthAttemptWaitResponse buildWaitResponse(AuthAttempt attempt, String status) {
    return new AuthAttemptWaitResponse(attempt, status, false, false, 1, OffsetDateTime.now());
  }
}
