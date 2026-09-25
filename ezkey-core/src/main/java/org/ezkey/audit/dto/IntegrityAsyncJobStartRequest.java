/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrityAsyncJobStartRequest
 * Description: Request body to start an Integrity async job.
 */

package org.ezkey.audit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import org.ezkey.audit.asyncjob.IntegrityAsyncJobType;

/**
 * Request body for {@code POST …/integrity/jobs}.
 *
 * @param type job kind
 * @param from inclusive window start (required for range jobs)
 * @param to exclusive window end (required for range jobs)
 * @param raiseAlert optional; for {@code RUN_VALIDATION} only (default true)
 * @since 2026
 */
@Schema(description = "Start an Integrity async job on the single global slot.")
public record IntegrityAsyncJobStartRequest(
    @NotNull @Schema(description = "Job type", requiredMode = Schema.RequiredMode.REQUIRED)
        IntegrityAsyncJobType type,
    @NotNull
        @Schema(
            description = "Inclusive window start (ISO-8601)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime from,
    @NotNull
        @Schema(
            description = "Exclusive window end (ISO-8601)",
            requiredMode = Schema.RequiredMode.REQUIRED)
        OffsetDateTime to,
    @Schema(
            description =
                "When type is RUN_VALIDATION, whether to raise/touch alert on eligible violations."
                    + " Default true. Ignored for verify job types.")
        Boolean raiseAlert) {}
