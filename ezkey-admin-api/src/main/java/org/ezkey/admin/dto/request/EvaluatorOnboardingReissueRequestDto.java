/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EvaluatorOnboardingReissueRequestDto
 * Description: Request body for bounded public evaluator onboarding re-issue.
 */
package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Username for resuming incomplete evaluator onboarding after bootstrap session death.
 *
 * @param username generated evaluator Tenant Admin username from signup
 */
@Schema(description = "Anonymous evaluator onboarding re-issue request")
public record EvaluatorOnboardingReissueRequestDto(
    @Schema(
            description = "Evaluator Tenant Admin username from signup (e.g. eval-admin-a1b2c3d4)",
            example = "eval-admin-a1b2c3d4",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 80, message = "Username must be between 3 and 80 characters")
        String username) {}
