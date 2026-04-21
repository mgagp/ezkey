/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: AdminProvisioningResponseDto
 * Description: Response DTO for administrator provisioning with onboarding credentials.
 */

package org.ezkey.admin.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for administrator provisioning.
 *
 * <p>This DTO contains basic information about the newly created administrator. Enrollment proof
 * token and challenge are still retrieved via GET /api/v1/admins/{id}/onboarding.
 *
 * <p><b>Recovery codes:</b> Recovery codes are generated server-side when the first enrollment is
 * created, but plain recovery codes are intentionally omitted from this bootstrap response. They
 * should be revealed later through an authenticated recovery-code management flow.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param adminId Unique identifier for the administrator
 * @param username Username for the administrator
 * @param email Email address
 * @param phoneNumber Phone number in canonical E.164 format
 * @param firstName First name
 * @param lastName Last name
 * @param adminType Type of administrator (GLOBAL_ADMIN, TENANT_ADMIN)
 * @param tenantId Tenant ID (null for global admins)
 * @param enrollmentId Enrollment ID for passwordless authentication when immediate onboarding is
 *     used; null for activation-code onboarding
 * @param lifecycleStatus Lifecycle status of the newly created administrator
 * @param onboardingMode Effective onboarding mode used during provisioning
 * @param activationCode One-time activation code shown once for deferred onboarding; null for
 *     immediate onboarding
 * @param activationCodeExpiresAt Expiration timestamp for the one-time activation code; null for
 *     immediate onboarding
 * @param createdAt Timestamp when the administrator was created
 * @author Ezkey contributors
 * @since 2025
 */
@Schema(
    description =
        "Response DTO for administrator provisioning. Immediate onboarding returns an"
            + " enrollmentId. Activation-code onboarding returns a one-time activation code"
            + " instead and leaves enrollmentId null until activation succeeds. Recovery codes"
            + " remain deferred in both bootstrap variants.")
public record AdminProvisioningResponseDto(
    @Schema(description = "Unique identifier for the administrator", example = "1") Integer adminId,
    @Schema(description = "Username for the administrator", example = "john.doe") String username,
    @Schema(description = "Email address", example = "john.doe@example.com") String email,
    @Schema(description = "Phone number", example = "+15145551234") String phoneNumber,
    @Schema(description = "First name", example = "John") String firstName,
    @Schema(description = "Last name", example = "Doe") String lastName,
    @Schema(
            description = "Type of administrator",
            example = "GLOBAL_ADMIN",
            allowableValues = {"GLOBAL_ADMIN", "TENANT_ADMIN"})
        String adminType,
    @Schema(description = "Tenant ID (null for global admins)", example = "1") Integer tenantId,
    @Schema(
            description =
                "Enrollment ID for passwordless authentication when immediate onboarding is used."
                    + " Null for activation-code onboarding until activation creates the first"
                    + " enrollment.",
            example = "123")
        Integer enrollmentId,
    @Schema(
            description = "Lifecycle status of the newly created administrator",
            example = "PENDING_ACTIVATION",
            allowableValues = {"PENDING_ACTIVATION", "ACTIVE", "DEACTIVATED"})
        String lifecycleStatus,
    @Schema(
            description = "Effective onboarding mode used during provisioning",
            example = "ACTIVATION_CODE",
            allowableValues = {"IMMEDIATE", "ACTIVATION_CODE"})
        String onboardingMode,
    @Schema(
            description =
                "One-time activation code shown once for deferred onboarding. Null for immediate"
                    + " onboarding.")
        String activationCode,
    @Schema(
            description =
                "Expiration timestamp for the activation code when deferred onboarding is used."
                    + " Null for immediate onboarding.",
            example = "2026-04-27T14:30:00Z")
        OffsetDateTime activationCodeExpiresAt,
    @Schema(
            description = "Timestamp when the administrator was created",
            example = "2025-10-15T14:30:00Z")
        OffsetDateTime createdAt,
    @Schema(
            description =
                "Single-use recovery codes are intentionally omitted from bootstrap responses and"
                    + " must be revealed later through an authenticated recovery-code"
                    + " management flow.")
        List<String> recoveryCodes) {}
