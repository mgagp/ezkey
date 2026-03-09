/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Enum: CheckpointType
 * Description: Lifecycle type of an audit chain checkpoint for API representation.
 */

package org.ezkey.audit.dto;

/**
 * Lifecycle type of an audit chain checkpoint.
 *
 * <p>Matches the database {@code checkpoint_type} values in {@code ezkey_audit_chain_checkpoint}:
 *
 * <ul>
 *   <li>{@code REGULAR} – normal scheduler-created 5-minute window
 *   <li>{@code ARCHIVE_SEAL} – entries archived to external storage; entries_digest not
 *       re-verifiable
 *   <li>{@code GAP_DECLARATION} – admin-declared downtime gap
 * </ul>
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public enum CheckpointType {
  REGULAR,
  ARCHIVE_SEAL,
  GAP_DECLARATION
}
