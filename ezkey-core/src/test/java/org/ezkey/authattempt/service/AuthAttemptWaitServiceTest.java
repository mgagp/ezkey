/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.AuthAttemptWaitRequest;
import org.ezkey.authattempt.domain.AuthAttemptWaitResponse;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.ezkey.exception.auth.AuthAttemptWaitValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthAttemptWaitService")
class AuthAttemptWaitServiceTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @Test
  @DisplayName("Rejects timeout outside allowed range")
  void rejectsTimeoutOutsideAllowedRange() {
    AuthAttemptWaitService service = new AuthAttemptWaitService(authAttemptRepository);

    assertThatThrownBy(() -> service.waitForResponse(123, new AuthAttemptWaitRequest(0, 2)))
        .isInstanceOf(AuthAttemptWaitValidationException.class)
        .hasMessage("Timeout must be between 1 and 300 seconds");

    verifyNoInteractions(authAttemptRepository);
  }

  @Test
  @DisplayName("Rejects polling outside allowed range")
  void rejectsPollingOutsideAllowedRange() {
    AuthAttemptWaitService service = new AuthAttemptWaitService(authAttemptRepository);

    assertThatThrownBy(() -> service.waitForResponse(123, new AuthAttemptWaitRequest(30, 61)))
        .isInstanceOf(AuthAttemptWaitValidationException.class)
        .hasMessage("Polling must be between 1 and 60 seconds");

    verifyNoInteractions(authAttemptRepository);
  }

  @Test
  @DisplayName("Rejects polling greater than timeout")
  void rejectsPollingGreaterThanTimeout() {
    AuthAttemptWaitService service = new AuthAttemptWaitService(authAttemptRepository);

    assertThatThrownBy(() -> service.waitForResponse(123, new AuthAttemptWaitRequest(30, 31)))
        .isInstanceOf(AuthAttemptWaitValidationException.class)
        .hasMessage("Polling interval cannot be greater than timeout");

    verifyNoInteractions(authAttemptRepository);
  }

  @Test
  @DisplayName(
      "Concurrent wait while two newer attempts exist within 1 s returns EXPIRED without error"
          + " (#727)")
  void wait_twoNewerAttemptsWithinOneSecond_returnsExpired() throws Exception {
    AuthAttemptWaitService service = new AuthAttemptWaitService(authAttemptRepository);

    OffsetDateTime t0 = OffsetDateTime.of(2026, 10, 8, 12, 0, 0, 0, ZoneOffset.UTC);
    AuthAttempt original = pendingAttempt(1, 99, t0);
    AuthAttempt newer = pendingAttempt(3, 99, t0.plusNanos(400_000_000));

    when(authAttemptRepository.findById(1)).thenReturn(Optional.of(original));
    when(authAttemptRepository.findNewerAttemptByEnrollmentId(eq(99), eq(t0)))
        .thenReturn(Optional.of(newer));

    CountDownLatch start = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<AuthAttemptWaitResponse> f1 =
          pool.submit(
              () -> {
                start.await(2, TimeUnit.SECONDS);
                return service.waitForResponse(1, new AuthAttemptWaitRequest(1, 1));
              });
      Future<AuthAttemptWaitResponse> f2 =
          pool.submit(
              () -> {
                start.await(2, TimeUnit.SECONDS);
                return service.waitForResponse(1, new AuthAttemptWaitRequest(1, 1));
              });
      start.countDown();

      AuthAttemptWaitResponse r1 = f1.get(5, TimeUnit.SECONDS);
      AuthAttemptWaitResponse r2 = f2.get(5, TimeUnit.SECONDS);

      assertThat(r1.getStatus()).isEqualTo("EXPIRED");
      assertThat(r2.getStatus()).isEqualTo("EXPIRED");
      assertThat(r1.getCompleted()).isFalse();
      assertThat(r2.getCompleted()).isFalse();
    } finally {
      pool.shutdownNow();
    }
  }

  private static AuthAttempt pendingAttempt(int id, int enrollmentId, OffsetDateTime createdAt) {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(id);
    attempt.setEnrollmentId(enrollmentId);
    attempt.setAuthAttemptStatus(AuthAttemptStatus.PENDING);
    attempt.setCreatedAt(createdAt);
    attempt.setExpiresAt(createdAt.plusMinutes(5));
    return attempt;
  }
}
