/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details returned by the Auth API for HTTP errors (application/problem+json).
 */

/**
 * Shape of RFC 9457 Problem Details from the Auth API (extension properties allowed).
 */
export type AuthApiProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  path?: string;
  timestamp?: string;
};

/**
 * Parses axios error response data as an Auth API Problem Detail when present.
 */
export function parseAuthApiProblemDetail(data: unknown): AuthApiProblemDetail | null {
  if (data === null || typeof data !== 'object') {
    return null;
  }
  const o = data as Record<string, unknown>;
  if (typeof o.title !== 'string' && typeof o.type !== 'string') {
    return null;
  }
  return o as AuthApiProblemDetail;
}
