/**
 * Builds the same JSON string as the Admin API {@code QrCodePayloadService.composePayload}
 * for enrollment QR codes (mobile app {@code parseQrPayload}).
 */
export function buildEnrollmentQrPayloadJson(
  enrollmentId: number,
  enrollmentProofToken: string,
): string {
  const authBase = (import.meta.env.VITE_QR_AUTH_BASE_URL as string | undefined)?.trim();
  const payload: Record<string, string> = {
    enrollmentId: String(enrollmentId),
    enrollmentProofToken,
  };
  if (authBase) {
    payload.authUrl = authBase;
  }
  return JSON.stringify(payload);
}
