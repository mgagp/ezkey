/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EvaluatorTempSessionRequestDto
 * Description: Request to mint a one-shot EVALUATOR_TEMP console session after activation.
 */

package org.ezkey.admin.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for minting a temporary evaluator console session (Mode C).
 *
 * <p>Authenticated by enrollment bind material returned from activation — not by a session cookie.
 *
 * @param enrollmentId first MFA enrollment id from activation
 * @param enrollmentProofToken plaintext enrollment proof token from activation (never logged)
 */
@Schema(description = "Mint a one-shot temporary evaluator console session after activation")
public record EvaluatorTempSessionRequestDto(
    @NotNull
        @Schema(
            description = "Enrollment ID returned by POST /admin/auth/activate",
            example = "42",
            requiredMode = RequiredMode.REQUIRED)
        Integer enrollmentId,
    @NotBlank
        @Schema(
            description =
                "Enrollment proof token returned by POST /admin/auth/activate (capability to mint)",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentProofToken) {}
