import { describe, expect, it } from 'vitest';
import { buildEnrollmentQrPayloadJson } from './enrollment-qr-payload';

describe('buildEnrollmentQrPayloadJson', () => {
  it('includes the proof token and omits enrollmentId (same fields as API QR payload)', () => {
    const json = buildEnrollmentQrPayloadJson('EZK-test-token');
    const parsed = JSON.parse(json) as Record<string, string>;
    expect(parsed.enrollmentProofToken).toBe('EZK-test-token');
    expect(parsed.enrollmentId).toBeUndefined();
  });
});
