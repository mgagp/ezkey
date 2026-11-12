import type { TFunction } from 'i18next';
import { ApiError } from '@/lib/api-client';
import { EZKEY_PROBLEM_TYPE_BASE } from '@/lib/api-error-i18n';

const COOLDOWN_TYPE = `${EZKEY_PROBLEM_TYPE_BASE}/domain/auth-attempt-creation-cooldown`;

/**
 * Login-page sentence for a creation-cooldown 409, including the Retry-After delay.
 * Returns null for every other error so the caller can use the usual translation.
 */
export function loginCooldownMessage(error: unknown, t: TFunction): string | null {
  if (!(error instanceof ApiError) || error.problemDetail?.type !== COOLDOWN_TYPE) {
    return null;
  }
  const seconds = error.retryAfterSeconds;
  if (seconds == null) {
    return t('states.error.cooldown', { ns: 'login' });
  }
  const minutes = Math.max(1, Math.ceil(seconds / 60));
  return t('states.error.cooldownMinutes', { ns: 'login', count: minutes });
}
