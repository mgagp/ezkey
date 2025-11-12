/*
 * Ezkey - Open Source MFA/Passkey Alternative
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
   * @return Integration metadata necessary to complete enrollment verification.
   * @since 2025
   */
  bind: async (payload: BindEnrollmentRequest) => {
    const response = await httpClient.post<BindEnrollmentResponse>(`${basePath}/bind`, payload);
    return response.data;
  },
  /**
   * Finalizes enrollment by submitting the device public key and signed proof token.
   *
   * The payload must comply with the cryptographic rules in `docs/CRYPTO.md` (RSA-2048, SHA256withRSA).
   *
   * @param payload Verify enrollment request carrying device credentials.
   * @return Verification response indicating whether the enrollment is active.
   * @since 2025
   */
  verify: async (payload: VerifyEnrollmentRequest) => {
    const response = await httpClient.post<VerifyEnrollmentResponse>(`${basePath}/verify`, payload);
    return response.data;
  },
};
