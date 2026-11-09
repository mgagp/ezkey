/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrationPendingAttemptPolicyRequestDto
 * Description: Request body for changing an integration pending-attempt policy.
 */

package org.ezkey.integration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.ezkey.integration.domain.PendingAttemptPolicy;

/**
 * Request body for changing an integration's pending-attempt policy.
 *
 * @param pendingAttemptPolicy REPLACE or REFUSE
 * @since 2026
 */
@Schema(description = "Request to change the pending-attempt policy of an integration")
public record IntegrationPendingAttemptPolicyRequestDto(
    @NotNull(message = "pendingAttemptPolicy is required")
        @Schema(description = "REPLACE or REFUSE", example = "REFUSE")
        PendingAttemptPolicy pendingAttemptPolicy) {}
