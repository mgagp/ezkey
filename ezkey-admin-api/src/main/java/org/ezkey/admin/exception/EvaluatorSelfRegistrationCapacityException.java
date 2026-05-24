/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: EvaluatorSelfRegistrationCapacityException
 * Description: Raised when anonymous evaluator signup limits are reached.
 */

package org.ezkey.admin.exception;

/**
 * Indicates anonymous evaluator signup is unavailable due to capacity or per-client limits.
 *
 * <p>Uses a single public message for daily cap and per-IP success limits.
 */
public class EvaluatorSelfRegistrationCapacityException extends RuntimeException {

  /** Generic client-facing message (no enumeration of limit type). */
  public static final String PUBLIC_MESSAGE =
      "Preview access is temporarily unavailable. Try again later or email info@ezkey.org for"
          + " assistance.";

  /** Constructs the exception with the standard public message. */
  public EvaluatorSelfRegistrationCapacityException() {
    super(PUBLIC_MESSAGE);
  }
}
