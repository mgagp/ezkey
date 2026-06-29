/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: ScheduledJobLastRunStatus
 * Description: Outcome of the most recent scheduled job execution.
 */

package org.ezkey.audit.integrity;

/**
 * Outcome recorded for the most recent scheduled job execution.
 *
 * @since 2026
 */
public enum ScheduledJobLastRunStatus {
  SUCCESS,
  FAILED,
  NEVER_RUN
}
