/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: AuditChainHeartbeatPhase
 * Description: Derived heartbeat supervision phase relative to the latest chain checkpoint.
 */

package org.ezkey.audit.integrity;

/**
 * Phase derived from comparing wall-clock time to the latest checkpoint window plus configured
 * grace and fail-closed thresholds.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public enum AuditChainHeartbeatPhase {

  /** Latest checkpoint is fresh enough that peripherals behave normally. */
  OK,

  /** No checkpoints yet shortly after process start — peripherals allowed until bootstrap grace. */
  BOOTSTRAPPING,

  /** Checkpoint is late but peripherals have not reached fail-closed yet. */
  UNSUPERVISED_ACTIVITY,

  /** Fail-closed threshold exceeded — peripherals must reject new MFA work. */
  DEGRADED_SERVICE
}
