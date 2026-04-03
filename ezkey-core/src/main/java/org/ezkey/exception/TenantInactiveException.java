/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

package org.ezkey.exception;

/**
 * Thrown when an operation targets a resource that belongs to an inactive tenant.
 *
 * <p>This exception is shared across admin and core business flows so the same tenant-state rule
 * can map consistently across API surfaces.
 */
public class TenantInactiveException extends RuntimeException {

  public TenantInactiveException(String message) {
    super(message);
  }

  public TenantInactiveException(String message, Throwable cause) {
    super(message, cause);
  }
}
