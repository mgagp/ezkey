/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Record: EnrollmentResponseDto
 * Description: Response DTO for enrollment data in admin API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response DTO for enrollment data in admin API.
 *
 * <p>This DTO represents the complete enrollment information returned by the admin API for
 * administrative purposes. It provides comprehensive enrollment details including status,
 * configuration, and metadata while excluding sensitive cryptographic material for security
 * purposes.
 *
 * <p><b>Usage Context:</b> Used by admin API endpoints to return enrollment information to
 * administrators for monitoring and management purposes. Contains all non-sensitive data needed for
 * enrollment administration.
 *
 * <p><b>Core Identification Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentId:</b> Unique identifier for the enrollment
 *   <li><b>integrationId:</b> Integration identifier this enrollment belongs to
 *   <li><b>enrollmentName:</b> Human-readable name for the enrollment
 * </ul>
 *
 * <p><b>Status Fields:</b>
 *
 * <ul>
 *   <li><b>enrollmentStatus:</b> Enrollment lifecycle status (CREATED, BOUND, VERIFIED, INVALID)
 *   <li><b>enrollmentActive:</b> Flag indicating if the enrollment is currently active
 * </ul>
 *
 * <p><b>Authentication Configuration:</b>
 *
 * <ul>
 *   <li><b>enrollmentChallenge:</b> Challenge value for enrollment verification
 *   <li><b>enrollmentProofToken:</b> Unique code for enrollment verification
 *   <li><b>authAttemptChallengeRequired:</b> Flag indicating if authentication attempts require
 *       challenge
 * </ul>
 *
 * <p><b>Cryptographic Material (Public Keys Only):</b>
 *
 * <ul>
 *   <li><b>integrationPublicKey:</b> Public key for integration communication
 *   <li><b>devicePublicKey:</b> Public key for the device
 * </ul>
 *
 * <p><b>Security Note:</b> This DTO excludes sensitive cryptographic keys and provides only the
 * information necessary for administrative operations. All private keys are deliberately excluded
 * for security purposes.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId Unique identifier for the enrollment (auto-generated primary key)
 * @param integrationId Integration identifier this enrollment belongs to (foreign key reference)
 * @param enrollmentName Human-readable name for the enrollment (e.g., "John's iPhone")
 * @param enrollmentStatus Enrollment lifecycle status (CREATED, BOUND, VERIFIED, INVALID)
 * @param enrollmentActive Flag indicating if the enrollment is currently active
 * @param enrollmentChallenge Challenge value for enrollment verification
 * @param enrollmentProofToken Unique code for enrollment verification (e.g., "EZK-ABC123-DEF456")
 * @param authAttemptChallengeRequired Flag indicating if authentication attempts require challenge
 * @param integrationPublicKey Public key for integration communication
 * @param devicePublicKey Public key for the device
 * @param verifiedAt When enrollment was verified (device completed binding)
 * @param expiresAt Optional expiration for pending enrollment (null = no expiration)
 * @param createdByAdminId Admin who created this enrollment (null when via API key)
 * @param lastUsedAt When enrollment was last used for successful authentication
 * @param contactEmail Optional contact email for the end-user
 * @param contactPhoneNumber Optional contact phone number for the end-user
 * @param userIdentifier Optional user identifier from the integrating application
 * @param integrationName Display name for the enrollment's integration (e.g. "Ezkey System"); set
 *     when enrichment is used (e.g. GET by ID)
 * @param isSystemIntegration Whether this enrollment's integration is the system integration; set
 *     when enrichment is used (e.g. GET by ID)
 * @param operational Whether the enrollment is currently operational: VERIFIED status, active flag
 *     true, and (when integration context is available) full parent chain also operational
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.entity.Enrollment
 * @see EnrollmentCreateRequestDto
 * @see EnrollmentCreateResponseDto
 */
@Schema(
    description =
        "Response DTO containing complete enrollment information for administrative purposes")
public record EnrollmentResponseDto(
    @Schema(description = "Unique identifier for the enrollment", example = "123")
        Integer enrollmentId,
    @Schema(
            description =
                "Optimistic lock version. Include in PATCH requests to prevent concurrent update",
            example = "0")
        Long version,
    @Schema(description = "Integration identifier this enrollment belongs to", example = "1")
        Integer integrationId,
    @Schema(description = "Human-readable name for the enrollment", example = "John's iPhone")
        String enrollmentName,
    @Schema(
            description = "Enrollment lifecycle status",
            example = "VERIFIED",
            allowableValues = {"CREATED", "BOUND", "VERIFIED", "INVALID", "REVOKED", "EXPIRED"})
        String enrollmentStatus,
    @Schema(description = "Flag indicating if the enrollment is currently active", example = "true")
        Boolean enrollmentActive,
    @Schema(description = "Challenge value for enrollment verification", example = "123456")
        Integer enrollmentChallenge,
    @Schema(description = "Unique code for enrollment verification", example = "EZK-ABC123-DEF456")
        String enrollmentProofToken,
    @Schema(
            description = "Flag indicating if authentication attempts require challenge",
            example = "false")
        Boolean authAttemptChallengeRequired,
    @Schema(description = "Public key for integration communication") String integrationPublicKey,
    @Schema(description = "Public key for the device") String devicePublicKey,
    @Schema(
            description =
                "Client-reported device private key storage tier at verify (NONE, STANDARD,"
                    + " STRONG); null if unknown or legacy",
            allowableValues = {"NONE", "STANDARD", "STRONG"})
        String devicePrivateKeyStorageTier,
    @Schema(description = "When enrollment was verified (device completed binding)")
        java.time.OffsetDateTime verifiedAt,
    @Schema(
            description =
                "Optional expiration for pending enrollment (CREATED/BOUND); null = no expiration")
        java.time.OffsetDateTime expiresAt,
    @Schema(description = "When the enrollment row was created (audit / sorting)")
        java.time.OffsetDateTime createdAt,
    @Schema(description = "Admin who created this enrollment (null when via API key)")
        Integer createdByAdminId,
    @Schema(description = "When enrollment was last used for successful authentication")
        java.time.OffsetDateTime lastUsedAt,
    @Schema(description = "Optional contact email for the end-user") String contactEmail,
    @Schema(
            description =
                "Optional contact phone number for the end-user in canonical E.164 format")
        String contactPhoneNumber,
    @Schema(description = "Optional user identifier from the integrating application")
        String userIdentifier,
    @Schema(
            description =
                "When the enrollment was deactivated (reversible soft-disable); null if never")
        java.time.OffsetDateTime deactivatedAt,
    @Schema(description = "Admin who deactivated this enrollment; null if never deactivated")
        Integer deactivatedByAdminId,
    @Schema(description = "When the enrollment was permanently revoked; null if never")
        java.time.OffsetDateTime revokedAt,
    @Schema(description = "Admin who revoked this enrollment; null if never revoked")
        Integer revokedByAdminId,
    @Schema(
            description =
                "Display name for the enrollment's integration (e.g. Ezkey System); populated on"
                    + " GET by ID")
        String integrationName,
    @Schema(
            description =
                "Whether this enrollment's integration is the system integration; populated on GET"
                    + " by ID")
        Boolean isSystemIntegration,
    @Schema(
            description =
                "Whether the enrollment is currently operational: VERIFIED status, active flag"
                    + " true, and (when integration context is available) full parent chain also"
                    + " operational",
            example = "true")
        Boolean operational) {}
