/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: RetroactiveIntegrityValidationTriggerSource
 * Description: Origin of a retroactive integrity validation run.
 */

package org.ezkey.audit.integrity;

/**
 * Identifies whether retroactive integrity validation was triggered by the nightly scheduler or by
 * a Global Admin operator POST.
 *
 * @since 2026
 */
public enum RetroactiveIntegrityValidationTriggerSource {
  SCHEDULED,
  OPERATOR
}
