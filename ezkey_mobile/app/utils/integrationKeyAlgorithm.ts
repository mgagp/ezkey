/**
 * Enrollment bind: integration signing keys use Ed25519 in phase 1 only.
 * See docs/CRYPTO.md and docs/ENDPOINT.md.
 */

export const INTEGRATION_KEY_ALGORITHM_PHASE1 = 'ed25519' as const;

/**
 * @returns null if the algorithm is supported; otherwise a user-safe error message.
 * Logs the unexpected value in __DEV__ only.
 */
export function integrationKeyAlgorithmBindError(value: unknown): string | null {
  if (value === INTEGRATION_KEY_ALGORITHM_PHASE1) {
    return null;
  }
  if (__DEV__) {
    const shown = value === undefined ? '(missing)' : JSON.stringify(value);
    console.warn(
      `[bind] Unsupported integrationKeyAlgorithm (expected ${INTEGRATION_KEY_ALGORITHM_PHASE1}):`,
      shown,
    );
  }
  return 'Unsupported integration key algorithm. This app requires ed25519.';
}
