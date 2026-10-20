/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: KeysetConcurrentModificationException
 * Description: Typed CAS conflict when a keyset blob write loses optimistic concurrency.
 */

package org.ezkey.security.exception;

/**
 * Thrown when a conditional keyset blob save updates zero rows because another writer advanced the
 * version between the local read and the CAS write.
 *
 * <p>Expected under HA multi-writer contention. Callers must not auto-retry the write; the operator
 * or next scheduler tick reloads and re-evaluates. Map promotion conflicts to WARN + {@code
 * KEY_PROMOTION_CONFLICT} audit (Christophe #914).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SuppressWarnings("serial")
public class KeysetConcurrentModificationException extends RuntimeException {

  private final long expectedVersion;

  /**
   * Creates a CAS conflict for the version that was read before the failed write.
   *
   * @param expectedVersion optimistic-lock version used in the {@code WHERE} clause
   * @param message human-readable detail
   */
  public KeysetConcurrentModificationException(long expectedVersion, String message) {
    super(message);
    this.expectedVersion = expectedVersion;
  }

  /**
   * Returns the version that was expected by the losing writer.
   *
   * @return the CAS expected version
   */
  public long getExpectedVersion() {
    return expectedVersion;
  }
}
