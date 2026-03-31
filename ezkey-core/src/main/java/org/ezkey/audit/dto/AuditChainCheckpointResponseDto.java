/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AuditChainCheckpointResponseDto
 * Description: Response DTO for audit chain checkpoint search API.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * Response DTO for a single audit chain checkpoint in the search API.
 *
 * <p>Exposes all checkpoint fields for operator visibility, SEAL range selection, and Declare Gap
 * (anchor checkpoint) use cases. Timestamps are in UTC with Z suffix per API standard.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @author Ezkey contributors
 * @since 2026
 */
public record AuditChainCheckpointResponseDto(
    Long checkpointId,
    OffsetDateTime windowStart,
    OffsetDateTime windowEnd,
    int entryCount,
    Long firstEntryId,
    Long lastEntryId,
    String entriesDigest,
    String prevChainHmac,
    String chainHmac,
    OffsetDateTime createdAt,
    CheckpointType checkpointType,
    String notes) {}
