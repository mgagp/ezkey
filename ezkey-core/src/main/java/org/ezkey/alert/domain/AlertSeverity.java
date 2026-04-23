/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AlertSeverity
 * Description: Operator-facing severity ladder for alerts.
 */

package org.ezkey.alert.domain;

/**
 * Operator-facing severity ladder for alerts.
 *
 * <p>Kept intentionally small (three levels) to stay pragmatic: {@code INFO} for awareness-only
 * signals, {@code WARNING} for action required without immediate security impact, {@code CRITICAL}
 * for incidents requiring immediate attention.
 *
 * @since 2026
 */
public enum AlertSeverity {
  INFO,
  WARNING,
  CRITICAL
}
