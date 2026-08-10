/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: instanceInfoApi
 * Description: Enrolled signed instance-info (Auth API) and legacy public GET helper.
 * @since 2025
 */

import {cryptoService} from '../crypto';
import {buildInstanceInfoPayload} from '../crypto/enrollmentPayload';
import {httpClient} from './httpClient';
import {PublicInstanceInfoResponse} from './types';

/**
 * Auth API response for POST /api/v1/enrollments/instance-info.
 */
export type EnrollmentInstanceInfoResponse = {
  enrollmentId: number;
  authApiPublicBaseUrl?: string | null;
  instanceName?: string | null;
  instanceDescription?: string | null;
  aboutUrl?: string | null;
  instanceInfoPayloadSignedByIntegration: string;
};

export type FetchVerifiedInstanceInfoParams = {
  authUrl?: string;
  enrollmentProofToken: string;
  integrationPublicKey: string;
};

/**
 * Lightweight wrapper around Auth API instance-info endpoints.
 *
 * Enrolled clients must use {@link fetchVerifiedInstanceInfo} (signed POST). The public GET is
 * retained only for non-enrolled / operator tooling and must not be used as a fallback on the
 * enrolled path.
 *
 * @since 2025
 */
export const instanceInfoApi = {
  /**
   * Fetches unsigned public installation metadata (not for enrolled refresh paths).
   *
   * @param authUrl Optional Auth API base URL.
   * @return Public Ezkey installation metadata.
   */
  get: async (authUrl?: string) => {
    const response = await httpClient.get<PublicInstanceInfoResponse>(
      '/api/v1/public/instance-info',
      authUrl ? {baseURL: authUrl} : undefined,
    );
    return response.data;
  },

  /**
   * Fetches integration-signed installation branding for an enrolled client.
   *
   * @param enrollmentProofToken Enrollment proof token.
   * @param authUrl Optional Auth API base URL.
   * @return Signed branding response (caller must verify the signature).
   */
  getSigned: async (enrollmentProofToken: string, authUrl?: string) => {
    const response = await httpClient.post<EnrollmentInstanceInfoResponse>(
      '/api/v1/enrollments/instance-info',
      {enrollmentProofToken},
      authUrl ? {baseURL: authUrl} : undefined,
    );
    return response.data;
  },
};

/**
 * Fetches enrolled instance-info and verifies the integration Ed25519 signature before returning
 * branding fields. Returns null on network/HTTP failure or signature failure (fail-closed on apply).
 *
 * @param params Auth URL, proof token, and stored integration public key.
 * @return Verified branding fields, or null when branding must not be applied.
 */
export async function fetchVerifiedInstanceInfo(
  params: FetchVerifiedInstanceInfoParams,
): Promise<PublicInstanceInfoResponse | null> {
  const {authUrl, enrollmentProofToken, integrationPublicKey} = params;
  if (!enrollmentProofToken || !integrationPublicKey) {
    console.warn('[instanceInfo] Missing proof token or integration public key');
    return null;
  }

  let response: EnrollmentInstanceInfoResponse;
  try {
    response = await instanceInfoApi.getSigned(enrollmentProofToken, authUrl);
  } catch (error) {
    console.warn('[instanceInfo] Signed instance-info request failed:', error);
    return null;
  }

  if (!response?.instanceInfoPayloadSignedByIntegration) {
    console.warn('[instanceInfo] Missing instanceInfoPayloadSignedByIntegration');
    return null;
  }

  const payload = buildInstanceInfoPayload({
    enrollmentProofToken,
    enrollmentId: response.enrollmentId,
    authApiPublicBaseUrl: response.authApiPublicBaseUrl,
    instanceName: response.instanceName,
    instanceDescription: response.instanceDescription,
    aboutUrl: response.aboutUrl,
  });

  const signatureOk = await cryptoService.verify(
    payload,
    response.instanceInfoPayloadSignedByIntegration,
    integrationPublicKey,
  );
  if (!signatureOk) {
    console.warn('[instanceInfo] Instance-info signature verification failed');
    return null;
  }

  return {
    authApiPublicBaseUrl: response.authApiPublicBaseUrl,
    instanceName: response.instanceName,
    instanceDescription: response.instanceDescription,
    aboutUrl: response.aboutUrl,
  };
}
