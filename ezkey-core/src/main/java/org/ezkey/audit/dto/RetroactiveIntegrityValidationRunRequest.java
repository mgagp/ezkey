/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: RetroactiveIntegrityValidationRunRequest
 * Description: Request body for operator-initiated retroactive integrity validation.
 */

package org.ezkey.audit.dto;

import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

/**
 * Request body for {@code POST /api/v1/audit-logs/integrity-validation/run}.
 *
 * @param from inclusive start of the validation window (ISO-8601 UTC)
 * @param to exclusive end of the validation window (ISO-8601 UTC)
 * @param raiseAlert when {@code true} or omitted, may raise/touch {@code AUDIT_INTEGRITY_RUPTURE};
 *     when {@code false}, completion audit only
 * @since 2026
 */
public record RetroactiveIntegrityValidationRunRequest(
    @NotNull OffsetDateTime from, @NotNull OffsetDateTime to, Boolean raiseAlert) {}
