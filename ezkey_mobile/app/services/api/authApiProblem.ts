/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details returned by the Auth API for HTTP errors (application/problem+json).
 * Production UI shows origin `detail` only when `type` is under the Ezkey problem namespace.
 */

/** RFC 9457 problem type base for Ezkey Auth (and shared) catalog types. */
export const EZKEY_PROBLEM_TYPE_BASE = 'https://ezkey.io/problems';

const EZKEY_PROBLEM_TYPE_PREFIX = `${EZKEY_PROBLEM_TYPE_BASE}/`;

/**
 * Matches {@code AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_STATE_CONFLICT}
 * ({@code https://ezkey.io/problems/auth/auth-attempt-state-conflict}).
 */
export const AUTH_ATTEMPT_STATE_CONFLICT_TYPE = `${EZKEY_PROBLEM_TYPE_BASE}/auth/auth-attempt-state-conflict`;

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
 * User-facing Auth API error outcome for mobile screens.
 *
 * When {@link retryable} is false (auth-attempt state conflict / lost-response 409), the UI must
 * offer Back only — the server decision is already recorded; Approve/Deny/retry are wrong.
 */
export type AuthApiUserFacingOutcome = {
  message: string;
  retryable: boolean;
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
 * True when the Auth API reported {@link AUTH_ATTEMPT_STATE_CONFLICT_TYPE} (HTTP 409 typical).
 *
 * @param error thrown value (typically Axios)
 */
export function isAuthAttemptStateConflict(error: unknown): boolean {
  const problem = parseAuthApiProblemDetail(httpErrorBody(error));
  return problem?.type?.trim() === AUTH_ATTEMPT_STATE_CONFLICT_TYPE;
}

/**
 * Resolves user-visible Auth API error text and retryability.
 *
 * {@link AUTH_ATTEMPT_STATE_CONFLICT_TYPE} maps to a neutral, non-retryable outcome (decision
 * already recorded or attempt no longer active) — callers must show Back only (#833 Patrick).
 *
 * Other Ezkey problem types show origin {@code detail} when present. Cloudflare 1020 JSON,
 * {@code about:blank}, HTML, network failures, and other non-Ezkey bodies return {@code fallback}
 * as retryable.
 *
 * @param error thrown value (typically Axios)
 * @param fallback localized generic string (for example requestFailed)
 * @param stateConflictMessage localized inactive-request copy (FR/EN via i18n)
 */
export function resolveAuthApiUserFacingOutcome(
  error: unknown,
  fallback: string,
  stateConflictMessage: string,
): AuthApiUserFacingOutcome {
  const problem = parseAuthApiProblemDetail(httpErrorBody(error));
  if (problem?.type?.trim() === AUTH_ATTEMPT_STATE_CONFLICT_TYPE) {
    return {message: stateConflictMessage, retryable: false};
  }
  if (!isEzkeyProblemType(problem?.type)) {
    return {message: fallback, retryable: true};
  }
  const detail = typeof problem?.detail === 'string' ? problem.detail.trim() : '';
  return {message: detail.length > 0 ? detail : fallback, retryable: true};
}

/**
 * User-visible Auth API error text.
 *
 * Shows origin RFC 9457 {@code detail} only when {@code type} is under
 * {@link EZKEY_PROBLEM_TYPE_BASE}. Maps {@link AUTH_ATTEMPT_STATE_CONFLICT_TYPE} to
 * {@code stateConflictMessage} when provided (else {@code fallback}). Prefer
 * {@link resolveAuthApiUserFacingOutcome} when the UI must distinguish retryable vs Back-only.
 *
 * @param error thrown value (typically Axios)
 * @param fallback localized generic string (for example requestFailed)
 * @param stateConflictMessage optional localized inactive-request copy
 */
export function userFacingAuthApiError(
  error: unknown,
  fallback: string,
  stateConflictMessage?: string,
): string {
  return resolveAuthApiUserFacingOutcome(
    error,
    fallback,
    stateConflictMessage ?? fallback,
  ).message;
}
