/**
 * Canonical payload builders for enrollment bind (integration-signed), verify request (device-signed),
 * verify response (integration-signed), and enrolled instance-info (integration-signed).
 * Matches docs/ENROLLMENT_SIGNATURE_PAYLOAD.md.
 */

const SEP = '|';

/** Purpose literal for enrolled instance-info domain separation. */
export const INSTANCE_INFO_PURPOSE = 'INSTANCE_INFO';

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

/**
 * Builds the payload the integration signs in the bind response.
 */
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

/**
 * Builds the payload the device signs for verify.
 */
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

/**
 * Builds the payload the integration signs on successful verify (same shape as respond result).
 */
export function buildVerifyResultPayload(
  enrollmentProofToken: string,
  enrollmentId: number,
  outcome: string,
  message: string | null | undefined,
): string {
  const msg = nfcOrEmpty(message);
  return [enrollmentProofToken, String(enrollmentId), outcome, msg].join(SEP);
}

export type InstanceInfoResponseLike = {
  enrollmentProofToken: string;
  enrollmentId: number | string;
  authApiPublicBaseUrl?: string | null;
  instanceName?: string | null;
  instanceDescription?: string | null;
  aboutUrl?: string | null;
};

/**
 * Builds the payload the integration signs for enrolled instance-info.
 */
export function buildInstanceInfoPayload(response: InstanceInfoResponseLike): string {
  const pt = response.enrollmentProofToken ?? '';
  const idStr =
    response.enrollmentId !== undefined && response.enrollmentId !== null
      ? String(response.enrollmentId)
      : '';
  const authBase = response.authApiPublicBaseUrl ?? '';
  return [
    pt,
    idStr,
    INSTANCE_INFO_PURPOSE,
    authBase,
    nfcOrEmpty(response.instanceName),
    nfcOrEmpty(response.instanceDescription),
    nfcOrEmpty(response.aboutUrl),
  ].join(SEP);
}
