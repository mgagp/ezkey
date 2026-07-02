/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EntryIntegrityViolation
 * Description: Structured per-audit-log HMAC integrity failure for operator investigation.
 */

package org.ezkey.audit.dto;

import java.time.OffsetDateTime;

/**
 * A single audit log entry that failed HMAC verification.
 *
 * @param auditLogId primary key of the audit log row
 * @param eventType audit event type at time of verification
 * @param createdAt audit row timestamp
 * @param reason machine-readable failure code (e.g. {@code HMAC_MISMATCH}, {@code
 *     MISSING_ENTRY_HMAC})
 * @since 2026
 */
public record EntryIntegrityViolation(
    Long auditLogId, String eventType, OffsetDateTime createdAt, String reason) {}
