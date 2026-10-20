/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: KeysetHaSyncSupportCasGuardTest
 * Description: M-a — claimOrCasUpdateKeyset must refuse expectedVersion <= 0 (no blind write).
 */

package org.ezkey.security;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import org.ezkey.config.TinkProperties;
import org.ezkey.security.domain.repository.KeysetBlobRepository;
import org.ezkey.security.exception.KeysetConcurrentModificationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Patrick M-a: restoring the {@code expectedVersion <= 0} re-read fallback must fail this test.
 *
 * @since 2026
 */
class KeysetHaSyncSupportCasGuardTest {

  @Test
  @DisplayName("claimOrCasUpdateKeyset refuses expectedVersion<=0 when a blob row exists")
  void claimOrCasUpdate_whenMemoryVersionZeroAndRowExists_throwsWithoutBlindWrite() {
    KeysetBlobRepository repository = Mockito.mock(KeysetBlobRepository.class);
    when(repository.keysetExists()).thenReturn(true);

    TinkProperties properties = new TinkProperties();
    KeysetHaSyncSupport support = new KeysetHaSyncSupport(properties, repository);
    AtomicLong memoryVersion = new AtomicLong(0L);
    byte[] payload = "{\"encryptedKeyset\":\"x\"}".getBytes(StandardCharsets.UTF_8);

    assertThrows(
        KeysetConcurrentModificationException.class,
        () ->
            support.claimOrCasUpdateKeyset(
                payload, "TEST_BLIND_WRITE", memoryVersion::get, memoryVersion::set));

    verify(repository, never()).updateKeysetIfVersionMatches(any(), any(), any(), anyLong());
    verify(repository, never()).findVersion();
  }
}
