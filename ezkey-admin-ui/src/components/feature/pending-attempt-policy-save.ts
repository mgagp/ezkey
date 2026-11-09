import type { TFunction } from 'i18next';
import { ApiError } from '@/lib/api-client';
import { getTranslatedApiError } from '@/lib/api-error-i18n';

/**
 * Message shown when saving the waiting-request setting fails.
 * A 5xx or a network failure uses the field's own sentence so a French operator
 * does not see the English server detail. Other API errors stay translated.
 */
export function policySaveErrorMessage(error: unknown, t: TFunction, saveFailed: string): string {
  if (!(error instanceof ApiError) || error.status >= 500) {
    return saveFailed;
  }
  return getTranslatedApiError(error, t, saveFailed);
}
