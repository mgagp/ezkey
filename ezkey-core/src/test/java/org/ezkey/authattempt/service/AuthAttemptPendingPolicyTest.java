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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
    when(authAttemptRepository.updateStatusForMultipleAttempts(anyList(), any(), anyList()))
        .thenReturn(1);
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
        .updateStatusForMultipleAttempts(
            List.of(8),
            AuthAttemptStatus.EXPIRED,
            List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ));
  }

  @Test
  @DisplayName("REPLACE still supersedes a live attempt")
  void replace_liveAttempt_supersedes() {
    stubEnrollment(PendingAttemptPolicy.REPLACE);
    AuthAttempt live = attempt(8, OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC).plusSeconds(40));
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenReturn(List.of(live));
    when(authAttemptRepository.updateStatusForMultipleAttempts(anyList(), any(), anyList()))
        .thenReturn(1);
    when(signatureService.generateProofToken()).thenReturn("proof");
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.create(request());

    verify(authAttemptRepository)
        .updateStatusForMultipleAttempts(
            List.of(8),
            AuthAttemptStatus.EXPIRED,
            List.of(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ));
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

  @Test
  @DisplayName("INVALID attempts arm the cooldown on the fixed clock")
  void cooldown_invalidArmsSuspension() {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(verifiedEnrollment()));
    OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    when(authAttemptRepository.findCreatedAtByEnrollmentIdAndStatusInSince(any(), anyList(), any()))
        .thenReturn(List.of(now.minusMinutes(8), now.minusMinutes(4), now.minusMinutes(1)));

    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(AuthAttemptCreationCooldownException.class)
        .extracting(ex -> ((AuthAttemptCreationCooldownException) ex).getRetryAfterSeconds())
        .isEqualTo(240L);

    verify(authAttemptRepository)
        .findCreatedAtByEnrollmentIdAndStatusInSince(
            eq(1),
            argThat(statuses ->
                statuses.contains(AuthAttemptStatus.INVALID)
                    && statuses.contains(AuthAttemptStatus.REJECTED)
                    && !statuses.contains(AuthAttemptStatus.EXPIRED)),
            eq(now.minusSeconds(900)));
  }

  @Test
  @DisplayName("Regular integration: superseded and expired rows do not cool down; signed ones do")
  void cooldown_regularIntegration_supersedeDoesNotSuspend_signedDoes() {
    stubEnrollment(PendingAttemptPolicy.REPLACE, false);
    List<AuthAttempt> rows = new ArrayList<>();
    wireAttemptStore(rows);

    for (int i = 0; i < 4; i++) {
      assertThat(service.create(request())).isNotNull();
    }
    assertThat(rows)
        .filteredOn(row -> row.getAuthAttemptStatus() == AuthAttemptStatus.EXPIRED)
        .hasSize(3);

    OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    rows.add(stored(90, AuthAttemptStatus.EXPIRED, now.minusMinutes(2)));
    rows.add(stored(91, AuthAttemptStatus.EXPIRED, now.minusMinutes(3)));
    assertThat(service.create(request())).isNotNull();

    rows.clear();
    rows.add(stored(1, AuthAttemptStatus.REJECTED, now.minusMinutes(8)));
    rows.add(stored(2, AuthAttemptStatus.REJECTED, now.minusMinutes(4)));
    rows.add(stored(3, AuthAttemptStatus.INVALID, now.minusMinutes(1)));
    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(AuthAttemptCreationCooldownException.class);

    rows.clear();
    AuthAttempt signed = stored(7, AuthAttemptStatus.REJECTED, now.minusMinutes(1));
    rows.add(signed);
    rows.add(stored(8, AuthAttemptStatus.PENDING, now.plusSeconds(30)));
    rows.get(1).setExpiresAt(now.plusSeconds(40));
    assertThat(service.create(request())).isNotNull();
    assertThat(signed.getAuthAttemptStatus()).isEqualTo(AuthAttemptStatus.REJECTED);
  }

  @Test
  @DisplayName("System integration: four superseding creates do not cool down; three INVALID do")
  void cooldown_systemIntegration_supersedeDoesNotSuspend_invalidDoes() {
    stubEnrollment(PendingAttemptPolicy.REPLACE, true);
    List<AuthAttempt> rows = new ArrayList<>();
    wireAttemptStore(rows);

    for (int i = 0; i < 4; i++) {
      assertThat(service.create(request())).isNotNull();
    }
    assertThat(rows)
        .filteredOn(row -> row.getAuthAttemptStatus() == AuthAttemptStatus.EXPIRED)
        .hasSize(3);
    assertThat(rows)
        .filteredOn(row -> row.getAuthAttemptStatus() == AuthAttemptStatus.PENDING)
        .hasSize(1);

    rows.clear();
    OffsetDateTime now = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
    rows.add(stored(1, AuthAttemptStatus.INVALID, now.minusMinutes(8)));
    rows.add(stored(2, AuthAttemptStatus.INVALID, now.minusMinutes(4)));
    rows.add(stored(3, AuthAttemptStatus.INVALID, now.minusMinutes(1)));

    assertThatThrownBy(() -> service.create(request()))
        .isInstanceOf(AuthAttemptCreationCooldownException.class);
  }

  private void wireAttemptStore(List<AuthAttempt> rows) {
    int[] nextId = {100};
    when(authAttemptRepository.findCreatedAtByEnrollmentIdAndStatusInSince(any(), anyList(), any()))
        .thenAnswer(invocation -> {
          List<AuthAttemptStatus> statuses = invocation.getArgument(1);
          OffsetDateTime since = invocation.getArgument(2);
          assertThat(statuses)
              .containsExactlyInAnyOrder(AuthAttemptStatus.REJECTED, AuthAttemptStatus.INVALID);
          return rows.stream()
              .filter(row -> statuses.contains(row.getAuthAttemptStatus()))
              .filter(row -> !row.getCreatedAt().isBefore(since))
              .map(AuthAttempt::getCreatedAt)
              .toList();
        });
    when(authAttemptRepository.findByEnrollmentIdAndStatusIn(any(), anyList()))
        .thenAnswer(invocation -> {
          List<AuthAttemptStatus> statuses = invocation.getArgument(1);
          return rows.stream()
              .filter(row -> statuses.contains(row.getAuthAttemptStatus()))
              .toList();
        });
    when(authAttemptRepository.updateStatusForMultipleAttempts(anyList(), any(), anyList()))
        .thenAnswer(invocation -> {
          List<Integer> ids = invocation.getArgument(0);
          AuthAttemptStatus status = invocation.getArgument(1);
          List<AuthAttemptStatus> live = invocation.getArgument(2);
          assertThat(live)
              .containsExactlyInAnyOrder(AuthAttemptStatus.PENDING, AuthAttemptStatus.READ);
          int updated = 0;
          for (AuthAttempt row : rows) {
            if (ids.contains(row.getAuthAttemptId()) && live.contains(row.getAuthAttemptStatus())) {
              row.setAuthAttemptStatus(status);
              updated++;
            }
          }
          return updated;
        });
    when(signatureService.generateProofToken()).thenReturn("proof");
    when(authAttemptRepository.save(any(AuthAttempt.class)))
        .thenAnswer(invocation -> {
          AuthAttempt saved = invocation.getArgument(0);
          saved.setAuthAttemptId(nextId[0]++);
          rows.add(saved);
          return saved;
        });
  }

  private void stubEnrollment(PendingAttemptPolicy policy) {
    stubEnrollment(policy, false);
  }

  private void stubEnrollment(PendingAttemptPolicy policy, boolean systemIntegration) {
    when(enrollmentRepository.findById(1)).thenReturn(Optional.of(verifiedEnrollment()));
    Integration integration = new Integration();
    integration.setId(9);
    integration.setPendingAttemptPolicy(policy);
    integration.setIsSystemIntegration(systemIntegration);
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

  private static AuthAttempt stored(int id, AuthAttemptStatus status, OffsetDateTime createdAt) {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(id);
    attempt.setEnrollmentId(1);
    attempt.setAuthAttemptStatus(status);
    attempt.setCreatedAt(createdAt);
    return attempt;
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
