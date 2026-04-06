import type { TFunction } from 'i18next';
import i18n from 'i18next';
import { ApiError, getApiErrorMessage } from './api-client';

/** RFC 9457 problem type base for Ezkey (Option B: path after this prefix → dotted i18n key under `errors`). */
export const EZKEY_PROBLEM_TYPE_BASE = 'https://ezkey.io/problems';

/**
 * Problem type keys (dotted, under {@code errors}) where a static locale string is preferred over
 * {@code detail}. Used for login/auth flows where the API {@code detail} is often English but we
 * ship curated FR/EN copy. For generic buckets (e.g. {@code admin.invalid-argument}), the server
 * {@code detail} carries the specific reason (duplicate name, etc.) and must win when present.
 */
function shouldPreferI18nOverDetail(rel: string): boolean {
  return rel.startsWith('authentication.');
}

/**
 * Maps an RFC 9457 {@code type} URI to a dotted key path under the {@code errors} i18n namespace.
 * Returns {@code null} if the value is missing or not under {@link EZKEY_PROBLEM_TYPE_BASE}.
 *
 * @example
 * // https://ezkey.io/problems/authentication/invalid-credentials → "authentication.invalid-credentials"
 * // https://ezkey.io/problems/admin/validation-failed → "admin.validation-failed"
 */
export function problemTypeToTranslationKey(type: string | undefined): string | null {
  if (type == null || typeof type !== 'string') return null;
  const trimmed = type.trim();
  if (!trimmed.startsWith(EZKEY_PROBLEM_TYPE_BASE)) return null;
  const remainder = trimmed.slice(EZKEY_PROBLEM_TYPE_BASE.length).replace(/^\/+/, '');
  if (!remainder || remainder.includes('//')) return null;
  if (!/^[\w\-/]+$/.test(remainder)) return null;
  return remainder.replace(/\//g, '.');
}

/**
 * Returns a user-facing API error string. Prefers curated i18n for {@code authentication.*}
 * problem types when a key exists; otherwise prefers RFC 9457 {@code detail} when non-empty (so
 * business-specific messages are not replaced by generic titles), then locale by {@code type},
 * then {@link getApiErrorMessage}.
 */
export function getTranslatedApiError(error: unknown, t: TFunction, fallback: string): string {
  if (!(error instanceof ApiError)) return fallback;
  const rel = problemTypeToTranslationKey(error.problemDetail?.type);
  const detail =
    typeof error.problemDetail?.detail === 'string' ? error.problemDetail.detail.trim() : '';

  if (rel != null && i18n.exists(rel, { ns: 'errors' }) && shouldPreferI18nOverDetail(rel)) {
    return t(rel, { ns: 'errors' });
  }
  if (detail) {
    return detail;
  }
  if (rel != null && i18n.exists(rel, { ns: 'errors' })) {
    return t(rel, { ns: 'errors' });
  }
  return getApiErrorMessage(error, fallback);
}
