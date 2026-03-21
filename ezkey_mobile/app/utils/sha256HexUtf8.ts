/**
 * SHA-256 digest of the string encoded as UTF-8, printed as lowercase hex.
 * Must match Auth API {@code AuthAttemptPendingService#sha256HexUtf8} for pending payload diagnostics.
 */

import {sha256} from 'js-sha256';

export function sha256HexUtf8(value: string): string {
  return sha256(value);
}
