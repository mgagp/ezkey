/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: CheckpointLifecycleState
 * Description: Operational lifecycle state for audit chain checkpoints.
 */

package org.ezkey.audit.integrity;

/**
 * Operational lifecycle state for audit chain checkpoints.
 *
 * <p>This state is independent from {@code checkpoint_type}. The checkpoint type describes the
 * continuity semantics of the checkpoint ({@code REGULAR}, {@code ARCHIVE_SEAL}, {@code
 * GAP_DECLARATION}), while lifecycle state describes where the checkpoint sits in the audit
 * archival lifecycle.
 *
 * @since 2026
 */
public enum CheckpointLifecycleState {
  ACTIVE,
  SEALED,
  EXPORTED,
  PURGEABLE,
  PURGED
}
