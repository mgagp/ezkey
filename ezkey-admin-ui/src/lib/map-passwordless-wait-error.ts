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
 */
export function mapPasswordlessWaitError(
  err: unknown,
  messageFallback: string,
  connectionLostMessage: string,
): PasswordlessWaitErrorOutcome {
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
        message: getApiErrorMessage(err, messageFallback),
      };
    }

    return {
      outcome: 'error',
      message: getApiErrorMessage(err, messageFallback),
    };
  }

  return { outcome: 'connectionLost', message: connectionLostMessage };
}
