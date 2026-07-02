/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: ChainIntegrityViolation
 * Description: Structured checkpoint chain integrity failure for operator investigation.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * A checkpoint-level chain integrity failure within a verification window.
 *
 * @param checkpointId checkpoint primary key when applicable
 * @param windowStart checkpoint window start
 * @param windowEnd checkpoint window end
 * @param violationType machine-readable code (e.g. {@code ENTRIES_DIGEST_MISMATCH})
 * @param detail human-readable explanation (parity with legacy string violations)
 * @since 2026
 */
public record ChainIntegrityViolation(
    Long checkpointId,
    OffsetDateTime windowStart,
    OffsetDateTime windowEnd,
    String violationType,
    String detail) {}
