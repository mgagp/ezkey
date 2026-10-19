/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for per-request {@code ttlSeconds} on create (#895 PR-A).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptService TTL (#895)")
class AuthAttemptServiceTtlTest {

  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private AuthAttemptPendingService pendingService;
  @Mock private AuthAttemptRespondService respondService;
  @Mock private AuthAttemptWaitService waitService;

  private AuthAttemptService authAttemptService;
  private EzkeyCoreProperties properties;

  @BeforeEach
  void setUp() {
    properties = new EzkeyCoreProperties();
    properties.getAuthAttempt().setTtlSeconds(120);
    authAttemptService =
        new AuthAttemptService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            properties,
            pendingService,
            respondService,
            waitService);
  }

  @Test
  @DisplayName("Absent ttlSeconds → server default (120)")
  void create_absentTtl_usesConfiguredDefault() {
    stubSuccessfulCreate(null);

    AuthAttemptCreateResponse response = authAttemptService.create(createRequest(null));

    assertThat(response.getTimeoutSeconds()).isEqualTo(120);
    assertThat(response.getExpiresAt()).isAfter(OffsetDateTime.now().plusSeconds(100));
  }

  @Test
  @DisplayName("ttlSeconds=15 → applied 15")
  void create_ttlAtFloor_applies15() {
    stubSuccessfulCreate(15);

    AuthAttemptCreateResponse response = authAttemptService.create(createRequest(15));

    assertThat(response.getTimeoutSeconds()).isEqualTo(15);
  }

  @Test
  @DisplayName("ttlSeconds=14 → AuthAttemptCreateValidationException")
  void create_ttlBelowFloor_rejected() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(verifiedEnrollment()));

    assertThatThrownBy(() -> authAttemptService.create(createRequest(14)))
        .isInstanceOf(AuthAttemptCreateValidationException.class)
        .hasMessageContaining("15");
  }

  @Test
  @DisplayName("ttlSeconds=121 → clamped to 120")
  void create_ttlAboveCeiling_clampedTo120() {
    stubSuccessfulCreate(120);

    AuthAttemptCreateResponse response = authAttemptService.create(createRequest(121));

    assertThat(response.getTimeoutSeconds()).isEqualTo(120);
  }

  @Test
  @DisplayName("ttlSeconds=0 → rejected")
  void create_ttlZero_rejected() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(verifiedEnrollment()));

    assertThatThrownBy(() -> authAttemptService.create(createRequest(0)))
        .isInstanceOf(AuthAttemptCreateValidationException.class);
  }

  @Test
  @DisplayName("resolveAppliedTtlSeconds: null → default, 15 ok, 121 clamp, 14 reject")
  void resolveAppliedTtlSeconds_branches() {
    assertThat(authAttemptService.resolveAppliedTtlSeconds(null)).isEqualTo(120);
    assertThat(authAttemptService.resolveAppliedTtlSeconds(15)).isEqualTo(15);
    assertThat(authAttemptService.resolveAppliedTtlSeconds(90)).isEqualTo(90);
    assertThat(authAttemptService.resolveAppliedTtlSeconds(121)).isEqualTo(120);
    assertThatThrownBy(() -> authAttemptService.resolveAppliedTtlSeconds(14))
        .isInstanceOf(AuthAttemptCreateValidationException.class);
  }

  @Test
  @DisplayName("Absent ttlSeconds with configured default 300 → applied min(300,120)=120 (M2)")
  void resolveAppliedTtlSeconds_configuredDefaultAboveCeiling_clamped() {
    properties.getAuthAttempt().setTtlSeconds(300);
    assertThat(authAttemptService.resolveAppliedTtlSeconds(null)).isEqualTo(120);
  }

  @Test
  @DisplayName("Startup clamp: configured default >120 reduced to 120 (M2)")
  void clampConfiguredDefaultTtlIfAboveHardCeiling_reducesProperty() {
    properties.getAuthAttempt().setTtlSeconds(480);
    authAttemptService.clampConfiguredDefaultTtlIfAboveHardCeiling();
    assertThat(properties.getAuthAttempt().getTtlSeconds()).isEqualTo(120);
  }

  @Test
  @DisplayName("Cancel then create immediately → 201 path (no retention) (#895)")
  void create_afterCancel_succeedsImmediately() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(verifiedEnrollment()));
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenReturn(Collections.emptyList());
    when(signatureService.generateProofToken()).thenReturn("proof");
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(
            invocation -> {
              AuthAttempt saved = invocation.getArgument(0);
              saved.setAuthAttemptId(99);
              return saved;
            });

    AuthAttemptCreateResponse response = authAttemptService.create(createRequest(30));

    assertThat(response.getAuthAttemptId()).isEqualTo(99);
    assertThat(response.getTimeoutSeconds()).isEqualTo(30);
    verify(authAttemptRepository).save(any(AuthAttempt.class));
  }

  private void stubSuccessfulCreate(Integer ignoredExpectedAppliedTtl) {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(verifiedEnrollment()));
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenReturn(Collections.emptyList());
    when(signatureService.generateProofToken()).thenReturn("proof");
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(
            invocation -> {
              AuthAttempt saved = invocation.getArgument(0);
              saved.setAuthAttemptId(42);
              assertThat(saved.getExpiresAt()).isNotNull();
              return saved;
            });
  }

  private static AuthAttemptCreateRequest createRequest(Integer ttlSeconds) {
    AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
    request.setEnrollmentId(1);
    request.setChallengeRequested(false);
    request.setTtlSeconds(ttlSeconds);
    return request;
  }

  private static Enrollment verifiedEnrollment() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(1);
    enrollment.setActive(true);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setAuthAttemptChallengeRequired(false);
    return enrollment;
  }
}
