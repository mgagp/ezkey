/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: TenantActivateRequestDto
 * Description: Optional request body for tenant activation carrying the audit justification.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional request body for the tenant activation endpoint.
 *
 * <p>Carries an optional {@code reason} field that is recorded in the audit log to satisfy SOC 2
 * traceability requirements. When provided the reason must be between 10 and 500 characters.
 *
 * @author Ezkey contributors
 * @since 2025
 * @param reason optional human-readable justification for activating the tenant
 */
public record TenantActivateRequestDto(
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
        String reason) {}
