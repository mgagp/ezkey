/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: DashboardIntegrationStatsDto
 * Description: Integration counts for dashboard overview.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Integration counts for the dashboard overview.
 *
 * <p>Provides total, active, and retired integration counts for the current scope (tenant or
 * instance).
 *
 * @param total total integrations in scope
 * @param active integrations in ACTIVE lifecycle status
 * @param retired integrations in RETIRED lifecycle status
 */
@Schema(description = "Integration counts for dashboard overview")
public record DashboardIntegrationStatsDto(
    @Schema(description = "Total integrations in scope") long total,
    @Schema(description = "Integrations in ACTIVE lifecycle status") long active,
    @Schema(description = "Integrations in RETIRED lifecycle status") long retired) {}
