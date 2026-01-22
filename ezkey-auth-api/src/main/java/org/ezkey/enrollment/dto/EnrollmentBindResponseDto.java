/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId The enrollment ID that was bound to the mobile device
 * @param integrationPublicKey The integration's public key for cryptographic verification
 * @param enrollmentProofToken The enrollment proof token to be signed by the device
 * @param integrationLogo The logo URL or base64-encoded image for the integration
 * @param integrationName The display name of the integration
 * @param integrationDescription The description of the integration
 * @param enrollmentName The human-readable name for the enrollment
 * @param tenantId The tenant ID of the integration associated with this enrollment
 * @param tenantName The tenant display name of the integration associated with this enrollment
 * @param tenantDescription The tenant description of the integration associated with this
 *     enrollment
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
            description = "Enrollment proof token to be signed by the device",
            example = "eyJhbGciOiJSUzI1NiJ9...",
            requiredMode = RequiredMode.REQUIRED)
        String enrollmentProofToken,
    @Schema(
            description = "Logo URL or base64-encoded image for the integration",
            example = "https://acme.com/logo.png",
            requiredMode = RequiredMode.NOT_REQUIRED)
        String integrationLogo,
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
            description = "Human-readable name for the enrollment",
            example = "John's iPhone",
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
        String tenantDescription) {}
