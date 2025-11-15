/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: authAttemptsApi
 * Description: HTTP client surface for the mobile authentication endpoints.
 * Security Context: Implements the polling and response workflow defined in docs/features/AUTH_SECURITY.md and
 *                   docs/ENDPOINT.md, preserving proof token secrecy and anti-replay semantics.
 * @since 2025
 */

import {httpClient} from './httpClient';
import {
  PendingAuthRequest,
  PendingAuthResponse,
  RespondAuthRequest,
  RespondAuthResponse,
} from './types';

const basePath = '/api/v1/auth-attempts';

/**
 * Lightweight wrapper around the authentication attempt endpoints exposed to the mobile application.
 *
 * @since 2025
 */
export const authAttemptsApi = {
  /**
   * Retrieves a pending authentication attempt for the provided enrollment proof tokens.
   *
   * The payload must contain the cryptographic assertions described in `docs/features/AUTH_SECURITY.md` to prevent
   * enumeration and replay attacks.
   *
   * @param payload Pending auth request encapsulating proof tokens and signatures.
   * @return Pending authentication attempt payload including integration signatures.
   * @since 2025
   */
  pending: async (payload: PendingAuthRequest) => {
    const response = await httpClient.post<PendingAuthResponse>(`${basePath}/pending`, payload);
    return response.data;
  },
  /**
   * Submits the device decision (approve or reject) for a specific authentication attempt.
   *
   * The request requires the `authAttemptProofToken` signature generated with the device private key as mandated by
   * `docs/CRYPTO.md`.
   *
   * @param payload Respond request including signed proof token and optional challenge.
   * @return Response acknowledgment indicating acceptance state.
   * @since 2025
   */
  respond: async (payload: RespondAuthRequest) => {
    const response = await httpClient.post<RespondAuthResponse>(`${basePath}/respond`, payload);
    return response.data;
  },
};
