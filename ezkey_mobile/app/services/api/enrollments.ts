/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: enrollmentsApi
 * Description: HTTP client surface for binding and verifying device enrollments.
 * Security Context: Enforces the secure enrollment flow outlined in docs/features/AUTH_SECURITY.md by transmitting
 *                   proof tokens over POST bodies and deferring signature validation to the backend.
 * @since 2025
 */

import {httpClient} from './httpClient';
import {
  BindEnrollmentRequest,
  BindEnrollmentResponse,
  VerifyEnrollmentRequest,
  VerifyEnrollmentResponse,
} from './types';

function verifyBody(payload: VerifyEnrollmentRequest) {
  return {
    enrollmentId: Number(payload.enrollmentId),
    challengeResponse: Number(payload.challengeResponse),
    devicePublicKey: payload.devicePublicKey,
    enrollmentProofTokenSigned: payload.enrollmentProofTokenSigned,
    ...(payload.devicePrivateKeyStorageTier != null
      ? {devicePrivateKeyStorageTier: payload.devicePrivateKeyStorageTier}
      : {}),
  };
}

const basePath = '/api/v1/enrollments';

/**
 * Enrollment API facade used by the mobile application.
 *
 * @since 2025
 */
export const enrollmentsApi = {
  /**
   * Initiates enrollment binding by exchanging the proof token for integration metadata and public keys.
   *
   * Refer to `docs/ENDPOINT.md` for payload semantics and security guarantees.
   *
   * @param payload Bind request containing enrollment proof token.
   * @param authUrl Optional per-enrollment Auth API base URL. When provided, overrides the global default.
   * @return Integration metadata necessary to complete enrollment verification.
   * @since 2025
   */
  bind: async (payload: BindEnrollmentRequest, authUrl?: string) => {
    const config = authUrl ? {baseURL: authUrl} : undefined;
    const body = {
      enrollmentId: Number(payload.enrollmentId),
      enrollmentProofToken: payload.enrollmentProofToken,
    };
    const response = await httpClient.post<BindEnrollmentResponse>(`${basePath}/bind`, body, config);
    return response.data;
  },
  /**
   * Finalizes enrollment by submitting the device public key, signed proof token, and challenge response.
   *
   * `challengeResponse` is required by the Auth API (`docs/ENDPOINT.md`) and must match the stored challenge.
   * The payload must comply with the cryptographic rules in `docs/CRYPTO.md` (device keys, signatures).
   *
   * @param payload Verify enrollment request carrying device credentials.
   * @param authUrl Optional per-enrollment Auth API base URL. When provided, overrides the global default.
   * @return Verification response indicating whether the enrollment is active.
   * @since 2025
   */
  verify: async (payload: VerifyEnrollmentRequest, authUrl?: string) => {
    const config = authUrl ? {baseURL: authUrl} : undefined;
    const body = verifyBody(payload);
    const response = await httpClient.post<VerifyEnrollmentResponse>(`${basePath}/verify`, body, config);
    return response.data;
  },
};
