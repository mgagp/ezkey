/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * DTO: EnrollmentVerifyRequestDto
 * Description: Request DTO for enrollment verification completion in auth API.
 */

package org.ezkey.enrollment.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request DTO for enrollment verification completion in auth API.
 *
 * <p>This DTO represents the request data sent by mobile devices to complete the enrollment
 * verification process. It contains the device's generated cryptographic keys, signed enrollment
 * code, and challenge response that finalize the enrollment and activate the device for MFA
 * authentication.
 *
 * <p><b>Usage Context:</b> Used by mobile devices to complete enrollment with the auth-api after
 * receiving binding information. The mobile app generates its cryptographic key pair, signs the
 * enrollment code, and submits this verification request to activate the enrollment.
 *
 * <p><b>Cryptographic Completion:</b> Contains the mobile device's public key and signature of the
 * enrollment code, proving that the device has the corresponding private key and can participate in
 * future authentication flows.
 *
 * <p><b>Project:</b> Ezkey - Open Source MFA/Passkey Alternative
 *
 * <p><b>License:</b> MIT
 *
 * @param enrollmentId The enrollment ID being verified
 * @param challengeResponse User's response to the enrollment challenge
 * @param devicePublicKey The mobile device's generated public key
 * @param enrollmentProofTokenSigned Device-signed enrollment proof token
 * @author Ezkey contributors
 * @since 2025
 * @see org.ezkey.enrollment.domain.EnrollmentVerifyRequest
 * @see EnrollmentBindResponseDto
 * @see EnrollmentVerifyResponseDto
 */
@Schema(description = "Request DTO for enrollment verification completion")
public record EnrollmentVerifyRequestDto(
    @Schema(description = "Enrollment ID being verified", example = "123", required = true)
        Integer enrollmentId,
    @Schema(
            description = "User's response to the enrollment challenge",
            example = "123456",
            required = true)
        Integer challengeResponse,
    @Schema(
            description = "Mobile device's generated public key",
            example = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...",
            required = true)
        String devicePublicKey,
    @Schema(
            description = "Device-signed enrollment proof token",
            example = "eyJhbGciOiJSUzI1NiJ9...",
            required = true)
        String enrollmentProofTokenSigned) {}
