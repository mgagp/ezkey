/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: httpClient
 * Description: Shared Axios instance used for interacting with the Ezkey Admin and Auth APIs.
 * Security Context: Mirrors the transport expectations outlined in docs/features/AUTH_SECURITY.md by enforcing JSON
 *                   payloads, request timeouts, and providing a single extension point for future token binding.
 * @since 2025
 */

import axios from 'axios';
import {env} from '../../config/env';

/**
 * Preconfigured Axios instance targeting the Ezkey mobile API gateway.
 *
 * The configuration reflects mobile guidance described in `docs/CRYPTO.md`—callers should attach cryptographic
 * signatures to payloads handled elsewhere in the stack. Centralizing the client enables certificate pinning,
 * replay detection headers, or mutual TLS adoption without touching individual services.
 *
 * @since 2025
 */
export const httpClient = axios.create({
  baseURL: env.apiBaseUrl,
  timeout: env.requestTimeoutMs,
  headers: {
    'Content-Type': 'application/json',
  },
});

httpClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response) {
      return Promise.reject(error);
    }
    return Promise.reject(error);
  },
);
