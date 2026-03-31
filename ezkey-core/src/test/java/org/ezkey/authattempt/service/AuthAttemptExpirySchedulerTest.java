/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptExpirySchedulerTest
 * Description: Unit tests for auth attempt expiry scheduler.
 */

package org.ezkey.authattempt.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Unit tests for {@link AuthAttemptExpiryScheduler#expireStalePendingAndReadAttempts()}. */
@ExtendWith(MockitoExtension.class)
@DisplayName("Auth attempt expiry scheduler")
class AuthAttemptExpirySchedulerTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  @InjectMocks private AuthAttemptExpiryScheduler scheduler;

  @Test
  @DisplayName("expireStalePendingAndReadAttempts delegates bulk expiry to repository")
  void expireStalePendingAndReadAttempts_CallsRepository() {
    when(authAttemptRepository.expireAttemptsPastDeadline(
            any(), any(OffsetDateTime.class), eq(AuthAttemptStatus.EXPIRED)))
        .thenReturn(0);

    scheduler.expireStalePendingAndReadAttempts();

    verify(authAttemptRepository)
        .expireAttemptsPastDeadline(
            any(), any(OffsetDateTime.class), eq(AuthAttemptStatus.EXPIRED));
  }
}
