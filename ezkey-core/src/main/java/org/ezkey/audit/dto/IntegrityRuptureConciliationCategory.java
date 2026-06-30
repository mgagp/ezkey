/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: IntegrityRuptureConciliationCategory
 * Description: Operator classification for an integrity rupture reconciliation.
 */

package org.ezkey.audit.dto;

/**
 * Operator-facing category for an {@code AUDIT_INTEGRITY_RUPTURE} reconciliation.
 *
 * <p>All categories share the same cryptographic bridge path; the category distinguishes intent for
 * SOC 2 traceability.
 *
 * @since 2026
 */
public enum IntegrityRuptureConciliationCategory {
  /** Suspected or confirmed accidental database operator edit. */
  ACCIDENTAL_DBA_EDIT,

  /** Storage or filesystem corruption investigated as benign. */
  CORRUPTION,

  /** Investigated and accepted as benign false alarm. */
  INVESTIGATED_BENIGN,

  /** Other; justification must explain. */
  OTHER
}
