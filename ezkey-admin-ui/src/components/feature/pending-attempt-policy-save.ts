import type { TFunction } from 'i18next';
import { ApiError } from '@/lib/api-client';
import { getTranslatedApiError, problemTypeToTranslationKey } from '@/lib/api-error-i18n';

/**
 * Message shown when saving the waiting-request setting fails.
 * A 5xx or a network failure uses the field's own sentence so a French operator
 * does not see the English server detail. A system-integration lifecycle 403 uses
 * the lock sentence here only: retire and delete return the same problem type and
 * must not show it. Other API errors stay translated.
 */
export function policySaveErrorMessage(error: unknown, t: TFunction, saveFailed: string): string {
  if (!(error instanceof ApiError) || error.status >= 500) {
    return saveFailed;
  }
  if (
    problemTypeToTranslationKey(error.problemDetail?.type) === 'domain.system-integration-lifecycle'
  ) {
    return t('pendingAttemptPolicy.systemLocked', { ns: 'integrations' });
  }
  return getTranslatedApiError(error, t, saveFailed);
}
