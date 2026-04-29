/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: DeclareAuditChainIncidentRequest
 * Description: Body for declaring an audit-chain heartbeat operational incident after recovery.
 */

package org.ezkey.audit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.ezkey.audit.integrity.AuditChainIncidentRootCause;

/**
 * Declares closure for an incident awaiting operator narrative after heartbeat recovery.
 *
 * @param justification operator narrative for SOC 2 traceability (10–500 chars)
 * @param rootCause classified operator-selected cause bucket
 * @author Ezkey contributors
 * @since 2026
 */
public record DeclareAuditChainIncidentRequest(
    @NotBlank(message = "Justification is required")
        @Size(min = 10, max = 500, message = "Justification must be between 10 and 500 characters")
        String justification,
    @NotNull(message = "Root cause is required") AuditChainIncidentRootCause rootCause) {}
