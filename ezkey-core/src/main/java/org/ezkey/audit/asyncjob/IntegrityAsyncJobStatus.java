/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: IntegrityAsyncJobStatus
 * Description: Lifecycle status for Integrity async operator jobs.
 */

package org.ezkey.audit.asyncjob;

/**
 * Lifecycle status for Integrity async operator jobs.
 *
 * <p>RUNNING is not "healthy OK" — it only means work is in flight. EXPIRED means heartbeat TTL
 * elapsed. INTERRUPTED means process restart left a RUNNING row.
 *
 * @since 2026
 */
public enum IntegrityAsyncJobStatus {
  RUNNING,
  SUCCEEDED,
  FAILED,
  CANCELLED,
  EXPIRED,
  INTERRUPTED
}
