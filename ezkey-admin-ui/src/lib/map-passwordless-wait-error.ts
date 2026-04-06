import { ApiError, getApiErrorMessage } from './api-client';

/** RFC 9457 problem `type` URI suffixes returned by the Admin API for passwordless-wait failures. */
const PROBLEM_AUTH_REJECTED = '/auth-rejected';
const PROBLEM_AUTH_EXPIRED = '/auth-expired';
const PROBLEM_AUTH_TIMEOUT = '/auth-timeout';

function problemTypeMatches(type: string | undefined, suffix: string): boolean {
  return type != null && type.includes(suffix);
}

export type PasswordlessWaitErrorOutcome =
  | { outcome: 'rejected' }
  | { outcome: 'expired' }
  | { outcome: 'error'; message: string }
  | { outcome: 'connectionLost'; message: string };

/**
 * Maps errors from POST /admin/auth/passwordless-wait to login UI outcomes.
 * Uses RFC 9457 {@code problemDetail.type} when the error is an {@link ApiError}.
 * Non-API failures (e.g. network {@code TypeError}) map to {@code connectionLost}.
 *
 * @param err - Caught error from passwordlessWait
 * @param messageFallback - i18n fallback when the API returns an error without a usable detail/title
 * @param connectionLostMessage - i18n copy for transport-level failures
 * @param translateApiError - When set (e.g. {@link getTranslatedApiError} bound with {@code t}), maps RFC 9457 {@code type} to the {@code errors} namespace before falling back to detail/title
 */
export function mapPasswordlessWaitError(
  err: unknown,
  messageFallback: string,
  connectionLostMessage: string,
  translateApiError?: (error: unknown, fallback: string) => string,
): PasswordlessWaitErrorOutcome {
  const resolveMessage = translateApiError ?? ((e: unknown, fb: string) => getApiErrorMessage(e, fb));

  if (err instanceof ApiError) {
    const type = err.problemDetail?.type;

    if (problemTypeMatches(type, PROBLEM_AUTH_REJECTED)) {
      return { outcome: 'rejected' };
    }
    if (problemTypeMatches(type, PROBLEM_AUTH_EXPIRED)) {
      return { outcome: 'expired' };
    }
    if (problemTypeMatches(type, PROBLEM_AUTH_TIMEOUT) || err.status === 408) {
      return {
        outcome: 'error',
        message: resolveMessage(err, messageFallback),
      };
    }

    return {
      outcome: 'error',
      message: resolveMessage(err, messageFallback),
    };
  }

  return { outcome: 'connectionLost', message: connectionLostMessage };
}
