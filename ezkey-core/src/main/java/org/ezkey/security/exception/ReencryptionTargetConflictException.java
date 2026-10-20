/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: ReencryptionTargetConflictException
 * Description: Thrown when re-encryption would be a no-op (old key equals target primary).
 */

package org.ezkey.security.exception;

/**
 * Thrown when a manual re-encryption request resolves to the same old and new key after a versioned
 * keyset reload (would create silent no-op batches).
 *
 * <p>Map to HTTP 409 Conflict with a clear message — never {@code 202 Accepted} followed by skipped
 * old==new batches (#921).
 *
 * @author Ezkey contributors
 * @since 2026
 */
@SuppressWarnings("serial")
public class ReencryptionTargetConflictException extends IllegalStateException {

  private final Long oldKeyId;
  private final Long newKeyId;

  /**
   * Creates a conflict when old and new encryption keys are identical after reload.
   *
   * @param oldKeyId the requested or discovered old key id
   * @param newKeyId the primary key id from the reloaded blob
   * @param message human-readable detail for Problem {@code detail}
   */
  public ReencryptionTargetConflictException(Long oldKeyId, Long newKeyId, String message) {
    super(message);
    this.oldKeyId = oldKeyId;
    this.newKeyId = newKeyId;
  }

  /**
   * Returns the old key id when known.
   *
   * @return old key id, or {@code null}
   */
  public Long getOldKeyId() {
    return oldKeyId;
  }

  /**
   * Returns the new (primary) key id when known.
   *
   * @return new key id, or {@code null}
   */
  public Long getNewKeyId() {
    return newKeyId;
  }
}
