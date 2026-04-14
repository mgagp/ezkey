/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: instanceInfoApi
 * Description: Read-only mobile access to the public Ezkey installation metadata endpoint.
 * @since 2025
 */

import {httpClient} from './httpClient';
import {PublicInstanceInfoResponse} from './types';

/**
 * Lightweight wrapper around the public instance-info endpoint.
 *
 * @since 2025
 */
export const instanceInfoApi = {
  /**
   * Fetches read-only installation metadata for the target Auth API base URL.
   *
   * @param authUrl Optional per-enrollment Auth API base URL.
   * @return Public Ezkey installation metadata.
   * @since 2025
   */
  get: async (authUrl?: string) => {
    const response = await httpClient.get<PublicInstanceInfoResponse>(
      '/api/v1/public/instance-info',
      authUrl ? {baseURL: authUrl} : undefined,
    );
    return response.data;
  },
};