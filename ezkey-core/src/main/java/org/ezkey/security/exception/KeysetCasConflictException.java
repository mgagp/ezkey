/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */
package org.ezkey.security.exception;

/**
 * Thrown when a keyset blob CAS mutation loses to a concurrent writer (or a stale cached version).
 *
 * <p>Callers must treat this as fail-closed: discard staging, reload the committed blob, and do not
 * serve an orphan in-memory primary. Used by {@code TinkKeyManager} online rotation (#961 n2).
 *
 * @since 2026
 */
public class KeysetCasConflictException extends IllegalStateException {

  /**
   * Creates a CAS-conflict failure with a human-readable message and optional cause.
   *
   * @param message detail including {@code updatedBy} / expected version when known
   * @param cause underlying persistence or optimistic-lock failure (nullable)
   */
  public KeysetCasConflictException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Creates a CAS-conflict failure with a human-readable message.
   *
   * @param message detail including {@code updatedBy} / expected version when known
   */
  public KeysetCasConflictException(String message) {
    super(message);
  }
}
