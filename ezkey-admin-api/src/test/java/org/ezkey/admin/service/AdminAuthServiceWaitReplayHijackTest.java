/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AdminAuthServiceWaitReplayHijackTest
 * Description: Verifies replay prevention, waiter secret enforcement, and CAS session consumption on /passwordless-wait.
 */

package org.ezkey.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.admin.config.AdminTokenRotationProperties;
import org.ezkey.admin.dto.response.AdminLoginResponseDto;
import org.ezkey.admin.exception.AdminAuthenticationException;
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

/**
 * Verifies mitigation of S3 replay and session hijacking on {@code /passwordless-wait} via waiter
 * secret capability and atomic CAS single-issuance.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AdminAuthServiceWaitReplayHijackTest {

  private static final String VALID_SECRET = "valid-waiter-secret-capability-token-32bytes";
  private static final String VALID_SECRET_HASH = SensitiveDataHasher.sha256Hex(VALID_SECRET);

  @Mock private EzkeyAdminRepository adminRepository;
  @Mock private AdminTokenRepository tokenRepository;
  @Mock private AdminTokenRotationProperties rotationProperties;
  @Mock private AuthAttemptService authAttemptService;
  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private AdminAuthAttemptTxHelper authAttemptTxHelper;

  @InjectMocks private AdminAuthService adminAuthService;

  @Test
  @DisplayName("Replay wait after ACCEPTED is rejected with 401 and does not rotate original token")
  void waitReplayAfterAcceptedFailsWith401AndPreservesInitialSession() {
    Integer attemptId = 100;
    Integer enrollmentId = 42;
    Integer adminId = 1;

    AuthAttempt attempt = buildAttempt(attemptId, enrollmentId, null);
    when(authAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    EzkeyAdmin admin = buildAdmin(adminId);
    when(adminRepository.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(admin));

    when(rotationProperties.isRotationOnLoginEnabled()).thenReturn(true);
    when(rotationProperties.getExpirationHours()).thenReturn(2);

    AuthAttemptWaitResponse acceptedResponse = buildWaitResponse(attempt, "ACCEPTED");
    when(authAttemptService.waitForResponse(eq(attemptId), any())).thenReturn(acceptedResponse);

    // Atomic CAS: first call succeeds (rows updated = 1)
    when(authAttemptRepository.markSessionIssued(eq(attemptId), any())).thenReturn(1);

    // Call 1: Legitimate client waits with valid waiter secret -> success
    AdminLoginResponseDto session1 =
        adminAuthService.waitForPasswordlessAuth(attemptId, null, VALID_SECRET);
    assertNotNull(session1.token());

    // Mark attempt as consumed in local entity to simulate pre-check or CAS reject
    attempt.setSessionIssuedAt(OffsetDateTime.now());

    // Call 2: Replay attack (even with same secret) -> rejected immediately by CAS pre-check
    AdminAuthenticationException ex =
        assertThrows(
            AdminAuthenticationException.class,
            () -> adminAuthService.waitForPasswordlessAuth(attemptId, null, VALID_SECRET));
    assertEquals("Authentication failed", ex.getMessage());

    // Crucial: Token rotation and token generation were called only ONCE (for session 1)
    verify(tokenRepository, times(1)).deactivateAllTokensForAdmin(adminId);
    verify(tokenRepository, times(1)).save(any());
  }

  @Test
  @DisplayName("waitForPasswordlessAuth rejects null or blank waiter secret")
  void waitFailsWhenWaiterSecretMissing() {
    Integer attemptId = 101;
    AuthAttempt attempt = buildAttempt(attemptId, 42, null);
    when(authAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    assertThrows(
        AdminAuthenticationException.class,
        () -> adminAuthService.waitForPasswordlessAuth(attemptId, null, null));

    assertThrows(
        AdminAuthenticationException.class,
        () -> adminAuthService.waitForPasswordlessAuth(attemptId, null, ""));

    assertThrows(
        AdminAuthenticationException.class,
        () -> adminAuthService.waitForPasswordlessAuth(attemptId, null, "   "));

    verify(authAttemptService, never()).waitForResponse(any(), any());
  }

  @Test
  @DisplayName("waitForPasswordlessAuth rejects mismatched waiter secret")
  void waitFailsWhenWaiterSecretMismatched() {
    Integer attemptId = 102;
    AuthAttempt attempt = buildAttempt(attemptId, 42, null);
    when(authAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    AdminAuthenticationException ex =
        assertThrows(
            AdminAuthenticationException.class,
            () -> adminAuthService.waitForPasswordlessAuth(attemptId, null, "tampered-secret"));

    assertEquals("Authentication failed", ex.getMessage());
    verify(authAttemptService, never()).waitForResponse(any(), any());
  }

  @Test
  @DisplayName("waitForPasswordlessAuth succeeds when both waiter secret and challenge match")
  void waitSucceedsWithValidSecretAndChallenge() {
    Integer attemptId = 103;
    Integer enrollmentId = 42;
    Integer challenge = 77;
    Integer adminId = 1;

    AuthAttempt attempt = buildAttempt(attemptId, enrollmentId, challenge);
    when(authAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    EzkeyAdmin admin = buildAdmin(adminId);
    when(adminRepository.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(admin));

    when(rotationProperties.isRotationOnLoginEnabled()).thenReturn(true);
    when(rotationProperties.getExpirationHours()).thenReturn(2);

    AuthAttemptWaitResponse acceptedResponse = buildWaitResponse(attempt, "ACCEPTED");
    when(authAttemptService.waitForResponse(eq(attemptId), any())).thenReturn(acceptedResponse);
    when(authAttemptRepository.markSessionIssued(eq(attemptId), any())).thenReturn(1);

    AdminLoginResponseDto response =
        adminAuthService.waitForPasswordlessAuth(attemptId, challenge, VALID_SECRET);

    assertNotNull(response.token());
    verify(authAttemptRepository).markSessionIssued(eq(attemptId), any());
    verify(tokenRepository, times(1)).deactivateAllTokensForAdmin(adminId);
  }

  @Test
  @DisplayName("Concurrent wait collision where CAS returns 0 rejects without rotating tokens")
  void concurrentWaitCASCollisionRejectsSecondIssuerWithoutRotation() {
    Integer attemptId = 104;
    Integer enrollmentId = 42;
    Integer adminId = 1;

    AuthAttempt attempt = buildAttempt(attemptId, enrollmentId, null);
    when(authAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    EzkeyAdmin admin = buildAdmin(adminId);
    when(adminRepository.findByEnrollmentId(enrollmentId)).thenReturn(Optional.of(admin));

    AuthAttemptWaitResponse acceptedResponse = buildWaitResponse(attempt, "ACCEPTED");
    when(authAttemptService.waitForResponse(eq(attemptId), any())).thenReturn(acceptedResponse);

    // Another thread won the race and updated the row first -> rows updated = 0
    when(authAttemptRepository.markSessionIssued(eq(attemptId), any())).thenReturn(0);

    AdminAuthenticationException ex =
        assertThrows(
            AdminAuthenticationException.class,
            () -> adminAuthService.waitForPasswordlessAuth(attemptId, null, VALID_SECRET));

    assertEquals("Authentication failed", ex.getMessage());
    verify(tokenRepository, never()).deactivateAllTokensForAdmin(any());
    verify(tokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("Attempt consumed by blocking login cannot be waited on subsequently")
  void blockingLoginConsumesAttemptPreventingSubsequentWait() {
    Integer attemptId = 105;
    AuthAttempt attempt = buildAttempt(attemptId, 42, null);
    attempt.setSessionIssuedAt(OffsetDateTime.now()); // Already marked issued by blocking login
    when(authAttemptRepository.findById(attemptId)).thenReturn(Optional.of(attempt));

    AdminAuthenticationException ex =
        assertThrows(
            AdminAuthenticationException.class,
            () -> adminAuthService.waitForPasswordlessAuth(attemptId, null, VALID_SECRET));

    assertEquals("Authentication failed", ex.getMessage());
    verify(authAttemptService, never()).waitForResponse(any(), any());
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

  private EzkeyAdmin buildAdmin(Integer adminId) {
    EzkeyAdmin admin = new EzkeyAdmin("admin.docker", AdminType.GLOBAL_ADMIN);
    admin.setAdminId(adminId);
    return admin;
  }

  private AuthAttemptWaitResponse buildWaitResponse(AuthAttempt attempt, String status) {
    return new AuthAttemptWaitResponse(attempt, status, false, false, 1, OffsetDateTime.now());
  }
}
