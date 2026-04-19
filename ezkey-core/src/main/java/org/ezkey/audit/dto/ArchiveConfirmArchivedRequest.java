/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ArchiveConfirmArchivedRequest
 * Description: Request body for confirming that a sealed audit range has been archived externally.
 */

package org.ezkey.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

/**
 * Request body for confirming that a sealed audit range has been archived externally.
 *
 * <p>Exactly one identification mode must be used: either a timestamp range or a checkpoint ID
 * range. The request carries the deterministic bundle digest produced by the external archival
 * workflow so the backend can bind the confirmation to a specific exported artifact.
 *
 * @param periodStart start of the confirmed archived period (inclusive) in timestamp mode
 * @param periodEnd end of the confirmed archived period (exclusive) in timestamp mode
 * @param checkpointIdFrom first checkpoint identifier in ID mode
 * @param checkpointIdTo last checkpoint identifier in ID mode
 * @param exportBundleDigest deterministic digest of the exported archive bundle
 * @param archivedAt optional confirmation timestamp supplied by the archival workflow; when absent,
 *     the backend uses the current UTC instant
 * @since 2026
 */
public record ArchiveConfirmArchivedRequest(
    OffsetDateTime periodStart,
    OffsetDateTime periodEnd,
    Long checkpointIdFrom,
    Long checkpointIdTo,
    @NotBlank(message = "Export bundle digest is required")
        @Size(
            min = 16,
            max = 88,
            message = "Export bundle digest must be between 16 and 88 characters")
        String exportBundleDigest,
    OffsetDateTime archivedAt) {}
