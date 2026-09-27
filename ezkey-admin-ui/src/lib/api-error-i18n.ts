import type { TFunction } from 'i18next';
import i18n from 'i18next';
import { ApiError, getApiErrorMessage, type ProblemDetail } from './api-client';

/** RFC 9457 problem type base for Ezkey (Option B: path after this prefix → dotted i18n key under `errors`). */
export const EZKEY_PROBLEM_TYPE_BASE = 'https://ezkey.io/problems';

/**
 * Problem type keys (dotted, under {@code errors}) where a static locale string is preferred over
 * non-empty {@code detail}. Authentication flows use this broadly. Other entries are allowlisted
 * only when production {@code detail} is a single stable message (see docs/admin-ui-admin-api-error-inventory.md).
 * For dynamic {@code detail} (duplicate names, limits with counts, etc.), the server message must win when present.
 */
const PREFER_I18N_OVER_DETAIL_RELS = new Set<string>([
  'domain.integration-has-enrollments',
  'domain.pending-encryption-key-exists',
  'domain.encryption-rotation-disabled',
  'domain.encryption-reencryption-disabled',
  'domain.integrity-validation-disabled',
  'enrollment.system-integration-create-not-allowed',
  'enrollment.active-verified-enrollment-exists',
  'enrollment.cannot-delete-with-history',
  'enrollment.cannot-delete-linked-as-admin',
]);

function shouldPreferI18nOverDetail(rel: string): boolean {
  if (rel.startsWith('authentication.')) {
    return true;
  }
  return PREFER_I18N_OVER_DETAIL_RELS.has(rel);
}

/**
 * Parses RFC 9457 extension {@code parameters} for safe i18next interpolation (scalars only).
 */
export function parseProblemParameters(
  problem: ProblemDetail | null | undefined,
): Record<string, string | number | boolean> | null {
  const raw = problem?.parameters;
  if (raw == null || typeof raw !== 'object' || Array.isArray(raw)) {
    return null;
  }
  const out: Record<string, string | number | boolean> = {};
  for (const [k, v] of Object.entries(raw)) {
    if (typeof v === 'string' || typeof v === 'number' || typeof v === 'boolean') {
      out[k] = v;
    }
  }
  return Object.keys(out).length > 0 ? out : null;
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
 * Returns a user-facing API error string. Prefers curated i18n for allowlisted problem types
 * (see {@link shouldPreferI18nOverDetail}) when a key exists; otherwise prefers RFC 9457 {@code detail}
 * when non-empty (so business-specific messages are not replaced by generic titles), then locale by
 * {@code type}, then {@link getApiErrorMessage}.
 */
/**
 * Softens deny-list 403 copy from temporary evaluator console sessions into locale strings.
 *
 * @param detail RFC 9457 problem detail text
 */
export function isTemporarySessionRestrictionDetail(detail: string): boolean {
  const lower = detail.toLowerCase();
  return lower.includes('temporary console session') || lower.includes('temporary evaluator');
}

export function getTranslatedApiError(error: unknown, t: TFunction, fallback: string): string {
  if (!(error instanceof ApiError)) return fallback;
  const rel = problemTypeToTranslationKey(error.problemDetail?.type);
  const detail =
    typeof error.problemDetail?.detail === 'string' ? error.problemDetail.detail.trim() : '';
  const params = parseProblemParameters(error.problemDetail);

  if (detail && isTemporarySessionRestrictionDetail(detail)) {
    if (i18n.exists('authorization.temporary-session-restricted', { ns: 'errors' })) {
      return t('authorization.temporary-session-restricted', { ns: 'errors' });
    }
  }

  if (
    rel != null &&
    params != null &&
    i18n.exists(rel, { ns: 'errors' })
  ) {
    return t(rel, { ns: 'errors', ...params });
  }
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
