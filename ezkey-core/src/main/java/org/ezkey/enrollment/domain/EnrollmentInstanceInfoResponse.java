/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Domain: EnrollmentInstanceInfoResponse
 * Description: Integration-signed installation branding for enrolled Auth API clients.
 */

package org.ezkey.enrollment.domain;

/**
 * Integration-signed instance branding returned for an enrolled client.
 *
 * @param enrollmentId enrollment that authenticated the request
 * @param authApiPublicBaseUrl optional public Auth API base URL
 * @param instanceName instance / organization display name
 * @param instanceDescription optional description
 * @param aboutUrl optional about URL
 * @param instanceInfoPayloadSignedByIntegration Base64URL Ed25519 signature over the canonical
 *     INSTANCE_INFO payload
 * @since 2026
 */
public record EnrollmentInstanceInfoResponse(
    Integer enrollmentId,
    String authApiPublicBaseUrl,
    String instanceName,
    String instanceDescription,
    String aboutUrl,
    String instanceInfoPayloadSignedByIntegration) {}
