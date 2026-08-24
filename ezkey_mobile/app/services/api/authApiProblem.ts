/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details returned by the Auth API for HTTP errors (application/problem+json).
 * Production UI shows origin `detail` only when `type` is under the Ezkey problem namespace.
 */

/** RFC 9457 problem type base for Ezkey Auth (and shared) catalog types. */
export const EZKEY_PROBLEM_TYPE_BASE = 'https://ezkey.io/problems';

const EZKEY_PROBLEM_TYPE_PREFIX = `${EZKEY_PROBLEM_TYPE_BASE}/`;

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
 * Structural only: any RFC 9457-shaped body is accepted, including non-Ezkey types.
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

/**
 * Returns true when {@code type} is under the Ezkey problem catalog namespace.
 *
 * @param type RFC 9457 problem type URI
 */
export function isEzkeyProblemType(type: string | undefined | null): boolean {
  if (typeof type !== 'string') {
    return false;
  }
  const trimmed = type.trim();
  return trimmed.startsWith(EZKEY_PROBLEM_TYPE_PREFIX) && trimmed.length > EZKEY_PROBLEM_TYPE_PREFIX.length;
}

function httpErrorBody(error: unknown): unknown {
  if (error == null || typeof error !== 'object' || !('response' in error)) {
    return undefined;
  }
  const response = (error as {response?: {data?: unknown}}).response;
  return response?.data;
}

/**
 * User-visible Auth API error text.
 *
 * Shows origin RFC 9457 {@code detail} only when {@code type} is under
 * {@link EZKEY_PROBLEM_TYPE_BASE}. Cloudflare 1020 JSON, {@code about:blank},
 * HTML, network failures, and other non-Ezkey bodies return {@code fallback}.
 *
 * @param error thrown value (typically Axios)
 * @param fallback localized generic string (for example requestFailed)
 */
export function userFacingAuthApiError(error: unknown, fallback: string): string {
  const problem = parseAuthApiProblemDetail(httpErrorBody(error));
  if (!isEzkeyProblemType(problem?.type)) {
    return fallback;
  }
  const detail = typeof problem?.detail === 'string' ? problem.detail.trim() : '';
  return detail.length > 0 ? detail : fallback;
}
