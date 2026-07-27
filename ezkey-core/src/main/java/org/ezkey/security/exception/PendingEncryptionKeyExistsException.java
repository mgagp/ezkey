/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: PendingEncryptionKeyExistsException
 * Description: Thrown when a new encryption key cannot be introduced because a PENDING key already
 * exists.
 */

package org.ezkey.security.exception;

/**
 * Thrown when key introduction is rejected because a {@code PENDING} encryption key already exists
 * and must be promoted (or immediate promotion used) before another key can be introduced.
 *
 * <p><b>Handling:</b> Map to HTTP 409 Conflict with RFC 9457 {@code ProblemDetail} in the Admin
 * API.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2025
 */
@SuppressWarnings("serial")
public class PendingEncryptionKeyExistsException extends RuntimeException {

  private final Long pendingKeyId;

  /**
   * Creates an exception when introduction is blocked by an existing PENDING key.
   *
   * @param pendingKeyId id of the existing PENDING key, or {@code null} if unknown
   * @param message human-readable detail (also used as Problem {@code detail})
   */
  public PendingEncryptionKeyExistsException(Long pendingKeyId, String message) {
    super(message);
    this.pendingKeyId = pendingKeyId;
  }

  /**
   * Returns the pending key identifier when the service resolved it from the database.
   *
   * @return the existing PENDING key id when known, or {@code null}
   */
  public Long getPendingKeyId() {
    return pendingKeyId;
  }
}
