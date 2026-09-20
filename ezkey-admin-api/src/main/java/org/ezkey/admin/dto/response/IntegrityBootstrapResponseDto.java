/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: IntegrityBootstrapResponseDto
 * Description: Thin Integrity atelier bootstrap (runtime profile + monitoring enable flags).
 */
package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Thin Integrity atelier bootstrap payload for Admin UI honesty chrome.
 *
 * <p><b>Dual source (do not collapse):</b>
 *
 * <ul>
 *   <li>{@code runtimeProfile} — product label from the live Spring runtime ({@code docker-base} →
 *       {@code base}; otherwise {@code integrity}). Not derived from enable flags.
 *   <li>{@code chainCheckpointsEnabled} / {@code nightlyValidationEnabled} — live config flags that
 *       drive monitoring-off copy and the inactive badge. Not derived from the profile name alone
 *       (an integrity profile can still disable jobs via config).
 * </ul>
 *
 * <p>Global Admin only. No job matrix, no second journal.
 *
 * @param runtimeProfile product runtime profile {@code base} or {@code integrity}
 * @param chainCheckpointsEnabled whether rolling audit chain checkpoints are enabled
 * @param nightlyValidationEnabled whether nightly retroactive integrity validation is enabled
 * @since 2026
 */
@Schema(
    description =
        "Thin Integrity atelier bootstrap: product runtime profile plus monitoring enable flags"
            + " (non-secret). Global Admin only.")
public record IntegrityBootstrapResponseDto(
    @Schema(
            description =
                "Product runtime profile from Spring environment (docker-base → base; else"
                    + " integrity). Not derived from enable flags.",
            allowableValues = {"base", "integrity"},
            example = "integrity")
        String runtimeProfile,
    @Schema(
            description =
                "Whether rolling audit chain checkpoints are enabled (config flag for monitoring"
                    + " honesty).")
        boolean chainCheckpointsEnabled,
    @Schema(
            description =
                "Whether nightly retroactive integrity validation is enabled (config flag for"
                    + " monitoring honesty).")
        boolean nightlyValidationEnabled) {}
