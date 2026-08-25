/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: TenantDeactivateRequestDto
 * Description: Optional request body for tenant deactivation carrying the audit justification.
 */

package org.ezkey.admin.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Optional request body for the tenant deactivation endpoint.
 *
 * <p>Carries an optional {@code reason} field that is recorded in the audit log for
 * reason/justification on sensitive change (who/what/when, and why when provided). When provided
 * the reason must be between 10 and 500 characters so that it is descriptive enough to be
 * operationally useful.
 *
 * @author Ezkey contributors
 * @since 2025
 * @param reason optional human-readable justification for deactivating the tenant
 */
public record TenantDeactivateRequestDto(
    @Size(min = 10, max = 500, message = "Reason must be between 10 and 500 characters")
        String reason) {}
