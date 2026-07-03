/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: EntryIntegrityConciliationStatus
 * Description: Operator conciliation posture for a per-entry HMAC integrity violation.
 */

package org.ezkey.audit.dto;

/**
 * Conciliation posture returned with {@link EntryIntegrityViolation} at verify time.
 *
 * @since 2026
 */
public enum EntryIntegrityConciliationStatus {
  /** HMAC is not intact and no ACTIVE conciliation exists. */
  NONE,

  /** HMAC is not intact but an ACTIVE conciliation fingerprint matches the live entry. */
  ACKNOWLEDGED,

  /** HMAC is not intact and an ACTIVE conciliation fingerprint no longer matches the live entry. */
  RE_TAMPER_SUSPECTED
}
