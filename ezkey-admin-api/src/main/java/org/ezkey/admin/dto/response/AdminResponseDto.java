/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: AdminResponseDto
 * Description: Response DTO for administrator listing in admin API.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

/**
 * Response DTO for administrator listing in admin API.
 *
 * <p>This DTO represents administrator information returned by the admin API for listing
 * administrators. It includes comprehensive administrator details while excluding sensitive
 * information like recovery codes and enrollment credentials.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return administrator information to
 * administrators for monitoring and management purposes. Contains all non-sensitive data needed for
 * administrator administration.
 *
 * <p><b>Core Identification Fields:</b>
 *
 * <ul>
 *   <li><b>adminId:</b> Unique identifier for the administrator
 *   <li><b>username:</b> Username for the administrator
 *   <li><b>email:</b> Email address (optional)
 *   <li><b>phoneNumber:</b> Phone number (optional contact metadata)
 *   <li><b>firstName:</b> First name (optional)
 *   <li><b>lastName:</b> Last name (optional)
 * </ul>
 *
 * <p><b>Administrator Type and Scope:</b>
 *
 * <ul>
 *   <li><b>adminType:</b> Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN)
 *   <li><b>tenantId:</b> Tenant ID (null for global admins)
 *   <li><b>tenantName:</b> Tenant display name (null for global admins)
 *   <li><b>enrollmentId:</b> MFA enrollment ID for passwordless admin identity (null if not linked)
 * </ul>
 *
 * <p><b>Status Fields:</b>
 *
 * <ul>
 *   <li><b>active:</b> Flag indicating if the administrator is currently active
 *   <li><b>createdAt:</b> Timestamp when the administrator was created
 *   <li><b>lastLoginAt:</b> Timestamp of last successful login (null if never logged in)
 * </ul>
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive information like recovery codes, enrollment
 * proof tokens, and challenge codes. These are only available during provisioning.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param adminId Unique identifier for the administrator (auto-generated primary key)
 * @param version Optimistic lock version for PATCH concurrency control
 * @param username Username for the administrator
 * @param email Email address (optional, required for GLOBAL_ADMIN)
 * @param phoneNumber Phone number (optional contact metadata)
 * @param firstName First name (optional, required for GLOBAL_ADMIN)
 * @param lastName Last name (optional, required for GLOBAL_ADMIN)
 * @param adminType Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN, INTEGRATION_ADMIN)
 * @param tenantId Tenant ID (null for global admins)
 * @param tenantName Tenant display name (null for global admins)
 * @param enrollmentId MFA enrollment ID (null if not linked)
 * @param enrollmentName Display name of the linked MFA enrollment (null when not linked)
 * @param active Flag indicating if the administrator is currently active
 * @param lifecycleStatus Explicit lifecycle status (PENDING_ACTIVATION, ACTIVE, DEACTIVATED)
 * @param createdAt Timestamp when the administrator was created (with timezone)
 * @param lastLoginAt Timestamp of last successful login (null if never logged in)
 * @param hasRecoveryCodes Whether a recovery-code set currently exists for this administrator
 * @param operational Whether this administrator is fully operational (active and, for tenant
 *     admins, tenant also active)
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.integration.domain.entity.EzkeyAdmin
 */
@Schema(description = "Response DTO containing administrator information for listing purposes")
public record AdminResponseDto(
    @Schema(description = "Unique identifier for the administrator", example = "1") Integer adminId,
    @Schema(
            description =
                "Optimistic lock version. Include in PATCH requests to prevent concurrent update"
                    + " conflicts.",
            example = "0")
        Long version,
    @Schema(description = "Username for the administrator", example = "john.doe") String username,
    @Schema(description = "Email address", example = "john.doe@example.com") String email,
    @Schema(description = "Phone number stored in canonical E.164 format", example = "+15145551234")
        String phoneNumber,
    @Schema(description = "First name", example = "John") String firstName,
    @Schema(description = "Last name", example = "Doe") String lastName,
    @Schema(
            description = "Type of administrator",
            example = "TENANT_ADMIN",
            allowableValues = {"GLOBAL_ADMIN", "TENANT_ADMIN", "INTEGRATION_ADMIN"})
        String adminType,
    @Schema(description = "Tenant ID (null for global admins)", example = "1") Integer tenantId,
    @Schema(
            description =
                "Tenant display name from the tenant record (null for global administrators)",
            example = "Acme Corp",
            nullable = true)
        String tenantName,
    @Schema(
            description =
                "Enrollment ID for MFA (passwordless admin identity). Null if not linked to an"
                    + " enrollment.",
            example = "123",
            nullable = true)
        Integer enrollmentId,
    @Schema(
            description =
                "Human-readable name of the linked MFA enrollment (null when enrollmentId is"
                    + " null)",
            example = "admin.docker MFA",
            nullable = true)
        String enrollmentName,
    @Schema(
            description = "Flag indicating if the administrator is currently active",
            example = "true")
        Boolean active,
    @Schema(
            description = "Explicit lifecycle status for the administrator account",
            example = "ACTIVE",
            allowableValues = {"PENDING_ACTIVATION", "ACTIVE", "DEACTIVATED"})
        String lifecycleStatus,
    @Schema(
            description = "Timestamp when the administrator was created",
            example = "2025-10-15T14:30:00Z")
        OffsetDateTime createdAt,
    @Schema(
            description = "Timestamp of last successful login (null if never logged in)",
            example = "2025-10-20T09:15:00Z",
            nullable = true)
        OffsetDateTime lastLoginAt,
    @Schema(
            description =
                "Whether this administrator currently has a recovery-code set stored server-side",
            example = "false")
        Boolean hasRecoveryCodes,
    @Schema(
            description =
                "Whether this administrator is fully operational (active and, for tenant admins,"
                    + " tenant also active)",
            example = "true")
        Boolean operational) {}
