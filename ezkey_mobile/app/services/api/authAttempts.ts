/*
 * Ezkey - Open Source Cryptographic MFA Platform
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

import {
  PendingAuthRequest,
  PendingAuthResponse,
  RespondAuthRequest,
  RespondAuthResponse,
} from './types';
import {
  pending as pendingGenerated,
  respond as respondGenerated,
} from './generated/auth-api/authentication-attempts/authentication-attempts';

/**
 * Lightweight wrapper around the authentication attempt endpoints exposed to the mobile application.
 *
 * @since 2025
 */
export const authAttemptsApi = {
  
  isUsablePendingResponse: (value: unknown): value is PendingAuthResponse => {
    if (!value || typeof value !== 'object') {
      return false;
    }

    const candidate = value as Partial<PendingAuthResponse>;
    return (
      typeof candidate.authAttemptId === 'number' &&
      typeof candidate.authAttemptProofToken === 'string' &&
      candidate.authAttemptProofToken.length > 0 &&
      typeof candidate.authAttemptProofTokenSignedByIntegration === 'string' &&
      candidate.authAttemptProofTokenSignedByIntegration.length > 0
    );
  },

  /**
   * Retrieves a pending authentication attempt for the provided enrollment proof tokens.
   *
   * The payload must contain the cryptographic assertions described in `docs/features/AUTH_SECURITY.md` to prevent
   * enumeration and replay attacks.
   *
   * @param payload Pending auth request encapsulating proof tokens and signatures.
   * @param authUrl Optional per-enrollment Auth API base URL. When provided, overrides the global default.
   * @return Pending authentication attempt payload including integration signatures.
   * @since 2025
   */
  pending: async (payload: PendingAuthRequest, authUrl?: string) => {
    const body = {
      enrollmentId: Number(payload.enrollmentId),
      enrollmentProofToken: payload.enrollmentProofToken,
      deviceProofToken: payload.deviceProofToken,
      deviceProofTokenSigned: payload.deviceProofTokenSigned,
    };
    const response = await pendingGenerated(
      body,
      (authUrl ? {baseURL: authUrl} : undefined) as RequestInit | undefined,
    );

    if (response.status === 204) {
      return undefined;
    }

    return authAttemptsApi.isUsablePendingResponse(response.data)
      ? response.data
      : undefined;
  },
  /**
   * Submits the device decision (approve or reject) for a specific authentication attempt.
   *
   * The request requires the `authAttemptProofToken` signature generated with the device private key as mandated by
   * `docs/CRYPTO.md`.
   *
   * @param payload Respond request including signed proof token and optional challenge.
   * @param authUrl Optional per-enrollment Auth API base URL. When provided, overrides the global default.
   * @return Response acknowledgment indicating acceptance state.
   * @since 2025
   */
  respond: async (payload: RespondAuthRequest, authUrl?: string) => {
    const body = {
      authAttemptId: Number(payload.authAttemptId),
      authAttemptAccepted: payload.authAttemptAccepted,
      authAttemptProofTokenSignedByDevice: payload.authAttemptProofTokenSignedByDevice,
      ...(payload.authAttemptChallengeResponse != null
        ? {authAttemptChallengeResponse: Number(payload.authAttemptChallengeResponse)}
        : {}),
    };
    const response = await respondGenerated(
      body,
      (authUrl ? {baseURL: authUrl} : undefined) as RequestInit | undefined,
    );
    return response.data as RespondAuthResponse;
  },
};
