import { describe, expect, it } from 'vitest';
import { buildEnrollmentQrPayloadJson } from './enrollment-qr-payload';

describe('buildEnrollmentQrPayloadJson', () => {
  it('includes enrollmentId as string and proof token (same fields as API QR payload)', () => {
    const json = buildEnrollmentQrPayloadJson(42, 'EZK-test-token');
    const parsed = JSON.parse(json) as Record<string, string>;
    expect(parsed.enrollmentId).toBe('42');
    expect(parsed.enrollmentProofToken).toBe('EZK-test-token');
  });
});
