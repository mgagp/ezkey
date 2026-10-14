/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptServiceCancelTest
 * Description: Tests for cancel() method in AuthAttemptService.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
import org.ezkey.exception.auth.AuthAttemptStateConflictException;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptService Cancel Tests")
class AuthAttemptServiceCancelTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Mock private EnrollmentRepository enrollmentRepository;

  @Mock private SignatureService signatureService;

  @Mock private AuthAttemptPendingService pendingService;

  @Mock private AuthAttemptRespondService respondService;

  @Mock private AuthAttemptWaitService waitService;

  private AuthAttemptService authAttemptService;

  @BeforeEach
  void setUp() {
    EzkeyCoreProperties ezkeyCoreProperties = new EzkeyCoreProperties();
    authAttemptService =
        new AuthAttemptService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            ezkeyCoreProperties,
            pendingService,
            respondService,
            waitService);
  }

  @Test
  @DisplayName("Should cancel PENDING authentication attempt via conditional UPDATE")
  void shouldCancelPendingAuthAttempt() {
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.EXPIRED);
    when(authAttemptRepository.expireIfPendingOrRead(
            eq(authAttemptId),
            eq(List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ)),
            eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(1);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    AuthAttempt result = authAttemptService.cancel(authAttemptId);

    assertThat(result.getAuthAttemptStatus()).isEqualTo(AuthAttemptStatus.EXPIRED);
    verify(authAttemptRepository)
        .expireIfPendingOrRead(
            authAttemptId,
            List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ),
            AuthAttemptStatus.EXPIRED);
    verify(authAttemptRepository, never()).save(any());
  }

  @Test
  @DisplayName("Should cancel READ authentication attempt via conditional UPDATE")
  void shouldCancelReadAuthAttempt() {
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.EXPIRED);
    when(authAttemptRepository.expireIfPendingOrRead(
            eq(authAttemptId),
            eq(List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ)),
            eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(1);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    AuthAttempt result = authAttemptService.cancel(authAttemptId);

    assertThat(result.getAuthAttemptStatus()).isEqualTo(AuthAttemptStatus.EXPIRED);
  }

  @Test
  @DisplayName("Should throw when auth attempt not found (conditional UPDATE matched 0 rows)")
  void shouldThrowExceptionWhenNotFound() {
    Integer authAttemptId = 999;
    when(authAttemptRepository.expireIfPendingOrRead(
            eq(authAttemptId),
            eq(List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ)),
            eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(0);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Authentication attempt")
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("Approve race: conditional UPDATE 0 rows + ACCEPTED → state conflict (#833 m1)")
  void shouldThrowWhenApproveWonRace() {
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.ACCEPTED);
    when(authAttemptRepository.expireIfPendingOrRead(
            eq(authAttemptId),
            eq(List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ)),
            eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(0);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("Cannot cancel")
        .hasMessageContaining("ACCEPTED");
  }

  @Test
  @DisplayName("Should throw exception when attempting to cancel REJECTED attempt")
  void shouldThrowExceptionWhenCancellingRejectedAttempt() {
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.REJECTED);
    when(authAttemptRepository.expireIfPendingOrRead(
            eq(authAttemptId),
            eq(List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ)),
            eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(0);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("REJECTED");
  }

  @Test
  @DisplayName("Should throw exception when attempting to cancel INVALID attempt")
  void shouldThrowExceptionWhenCancellingInvalidAttempt() {
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.INVALID);
    when(authAttemptRepository.expireIfPendingOrRead(
            eq(authAttemptId),
            eq(List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ)),
            eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(0);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("INVALID");
  }

  @Test
  @DisplayName("Should throw exception when attempting to cancel EXPIRED attempt")
  void shouldThrowExceptionWhenCancellingExpiredAttempt() {
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.EXPIRED);
    when(authAttemptRepository.expireIfPendingOrRead(
            eq(authAttemptId),
            eq(List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ)),
            eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(0);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(AuthAttemptStateConflictException.class)
        .hasMessageContaining("EXPIRED");
  }

  private AuthAttempt createAuthAttempt(Integer id, AuthAttemptStatus status) {
    AuthAttempt authAttempt = new AuthAttempt();
    authAttempt.setAuthAttemptId(id);
    authAttempt.setEnrollmentId(456);
    authAttempt.setAuthAttemptStatus(status);
    authAttempt.setCreatedAt(OffsetDateTime.now());
    authAttempt.setExpiresAt(OffsetDateTime.now().plusSeconds(120));
    return authAttempt;
  }
}
