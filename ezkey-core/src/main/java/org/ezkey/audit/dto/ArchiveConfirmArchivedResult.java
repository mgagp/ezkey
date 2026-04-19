/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ArchiveConfirmArchivedResult
 * Description: Result of confirming that a sealed audit range has been archived externally.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * Result of confirming that a sealed audit range has been archived externally.
 *
 * @param periodStart start of the confirmed archived period (inclusive)
 * @param periodEnd end of the confirmed archived period (exclusive)
 * @param checkpointsExported number of checkpoints transitioned to {@code EXPORTED}
 * @param exportBundleDigest deterministic digest of the exported archive bundle
 * @param exportedAt effective confirmation timestamp recorded by the backend
 * @param auditLogId identifier of the meta-audit entry created for the confirmation
 * @since 2026
 */
public record ArchiveConfirmArchivedResult(
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    int checkpointsExported,
    String exportBundleDigest,
    OffsetDateTime exportedAt,
    Long auditLogId) {}
