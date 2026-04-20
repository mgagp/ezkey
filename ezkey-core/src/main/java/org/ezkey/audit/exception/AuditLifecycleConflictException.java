/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.audit.exception;

import java.io.Serial;

/**
 * Thrown when an audit lifecycle operation would violate the current checkpoint state.
 *
 * <p>Used for lifecycle conflicts such as re-sealing an already advanced checkpoint range.
 *
 * @since 2026
 */
public class AuditLifecycleConflictException extends IllegalStateException {

  @Serial private static final long serialVersionUID = 1L;

  public AuditLifecycleConflictException(String message) {
    super(message);
  }
}
