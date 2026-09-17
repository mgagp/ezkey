/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentBindResponseDto
 * Description: Response DTO for enrollment binding information in auth API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode;

/**
 * Response DTO for enrollment binding information in auth API.
 *
 * <p>This DTO represents the response data returned to mobile devices when they initiate enrollment
 * binding. It contains all the information needed by the mobile device to complete the enrollment
 * process, including cryptographic keys and enrollment codes.
 *
 * <p><b>Usage Context:</b> Returned by auth-api when mobile devices request enrollment binding
 * information. The mobile app uses this data to generate its own cryptographic keys, sign the
 * enrollment code, and complete the enrollment verification process.
 *
 * <p><b>Cryptographic Flow:</b> Contains the integration's public key and signed enrollment code
 * that the mobile device must validate and respond to with its own generated keys and signature.
 *
 * <p><b>Project:</b> Ezkey - Open Source Cryptographic MFA Platform
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId The enrollment ID that was bound to the mobile device
 * @param integrationPublicKey The integration's public key for cryptographic verification
 * @param integrationKeyAlgorithm Algorithm for the integration public key (e.g. ed25519)
 * @param enrollmentProofToken The enrollment proof token to be signed by the device
 * @param integrationName The display name of the integration
 * @param integrationDescription The description of the integration
 * @param enrollmentName Person-facing enrollment label shown on the device (admin MFA uses
 *     first+last; do not encode Global/Tenant Admin here — see
 *     I-2026-09-15-mobile-admin-enrollment-account-label)
 * @param tenantId The tenant ID of the integration associated with this enrollment
 * @param tenantName The tenant display name of the integration associated with this enrollment
 * @param tenantDescription The tenant description of the integration associated with this
 *     enrollment
 * @param isSystemIntegration Whether this enrollment is admin MFA on the system integration
 * @param adminType {@code GLOBAL_ADMIN} or {@code TENANT_ADMIN} when linked to an administrator;
 *     absent for regular enrollments
 * @param enrollmentBindPayloadSignedByIntegration Ed25519 signature over the canonical bind payload
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentBindResponse
 * @see EnrollmentBindRequestDto
 * @see EnrollmentVerifyRequestDto
 */
@Schema(description = "Response DTO containing enrollment binding information")
public record EnrollmentBindResponseDto(
    @Schema(
            description = "Enrollment ID that was bound to the mobile device",
            example = "123",
            requiredMode = RequiredMode.REQUIRED)
        Integer enrollmentId,
    @Schema(
            description = "Integration's public key for cryptographic verification",
            example = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
            requiredMode = RequiredMode.REQUIRED)
        String integrationPublicKey,
    @Schema(
            description =
                "Algorithm for integrationPublicKey (ed25519: raw 32-byte key, Base64URL no"
                    + " padding)",
            example = "ed25519",
            requiredMode = RequiredMode.REQUIRED)
        String integrationKeyAlgorithm,
    @Schema(
            description = "Enrollment proof token to be signed by the device",
            example = "eyJhbGciOiJSUzI1NiJ9...",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentProofToken,
    @Schema(
            description = "Display name of the integration",
            example = "Acme Bank",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String integrationName,
    @Schema(
            description = "Description of the integration",
            example = "Acme Bank provides secure online banking services.",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String integrationDescription,
    @Schema(
            description =
                "Person-facing enrollment label shown on the device (for admin MFA: first and last"
                    + " name; not a role or username blob)",
            example = "Marie Dupont",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String enrollmentName,
    @Schema(
            description = "Tenant ID of the integration associated with this enrollment",
            example = "2",
            requiredMode = RequiredMode.NOT_REQUIRED)
        Integer tenantId,
    @Schema(
            description = "Tenant display name of the integration associated with this enrollment",
            example = "Acme Corp",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String tenantName,
    @Schema(
            description = "Tenant description of the integration associated with this enrollment",
            example = "Acme Corp tenant workspace",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String tenantDescription,
    @Schema(
            description =
                "True when this enrollment is administrator MFA on the system integration",
            example = "false",
            requiredMode = RequiredMode.REQUIRED)
        Boolean isSystemIntegration,
    @Schema(
            description =
                "Administrator type when this enrollment is admin MFA (GLOBAL_ADMIN or"
                    + " TENANT_ADMIN); absent for regular enrollments",
            example = "TENANT_ADMIN",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String adminType,
    @Schema(
            description =
                "Ed25519 signature (Base64URL, no padding, raw 64 bytes) over the canonical bind"
                    + " payload (see docs/ENROLLMENT_SIGNATURE_PAYLOAD.md)",
            example =
                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentBindPayloadSignedByIntegration) {}
