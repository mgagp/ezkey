/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Test: EncryptionOperationsHolderTest
 * Description: Lazy supplier contract of the JPA encryption bridge (#1458).
 */

package org.ezkey.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EncryptionOperationsHolder}.
 *
 * @since 2026
 */
class EncryptionOperationsHolderTest {

  @AfterEach
  void tearDown() {
    EncryptionOperationsHolder.clear();
  }

  @Test
  void supplierIsNotCalledUntilFirstGet() {
    AtomicInteger calls = new AtomicInteger();
    EncryptionOperations operations = mock(EncryptionOperations.class);

    EncryptionOperationsHolder.setSupplier(() -> {
      calls.incrementAndGet();
      return operations;
    });

    assertThat(calls).hasValue(0);
    assertThat(EncryptionOperationsHolder.get()).isSameAs(operations);
    assertThat(EncryptionOperationsHolder.get()).isSameAs(operations);
    assertThat(calls).hasValue(1);
  }

  @Test
  void nullAnswerIsNotCachedSoALaterBeanIsPickedUp() {
    AtomicReference<EncryptionOperations> source = new AtomicReference<>();
    EncryptionOperationsHolder.setSupplier(source::get);

    assertThat(EncryptionOperationsHolder.get()).isNull();

    EncryptionOperations operations = mock(EncryptionOperations.class);
    source.set(operations);
    assertThat(EncryptionOperationsHolder.get()).isSameAs(operations);
  }

  @Test
  void setSupplierDropsThePreviouslyCachedInstance() {
    EncryptionOperations first = mock(EncryptionOperations.class);
    EncryptionOperations second = mock(EncryptionOperations.class);
    EncryptionOperationsHolder.set(first);

    EncryptionOperationsHolder.setSupplier(() -> second);

    assertThat(EncryptionOperationsHolder.get()).isSameAs(second);
  }
}
