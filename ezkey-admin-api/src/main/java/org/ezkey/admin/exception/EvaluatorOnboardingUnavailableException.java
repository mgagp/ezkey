/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Exception: EvaluatorOnboardingUnavailableException
 * Description: Raised when public evaluator onboarding re-issue cannot proceed.
 */
package org.ezkey.admin.exception;

/**
 * Indicates public evaluator onboarding re-issue is unavailable for the given username.
 *
 * <p>Uses a single public message for missing, ineligible, or completed enrollments
 * (anti-enumeration). Maps to HTTP 404.
 *
 * @since 2026
 */
@SuppressWarnings("serial")
public class EvaluatorOnboardingUnavailableException extends RuntimeException {

  /** Generic client-facing message (no enumeration of reason). */
  public static final String PUBLIC_MESSAGE =
      "Unable to resume evaluator onboarding for this username. Complete device bind and sign in,"
          + " or start a new preview signup if this workspace is no longer available.";

  /** Constructs the exception with the standard public message. */
  public EvaluatorOnboardingUnavailableException() {
    super(PUBLIC_MESSAGE);
  }
}
