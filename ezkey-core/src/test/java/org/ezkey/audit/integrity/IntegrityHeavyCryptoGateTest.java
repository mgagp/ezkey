/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.integrity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.ezkey.audit.integrity.IntegrityHeavyCryptoGate.Holder;
import org.junit.jupiter.api.Test;

/**
 * CAS ownership tests for {@link IntegrityHeavyCryptoGate} (Integrity cut 3 F1).
 *
 * @since 2026
 */
class IntegrityHeavyCryptoGateTest {

  @Test
  void tryEnter_twoThreads_onlyOneWins_andPublishesWinnerHolder() throws Exception {
    IntegrityHeavyCryptoGate gate = new IntegrityHeavyCryptoGate();
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger wins = new AtomicInteger();
    AtomicReference<Holder> published = new AtomicReference<>();

    Thread t1 =
        new Thread(
            () -> {
              ready.countDown();
              await(start);
              if (gate.tryEnter(Holder.NIGHTLY)) {
                wins.incrementAndGet();
                published.compareAndSet(null, gate.holder());
              }
            });
    Thread t2 =
        new Thread(
            () -> {
              ready.countDown();
              await(start);
              if (gate.tryEnter(Holder.OPERATOR_JOB)) {
                wins.incrementAndGet();
                published.compareAndSet(null, gate.holder());
              }
            });
    t1.start();
    t2.start();
    assertTrue(ready.await(5, TimeUnit.SECONDS));
    start.countDown();
    t1.join(5_000);
    t2.join(5_000);

    assertEquals(1, wins.get());
    assertTrue(
        published.get() == Holder.NIGHTLY || published.get() == Holder.OPERATOR_JOB,
        "published holder must be the winner");
    assertEquals(published.get(), gate.holder());
  }

  @Test
  void exit_wrongHolder_doesNotRelease() {
    IntegrityHeavyCryptoGate gate = new IntegrityHeavyCryptoGate();
    assertTrue(gate.tryEnter(Holder.OPERATOR_JOB));
    gate.exit(Holder.NIGHTLY);
    assertTrue(gate.isBusy());
    assertEquals(Holder.OPERATOR_JOB, gate.holder());
    gate.exit(Holder.OPERATOR_JOB);
    assertFalse(gate.isBusy());
    assertNull(gate.holder());
  }

  private static void await(CountDownLatch latch) {
    try {
      assertTrue(latch.await(5, TimeUnit.SECONDS));
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(e);
    }
  }
}
