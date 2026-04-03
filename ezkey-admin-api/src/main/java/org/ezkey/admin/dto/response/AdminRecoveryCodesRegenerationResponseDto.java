/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminRecoveryCodesRegenerationResponseDto
 * Description: Response DTO for administrator recovery code regeneration.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * Response DTO for administrator recovery code regeneration.
 *
 * <p>This DTO returns a freshly generated set of recovery codes exactly once. The previous unused
 * recovery codes are invalidated immediately and only BCrypt hashes of the new codes are retained
 * at rest.
 *
 * @param adminId unique identifier for the administrator
 * @param username username for the administrator
 * @param recoveryCodes freshly generated plain-text recovery codes (shown once)
 * @param codesCount number of codes returned in this response
 * @param invalidatedPreviousCodes whether the prior remaining set was invalidated
 * @param message user-facing summary of the operation
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(
    description =
        "Response DTO for administrator recovery code regeneration. Plain-text recovery codes are"
            + " shown once and cannot be retrieved later.")
public record AdminRecoveryCodesRegenerationResponseDto(
    @Schema(description = "Unique identifier for the administrator", example = "42")
        Integer adminId,
    @Schema(description = "Username for the administrator", example = "tenant.admin")
        String username,
    @Schema(
            description =
                "New single-use recovery codes in plain text. Save them now; they will not be shown"
                    + " again.")
        List<String> recoveryCodes,
    @Schema(description = "Number of recovery codes returned", example = "5") Integer codesCount,
    @Schema(
            description = "Whether the previous remaining recovery codes were invalidated",
            example = "true")
        boolean invalidatedPreviousCodes,
    @Schema(
            description = "Summary message for the operator",
            example = "New recovery codes generated. Previous unused codes are no longer valid.")
        String message) {}
