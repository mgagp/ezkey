/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.ResourceNotFoundException;
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
  @DisplayName("Should cancel PENDING authentication attempt")
  void shouldCancelPendingAuthAttempt() {
    // Arrange
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.PENDING);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    AuthAttempt result = authAttemptService.cancel(authAttemptId);

    // Assert
    assertThat(result.getAuthAttemptStatus()).isEqualTo(AuthAttemptStatus.EXPIRED);
    verify(authAttemptRepository).findById(authAttemptId);
    verify(authAttemptRepository).save(authAttempt);
  }

  @Test
  @DisplayName("Should cancel READ authentication attempt")
  void shouldCancelReadAuthAttempt() {
    // Arrange
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.READ);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    // Act
    AuthAttempt result = authAttemptService.cancel(authAttemptId);

    // Assert
    assertThat(result.getAuthAttemptStatus()).isEqualTo(AuthAttemptStatus.EXPIRED);
    verify(authAttemptRepository).findById(authAttemptId);
    verify(authAttemptRepository).save(authAttempt);
  }

  @Test
  @DisplayName("Should throw exception when auth attempt not found")
  void shouldThrowExceptionWhenNotFound() {
    // Arrange
    Integer authAttemptId = 999;
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.empty());

    // Act & Assert
    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("Authentication attempt")
        .hasMessageContaining("999");
  }

  @Test
  @DisplayName("Should throw exception when attempting to cancel ACCEPTED attempt")
  void shouldThrowExceptionWhenCancellingAcceptedAttempt() {
    // Arrange
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.ACCEPTED);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    // Act & Assert
    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot cancel")
        .hasMessageContaining("ACCEPTED");
  }

  @Test
  @DisplayName("Should throw exception when attempting to cancel REJECTED attempt")
  void shouldThrowExceptionWhenCancellingRejectedAttempt() {
    // Arrange
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.REJECTED);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    // Act & Assert
    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot cancel")
        .hasMessageContaining("REJECTED");
  }

  @Test
  @DisplayName("Should throw exception when attempting to cancel INVALID attempt")
  void shouldThrowExceptionWhenCancellingInvalidAttempt() {
    // Arrange
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.INVALID);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    // Act & Assert
    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot cancel")
        .hasMessageContaining("INVALID");
  }

  @Test
  @DisplayName("Should throw exception when attempting to cancel EXPIRED attempt")
  void shouldThrowExceptionWhenCancellingExpiredAttempt() {
    // Arrange
    Integer authAttemptId = 123;
    AuthAttempt authAttempt = createAuthAttempt(authAttemptId, AuthAttemptStatus.EXPIRED);
    when(authAttemptRepository.findById(authAttemptId)).thenReturn(Optional.of(authAttempt));

    // Act & Assert
    assertThatThrownBy(() -> authAttemptService.cancel(authAttemptId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Cannot cancel")
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
