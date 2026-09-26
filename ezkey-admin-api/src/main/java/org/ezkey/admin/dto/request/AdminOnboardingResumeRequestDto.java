/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminOnboardingResumeRequestDto
 * Description: Request body for redeeming an onboarding-resume secret.
 */
package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Opaque onboarding-resume secret from activation (capability redeem).
 *
 * @param onboardingResumeSecret plaintext resume secret minted at activate
 */
@Schema(description = "Onboarding-resume redeem request")
public record AdminOnboardingResumeRequestDto(
    @Schema(
            description =
                "Opaque onboarding-resume secret from activation (ezkey_onboarding_resume_…)",
            example = "ezkey_onboarding_resume_…",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Onboarding resume secret is required")
        @Size(min = 20, max = 200, message = "Onboarding resume secret length is invalid")
        String onboardingResumeSecret) {}
