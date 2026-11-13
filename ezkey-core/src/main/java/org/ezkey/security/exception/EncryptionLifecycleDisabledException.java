/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: EncryptionLifecycleDisabledException
 * Description: Thrown when rotation or re-encryption is requested while that lifecycle job is off.
 */

package org.ezkey.security.exception;

/**
 * Thrown when a manual encryption-lifecycle mutation is rejected because the matching config flag
 * is off ({@code ezkey.encryption.rotation.enabled} or {@code
 * ezkey.encryption.reencryption.enabled}).
 *
 * <p>Typical on the opt-in <strong>base</strong> runtime profile. Gate on those flags, not on the
 * product profile name. Map to HTTP 409 with RFC 9457 {@code ProblemDetail} in the Admin API.
 *
 * @since 2026
 */
@SuppressWarnings("serial")
public class EncryptionLifecycleDisabledException extends RuntimeException {

  /** Which lifecycle operation was refused. */
  public enum Operation {
    /** Key introduction / rotation. */
    ROTATION,
    /** Re-encryption enqueue, batch create, or resume. */
    REENCRYPTION
  }

  private final Operation operation;

  /**
   * Creates an exception for a disabled encryption-lifecycle operation.
   *
   * @param operation which operation is inactive
   */
  public EncryptionLifecycleDisabledException(Operation operation) {
    super(
        operation == Operation.ROTATION
            ? "Key rotation is inactive on this instance."
            : "Re-encryption is inactive on this instance.");
    this.operation = operation;
  }

  /**
   * Returns which lifecycle operation was refused.
   *
   * @return the refused operation
   */
  public Operation getOperation() {
    return operation;
  }
}
