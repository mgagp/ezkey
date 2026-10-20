/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: AuthAttemptTxHelperTest
 * Description: Atomic markAsInvalid only when status is READ (#912 M1 / Patrick M5).
 */

package org.ezkey.authattempt.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Optional;
import org.ezkey.authattempt.domain.AuthAttemptStatus;
import org.ezkey.authattempt.domain.entity.AuthAttempt;
import org.ezkey.authattempt.domain.repository.AuthAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link AuthAttemptTxHelper#markAsInvalid} atomic READ → INVALID update.
 *
 * @since 2026
 */
@ExtendWith(MockitoExtension.class)
class AuthAttemptTxHelperTest {

  @Mock private AuthAttemptRepository authAttemptRepository;

  private AuthAttemptTxHelper helper;

  @BeforeEach
  void setUp() {
    helper = new AuthAttemptTxHelper(authAttemptRepository);
  }

  @Test
  @DisplayName("markAsInvalid uses updateStatusIfCurrent(READ → INVALID)")
  void markAsInvalid_usesConditionalUpdate() {
    when(authAttemptRepository.updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.INVALID), any()))
        .thenReturn(1);

    helper.markAsInvalid(42);

    ArgumentCaptor<OffsetDateTime> nowCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
    verify(authAttemptRepository)
        .updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.INVALID), nowCaptor.capture());
    assertThat(nowCaptor.getValue()).isNotNull();
    verify(authAttemptRepository, never()).save(any());
  }

  /**
   * Discriminant vs the old read-then-save path (#912 Patrick M5): when the conditional UPDATE
   * loses the race (0 rows), the helper must not {@code save} even if a subsequent {@code findById}
   * still returns {@code READ} (stale view that the old implementation would overwrite onto a
   * concurrent ACCEPTED/EXPIRED).
   */
  @Test
  @DisplayName(
      "markAsInvalid after lost conditional update never saves (even if findById still shows READ)")
  void markAsInvalid_whenConditionalUpdateLoses_doesNotSaveEvenIfFindReturnsRead() {
    when(authAttemptRepository.updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.INVALID), any()))
        .thenReturn(0);
    AuthAttempt staleRead = new AuthAttempt();
    staleRead.setAuthAttemptId(42);
    staleRead.setAuthAttemptStatus(AuthAttemptStatus.READ);
    when(authAttemptRepository.findById(42)).thenReturn(Optional.of(staleRead));

    helper.markAsInvalid(42);

    verify(authAttemptRepository)
        .updateStatusIfCurrent(
            eq(42), eq(AuthAttemptStatus.READ), eq(AuthAttemptStatus.INVALID), any());
    verify(authAttemptRepository, never()).save(any());
  }
}
