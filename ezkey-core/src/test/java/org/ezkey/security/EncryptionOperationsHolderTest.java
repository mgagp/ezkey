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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link EncryptionOperationsHolder}.
 *
 * @since 2026
 */
class EncryptionOperationsHolderTest {

  private Logger holderLogger;
  private ListAppender<ILoggingEvent> logAppender;

  @BeforeEach
  void captureLogs() {
    holderLogger = (Logger) LoggerFactory.getLogger(EncryptionOperationsHolder.class);
    logAppender = new ListAppender<>();
    logAppender.start();
    holderLogger.addAppender(logAppender);
  }

  @AfterEach
  void tearDown() {
    holderLogger.detachAppender(logAppender);
    EncryptionOperationsHolder.clear();
  }

  @Test
  void nullAnswerFromRegisteredSupplierWarnsOncePerSupplier() {
    EncryptionOperationsHolder.setSupplier(() -> null);

    EncryptionOperationsHolder.get();
    EncryptionOperationsHolder.get();
    assertThat(warnings()).hasSize(1);
    assertThat(warnings().get(0).getFormattedMessage())
        .contains("No EncryptionOperations bean is available");

    EncryptionOperationsHolder.setSupplier(() -> null);
    EncryptionOperationsHolder.get();
    assertThat(warnings()).hasSize(2);
  }

  @Test
  void noSupplierOrResolvedOperationsDoNotWarn() {
    assertThat(EncryptionOperationsHolder.get()).isNull();

    EncryptionOperations operations = mock(EncryptionOperations.class);
    EncryptionOperationsHolder.setSupplier(() -> operations);
    EncryptionOperationsHolder.get();

    assertThat(warnings()).isEmpty();
  }

  private List<ILoggingEvent> warnings() {
    return logAppender.list.stream().filter(event -> event.getLevel() == Level.WARN).toList();
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
