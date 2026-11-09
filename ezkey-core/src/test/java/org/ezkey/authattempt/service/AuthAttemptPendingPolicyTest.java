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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptCreateRequest;
import org.ezkey.authattempt.domain.AuthAttemptCreateResponse;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.config.EzkeyCoreProperties;
import org.ezkey.enrollment.domain.EnrollmentStatus;
import org.ezkey.enrollment.domain.entity.Enrollment;
import org.ezkey.enrollment.domain.repository.EnrollmentRepository;
import org.ezkey.exception.auth.AuthAttemptCreateValidationException;
import org.ezkey.exception.auth.AuthAttemptCreationCooldownException;
import org.ezkey.exception.auth.AuthAttemptPendingExistsException;
import org.ezkey.integration.domain.PendingAttemptPolicy;
import org.ezkey.integration.domain.entity.Integration;
import org.ezkey.integration.domain.repository.IntegrationRepository;
import org.ezkey.signature.SignatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Pending-attempt policy and creation cooldown on {@link AuthAttemptService#create}.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuthAttemptPendingPolicyTest {

  private static final Instant NOW = Instant.parse("2026-11-09T12:00:00Z");

  @Mock private AuthAttemptRepository authAttemptRepository;
  @Mock private EnrollmentRepository enrollmentRepository;
  @Mock private SignatureService signatureService;
  @Mock private AuthAttemptPendingService pendingService;
  @Mock private AuthAttemptRespondService respondService;
  @Mock private AuthAttemptWaitService waitService;
  @Mock private IntegrationRepository integrationRepository;
  @Mock private AuthAttemptCreateLock createLock;

  private AuthAttemptService service;

  @BeforeEach
  void setUp() {
    service =
        new AuthAttemptService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            new EzkeyCoreProperties(),
            pendingService,
            respondService,
            waitService,
            integrationRepository,
            createLock,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  @DisplayName("REFUSE with a live attempt returns retry equal to the remaining TTL")
  void refuse_liveAttempt_throwsWithRemainingTtl() {
    stubEnrollment(PendingAttemptPolicy.REFUSE);
    AuthAttempt live = attempt(8, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(40));
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenReturn(List.of(live));

    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(AuthAttemptPendingExistsException.class)
        .extracting(ex -> ((AuthAttemptPendingExistsException) ex).getRetryAfterSeconds())
        .isEqualTo(40L);
    verify(authAttemptRepository, never()).save(any());
    verify(createLock).lockEnrollment(1);
  }

  @Test
  @DisplayName("REFUSE expires a time-elapsed pending row and then creates")
  void refuse_stalePending_expiresThenCreates() {
    stubEnrollment(PendingAttemptPolicy.REFUSE);
    AuthAttempt stale = attempt(8, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).minusSeconds(5));
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenReturn(List.of(stale));
    when(authAttemptRepository.updateStatusForMultipleAttempts(anyList(), any())).thenReturn(1);
    when(signatureService.generateProofToken()).thenReturn("proof");
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> {
          AuthAttempt saved = invocation.getArgument(0);
          saved.setAuthAttemptId(99);
          return saved;
        });

    AuthAttemptCreateResponse response = service.create(request());

    assertThat(response.getAuthAttemptId()).isEqualTo(99);
    verify(authAttemptRepository)
        .updateStatusForMultipleAttempts(List.of(8), AuthAttemptStatus.EXPIRED);
  }

  @Test
  @DisplayName("REPLACE still supersedes a live attempt")
  void replace_liveAttempt_supersedes() {
    stubEnrollment(PendingAttemptPolicy.REPLACE);
    AuthAttempt live = attempt(8, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(40));
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenReturn(List.of(live));
    when(authAttemptRepository.updateStatusForMultipleAttempts(anyList(), any())).thenReturn(1);
    when(signatureService.generateProofToken()).thenReturn("proof");
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.create(request());

    verify(authAttemptRepository)
        .updateStatusForMultipleAttempts(List.of(8), AuthAttemptStatus.EXPIRED);
  }

  @Test
  @DisplayName("Missing integration row fails closed")
  void missingIntegration_failsClosed() {
    Enrollment enrollment = verifiedEnrollment();
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(enrollment));
    when(integrationRepository.findById(9)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(AuthAttemptCreateValidationException.class);
  }

  @Test
  @DisplayName("Cooldown suspends create and then allows it after the window expires")
  void cooldown_suspendsThenExpires() {
    stubEnrollment(PendingAttemptPolicy.REPLACE);
    OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    when(authAttemptRepository.findCreatedAtByEnrollmentIdAndStatusInSince(any(), anyList(), any()))
        .thenReturn(List.of(now.minusMinutes(8), now.minusMinutes(4), now.minusMinutes(1)));

    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(AuthAttemptCreationCooldownException.class)
        .extracting(ex -> ((AuthAttemptCreationCooldownException) ex).getRetryAfterSeconds())
        .isEqualTo(240L);

    Clock later = Clock.fixed(NOW.plusSeconds(240), ZoneOffset.UTC);
    AuthAttemptService after =
        new AuthAttemptService(
            authAttemptRepository,
            enrollmentRepository,
            signatureService,
            new EzkeyCoreProperties(),
            pendingService,
            respondService,
            waitService,
            integrationRepository,
            createLock,
            later);
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenReturn(List.of());
    when(signatureService.generateProofToken()).thenReturn("proof");
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    assertThat(after.create(request())).isNotNull();
  }

  private void stubEnrollment(PendingAttemptPolicy policy) {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(verifiedEnrollment()));
    Integration integration = new Integration();
    integration.setId(9);
    integration.setPendingAttemptPolicy(policy);
    when(integrationRepository.findById(9)).thenReturn(Optional.of(integration));
  }

  private static Enrollment verifiedEnrollment() {
    Enrollment enrollment = new Enrollment();
    enrollment.setEnrollmentId(1);
    enrollment.setIntegrationId(9);
    enrollment.setActive(true);
    enrollment.setStatus(EnrollmentStatus.VERIFIED);
    enrollment.setAuthAttemptChallengeRequired(false);
    return enrollment;
  }

  private static AuthAttempt attempt(int id, OffsetDateTime expiresAt) {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(id);
    attempt.setEnrollmentId(1);
    attempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    attempt.setExpiresAt(expiresAt);
    return attempt;
  }

  private static AuthAttemptCreateRequest request() {
    AuthAttemptCreateRequest request = new AuthAttemptCreateRequest();
    request.setEnrollmentId(1);
    request.setChallengeRequested(false);
    return request;
  }
}
