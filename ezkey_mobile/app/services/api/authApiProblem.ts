/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * RFC 9457 Problem Details returned by the Auth API for HTTP errors (application/problem+json).
 * Production UI never shows origin {@code detail}; only local catalog strings chosen by {@code type}
 * (#844).
 */

/** RFC 9457 problem type base for Ezkey Auth (and shared) catalog types. */
export const EZKEY_PROBLEM_TYPE_BASE = 'https://ezkey.io/problems';

const EZKEY_PROBLEM_TYPE_PREFIX = `${EZKEY_PROBLEM_TYPE_BASE}/`;

/**
 * Matches {@code AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_STATE_CONFLICT}
 * ({@code https://ezkey.io/problems/auth/auth-attempt-state-conflict}).
 *
 * On {@code /respond}, not-found / PENDING / terminal all emit this type (#844 Marc m1).
 */
export const AUTH_ATTEMPT_STATE_CONFLICT_TYPE = `${EZKEY_PROBLEM_TYPE_BASE}/auth/auth-attempt-state-conflict`;

/**
 * Matches {@code AuthApiProblemCatalog.TYPE_AUTH_ATTEMPT_EXPIRED} (#895 Patrick M1).
 * Wall-clock expiry after {@code expiresAt} — distinct from state-conflict.
 */
export const AUTH_ATTEMPT_EXPIRED_TYPE = `${EZKEY_PROBLEM_TYPE_BASE}/auth/auth-attempt-expired`;

/**
 * Matches {@code AuthApiProblemCatalog.TYPE_INTEGRATION_SIGNING_UNAVAILABLE} (#844).
 */
export const INTEGRATION_SIGNING_UNAVAILABLE_TYPE = `${EZKEY_PROBLEM_TYPE_BASE}/auth/integration-signing-unavailable`;

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
 * Local catalog keys for Auth API problem types (EN/FR via i18n). Never display server
 * {@code detail} (#844). Respond not-found / PENDING / terminal share {@link stateConflict}.
 */
export type AuthApiProblemCatalogKey =
  | 'stateConflict'
  | 'attemptExpired'
  | 'integrationSigningUnavailable'
  | 'generic';

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
 * Localized strings for Auth API problem catalog keys. Callers supply EN/FR via i18n.
 */
export type AuthApiProblemCatalogMessages = {
  stateConflict: string;
  /** Neutral expired copy for {@link AUTH_ATTEMPT_EXPIRED_TYPE} (#895). */
  attemptExpired: string;
  integrationSigningUnavailable: string;
  /** Generic fallback for unknown Ezkey types and non-Ezkey bodies. */
  fallback: string;
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

/**
 * Maps an Ezkey problem {@code type} URI to a local catalog key. Unknown Ezkey types use
 * {@code generic}. Server {@code detail} is never used (#844).
 *
 * @param type RFC 9457 problem type URI
 */
export function catalogKeyForAuthApiProblemType(
  type: string | undefined | null,
): AuthApiProblemCatalogKey {
  const trimmed = typeof type === 'string' ? type.trim() : '';
  switch (trimmed) {
    case AUTH_ATTEMPT_STATE_CONFLICT_TYPE:
      return 'stateConflict';
    case AUTH_ATTEMPT_EXPIRED_TYPE:
      return 'attemptExpired';
    case INTEGRATION_SIGNING_UNAVAILABLE_TYPE:
      return 'integrationSigningUnavailable';
    default:
      return 'generic';
  }
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

function normalizeCatalog(
  fallbackOrCatalog: string | AuthApiProblemCatalogMessages,
  stateConflictMessage?: string,
): AuthApiProblemCatalogMessages {
  if (typeof fallbackOrCatalog !== 'string') {
    return fallbackOrCatalog;
  }
  const fallback = fallbackOrCatalog;
  const stateConflict = stateConflictMessage ?? fallback;
  return {
    fallback,
    stateConflict,
    attemptExpired: fallback,
    integrationSigningUnavailable: fallback,
  };
}

/**
 * Resolves user-visible Auth API error text and retryability.
 *
 * {@link AUTH_ATTEMPT_STATE_CONFLICT_TYPE} maps to a neutral, non-retryable outcome (decision
 * already recorded, attempt no longer active, not found, or still PENDING on respond) — callers
 * must show Back only (#833 Patrick, #844 Marc m1). A 409 of another type stays retryable (#844).
 * Never surfaces origin {@code detail}.
 *
 * Pass either a full {@link AuthApiProblemCatalogMessages} or the legacy
 * {@code (fallback, stateConflictMessage)} pair (other types then use {@code fallback}).
 *
 * @param error thrown value (typically Axios)
 * @param fallbackOrCatalog localized fallback string or full catalog
 * @param stateConflictMessage optional when using the string overload
 */
export function resolveAuthApiUserFacingOutcome(
  error: unknown,
  fallbackOrCatalog: string | AuthApiProblemCatalogMessages,
  stateConflictMessage?: string,
): AuthApiUserFacingOutcome {
  const catalog = normalizeCatalog(fallbackOrCatalog, stateConflictMessage);
  const problem = parseAuthApiProblemDetail(httpErrorBody(error));
  const type = problem?.type?.trim();
  if (type === AUTH_ATTEMPT_STATE_CONFLICT_TYPE) {
    return {message: catalog.stateConflict, retryable: false};
  }
  if (type === AUTH_ATTEMPT_EXPIRED_TYPE) {
    return {message: catalog.attemptExpired, retryable: false};
  }
  if (!isEzkeyProblemType(type)) {
    return {message: catalog.fallback, retryable: true};
  }
  const key = catalogKeyForAuthApiProblemType(type);
  switch (key) {
    case 'integrationSigningUnavailable':
      return {message: catalog.integrationSigningUnavailable, retryable: true};
    case 'stateConflict':
      return {message: catalog.stateConflict, retryable: false};
    case 'attemptExpired':
      return {message: catalog.attemptExpired, retryable: false};
    default:
      // Other Ezkey types (including a future non-conflict 409): local fallback only, retryable.
      return {message: catalog.fallback, retryable: true};
  }
}

/**
 * User-visible Auth API error text (message only). Prefer
 * {@link resolveAuthApiUserFacingOutcome} when the UI must distinguish retryable vs Back-only.
 *
 * @param error thrown value (typically Axios)
 * @param fallbackOrCatalog localized fallback string or full catalog
 * @param stateConflictMessage optional when using the string overload
 */
export function userFacingAuthApiError(
  error: unknown,
  fallbackOrCatalog: string | AuthApiProblemCatalogMessages,
  stateConflictMessage?: string,
): string {
  return resolveAuthApiUserFacingOutcome(error, fallbackOrCatalog, stateConflictMessage).message;
}
