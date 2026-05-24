/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EvaluatorSelfRegistrationRequestDto
 * Description: Request body for anonymous evaluator self-registration.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * Optional label for an anonymous evaluator preview tenant.
 *
 * @param tenantLabel optional short display label (max 40 characters; no email or URL)
 */
@Schema(description = "Anonymous evaluator self-registration request")
public record EvaluatorSelfRegistrationRequestDto(
    @Schema(
            description =
                "Optional short label for the preview tenant (max 40 characters; no email or URL)",
            example = "My preview workspace",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 40, message = "Tenant label must not exceed 40 characters")
        String tenantLabel) {}
