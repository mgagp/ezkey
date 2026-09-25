/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrityAsyncJobAcceptedResponse
 * Description: Minimal 202 body after Integrity async job start.
 */

package org.ezkey.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

/**
 * Minimal body for {@code 202 Accepted} after starting an Integrity async job.
 *
 * @param jobId opaque job UUID
 * @since 2026
 */
@Schema(description = "Accepted Integrity async job (minimal body).")
public record IntegrityAsyncJobAcceptedResponse(
    @Schema(description = "Opaque job id", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID jobId) {}
