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

import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Process-local single-flight gate for heavy Integrity crypto paths.
 *
 * <p>Used so the nightly retroactive batch and operator async jobs do not stampede each other on
 * the same Admin API JVM. Cross-instance operator concurrency is enforced by the DB unique RUNNING
 * slot. Checkpoint creation keeps its own short ShedLock and is intentionally not held behind this
 * gate (blocking checkpoints for a long verify would starve the audit-chain heartbeat).
 *
 * <p>Ownership is published atomically with acquisition via a single {@link AtomicReference} of
 * {@link Holder} (Integrity cut 3 F1). Cross-replica "nightly running" for the Admin UI is read
 * from {@code ezkey_shedlock}, not from this process-local gate.
 *
 * @since 2026
 */
@Component
public class IntegrityHeavyCryptoGate {

  private static final Logger logger = LoggerFactory.getLogger(IntegrityHeavyCryptoGate.class);

  /**
   * Who holds the process-local heavy-crypto gate.
   *
   * @since 2026
   */
  public enum Holder {
    /** Nightly scheduled validation batch. */
    NIGHTLY,
    /** Synchronous report GET (chain / entry / retroactive). */
    REPORT_GET,
    /** Operator Integrity async job (VERIFY_* or RUN_VALIDATION). */
    OPERATOR_JOB
  }

  private final AtomicReference<Holder> holder = new AtomicReference<>();

  /**
   * Attempts to enter the heavy crypto critical section as {@code h}.
   *
   * @param h intended holder (must not be null)
   * @return true if this caller owns the gate
   */
  public boolean tryEnter(Holder h) {
    if (h == null) {
      throw new IllegalArgumentException("holder must not be null");
    }
    return holder.compareAndSet(null, h);
  }

  /**
   * Releases the heavy crypto critical section when still held by {@code h}.
   *
   * <p>A mismatched or empty holder logs WARN and does not clear another owner's claim.
   *
   * @param h holder that acquired the gate
   */
  public void exit(Holder h) {
    if (h == null) {
      throw new IllegalArgumentException("holder must not be null");
    }
    if (!holder.compareAndSet(h, null)) {
      logger.warn(
          "Integrity heavy-crypto gate exit mismatch: expected holder={}, actual={}",
          h,
          holder.get());
    }
  }

  /**
   * Whether the gate is currently held.
   *
   * @return true when busy
   */
  public boolean isBusy() {
    return holder.get() != null;
  }

  /**
   * Current process-local holder, or null when idle.
   *
   * @return holder or null
   */
  public Holder holder() {
    return holder.get();
  }
}
