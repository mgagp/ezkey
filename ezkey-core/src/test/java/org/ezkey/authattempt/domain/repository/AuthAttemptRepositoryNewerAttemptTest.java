/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptRepositoryNewerAttemptTest
 * Description: findNewerAttemptByEnrollmentId returns at most one row when several exist (#727).
 */

package org.ezkey.authattempt.domain.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.data.domain.PageRequest;

/**
 * Regression for #727: concurrent creates within ~1 s must not raise NonUniqueResultException.
 *
 * @since 2026
 */
@DisplayName("AuthAttemptRepository findNewerAttemptByEnrollmentId (#727)")
class AuthAttemptRepositoryNewerAttemptTest {

  @Test
  @DisplayName(
      "When several newer attempts exist, default method requests page size 1 and returns latest"
          + " (SQL orders by createdAt DESC, authAttemptId DESC)")
  void findNewerAttempt_multipleNewer_usesPageSizeOneAndReturnsLatest() {
    AuthAttemptRepository repository =
        mock(AuthAttemptRepository.class, withSettings().defaultAnswer(Answers.CALLS_REAL_METHODS));

    OffsetDateTime base = OffsetDateTime.of(2026, 10, 8, 12, 0, 0, 0, ZoneOffset.UTC);
    AuthAttempt latest = attempt(3, base.plusNanos(500_000_000));
    when(repository.findNewerAttemptsByEnrollmentId(eq(42), eq(base), eq(PageRequest.of(0, 1))))
        .thenReturn(List.of(latest));

    Optional<AuthAttempt> result = repository.findNewerAttemptByEnrollmentId(42, base);

    assertThat(result).contains(latest);
    verify(repository).findNewerAttemptsByEnrollmentId(42, base, PageRequest.of(0, 1));
  }

  private static AuthAttempt attempt(int id, OffsetDateTime createdAt) {
    AuthAttempt attempt = new AuthAttempt();
    attempt.setAuthAttemptId(id);
    attempt.setEnrollmentId(42);
    attempt.setCreatedAt(createdAt);
    return attempt;
  }
}
