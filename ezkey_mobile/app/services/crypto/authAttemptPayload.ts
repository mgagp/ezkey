/**
 * Canonical payload builders for Pending (verify integration signature) and Respond (device sign).
 * Matches backend format: docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md. Uses Unicode NFC for context text.
 */

const SEP = '|';
const TRUE = 'true';
const FALSE = 'false';

function nfcOrEmpty(s: string | null | undefined): string {
  if (s == null) return '';
  return s.normalize('NFC');
}

/**
 * Builds the payload that the integration signed for the Pending response.
 * Format: proofToken|challengeRequired|challengeRequiredByPolicy|contextTitle|contextMessage.
 */
export function buildPendingPayload(
  proofToken: string,
  challengeRequired: boolean,
  challengeRequiredByPolicy: boolean,
  contextTitle: string | null | undefined,
  contextMessage: string | null | undefined,
): string {
  const challengeStr = challengeRequired ? TRUE : FALSE;
  const challengeRequiredByPolicyStr = challengeRequiredByPolicy ? TRUE : FALSE;
  const title = nfcOrEmpty(contextTitle);
  const message = nfcOrEmpty(contextMessage);
  return `${proofToken}${SEP}${challengeStr}${SEP}${challengeRequiredByPolicyStr}${SEP}${title}${SEP}${message}`;
}

/**
 * Builds the payload the device must sign for the Respond request.
 * Format: proofToken|accepted.
 */
export function buildRespondPayload(proofToken: string, accepted: boolean): string {
  const acceptedStr = accepted ? TRUE : FALSE;
  return `${proofToken}${SEP}${acceptedStr}`;
}

/**
 * Builds the payload the integration signs for the Respond HTTP response body.
 * Format: proofToken|authAttemptId|result|message (NFC on message; null proof token and id use "").
 */
export function buildRespondResultPayload(
  proofToken: string | null | undefined,
  authAttemptId: string | number | null | undefined,
  result: string | null | undefined,
  message: string | null | undefined,
): string {
  const pt = proofToken != null ? String(proofToken) : '';
  const idStr = authAttemptId != null ? String(authAttemptId) : '';
  const res = result != null ? result : '';
  const msg = nfcOrEmpty(message);
  return `${pt}${SEP}${idStr}${SEP}${res}${SEP}${msg}`;
}
