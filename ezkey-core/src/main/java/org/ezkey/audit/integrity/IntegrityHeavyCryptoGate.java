/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Component: IntegrityHeavyCryptoGate
 * Description: Single-flight gate so nightly and operator Integrity heavy crypto do not stampede.
 */

package org.ezkey.audit.integrity;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Component;

/**
 * Process-local single-flight gate for heavy Integrity crypto paths.
 *
 * <p>Used so the nightly retroactive batch and operator async jobs do not stampede each other on
 * the same Admin API JVM. Cross-instance operator concurrency is enforced by the DB unique RUNNING
 * slot. Checkpoint creation keeps its own short ShedLock and is intentionally not held behind this
 * gate (blocking checkpoints for a long verify would starve the audit-chain heartbeat).
 *
 * @since 2026
 */
@Component
public class IntegrityHeavyCryptoGate {

  private final AtomicBoolean busy = new AtomicBoolean(false);

  /**
   * Attempts to enter the heavy crypto critical section.
   *
   * @return true if this caller owns the gate
   */
  public boolean tryEnter() {
    return busy.compareAndSet(false, true);
  }

  /** Releases the heavy crypto critical section. */
  public void exit() {
    busy.set(false);
  }

  /**
   * Whether the gate is currently held.
   *
   * @return true when busy
   */
  public boolean isBusy() {
    return busy.get();
  }
}
