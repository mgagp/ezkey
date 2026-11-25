/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AlertResolveRequestDto
 * Description: Optional request body for manual alert resolution.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional request body for manual alert resolution.
 *
 * @param note optional short operator note stored in the audit trail
 * @since 2026
 */
public record AlertResolveRequestDto(
    @Size(max = 500, message = "Note must not exceed 500 characters") String note) {}
