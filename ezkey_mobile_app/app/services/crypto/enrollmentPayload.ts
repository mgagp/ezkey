/**
 * Canonical payload builders for enrollment bind / verify. Matches docs/ENROLLMENT_SIGNATURE_PAYLOAD.md.
 */

const SEP = '|';

function nfcOrEmpty(s: string | null | undefined): string {
  if (s == null) {
    return '';
  }
  return s.normalize('NFC');
}

export type BindEnrollmentResponseLike = {
  enrollmentProofToken: string;
  enrollmentId: number | string;
  integrationPublicKey: string;
  integrationKeyAlgorithm: string;
  integrationName?: string | null;
  integrationDescription?: string | null;
  enrollmentName?: string | null;
  tenantId?: number | null;
  tenantName?: string | null;
  tenantDescription?: string | null;
};

export function buildBindPayload(response: BindEnrollmentResponseLike): string {
  const pt = response.enrollmentProofToken ?? '';
  const idStr =
    response.enrollmentId !== undefined && response.enrollmentId !== null
      ? String(response.enrollmentId)
      : '';
  const integPk = response.integrationPublicKey ?? '';
  const algo = response.integrationKeyAlgorithm ?? '';
  const tenantIdStr =
    response.tenantId !== undefined && response.tenantId !== null
      ? String(response.tenantId)
      : '';
  return [
    pt,
    idStr,
    integPk,
    algo,
    nfcOrEmpty(response.integrationName),
    nfcOrEmpty(response.integrationDescription),
    nfcOrEmpty(response.enrollmentName),
    tenantIdStr,
    nfcOrEmpty(response.tenantName),
    nfcOrEmpty(response.tenantDescription),
  ].join(SEP);
}

export function buildVerifyDevicePayload(
  enrollmentProofToken: string,
  enrollmentId: number,
  challengeResponse: number,
  devicePublicKey: string,
): string {
  return [
    enrollmentProofToken,
    String(enrollmentId),
    String(challengeResponse),
    devicePublicKey,
  ].join(SEP);
}

export function buildVerifyResultPayload(
  enrollmentProofToken: string,
  enrollmentId: number,
  outcome: string,
  message: string | null | undefined,
): string {
  const msg = nfcOrEmpty(message);
  return [enrollmentProofToken, String(enrollmentId), outcome, msg].join(SEP);
}
