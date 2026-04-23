/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AlertStatus
 * Description: Lifecycle state of an alert row.
 */

package org.ezkey.alert.domain;

/**
 * Lifecycle state of an alert row.
 *
 * <p>The alert subsystem keeps a deliberately minimal lifecycle: a row is either {@code OPEN}
 * (operator-visible, action may be required) or {@code RESOLVED} (kept for traceability and SOC
 * 2-oriented audit).
 *
 * @since 2026
 */
public enum AlertStatus {
  OPEN,
  RESOLVED
}
