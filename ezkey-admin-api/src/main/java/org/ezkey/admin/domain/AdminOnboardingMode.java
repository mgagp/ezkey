/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AdminOnboardingMode
 * Description: Supported onboarding modes for administrator provisioning.
 */

package org.ezkey.admin.domain;

/**
 * Supported onboarding modes for administrator provisioning.
 *
 * <p>The mode controls whether the first enrollment is created immediately or deferred behind a
 * one-time activation code.
 *
 * @since 2025
 */
public enum AdminOnboardingMode {
  /** Creates the enrollment and recovery codes immediately during admin provisioning. */
  IMMEDIATE,

  /** Defers the first enrollment until a one-time activation code is consumed. */
  ACTIVATION_CODE
}
