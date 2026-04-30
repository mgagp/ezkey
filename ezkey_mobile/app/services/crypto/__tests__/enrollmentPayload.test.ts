import {
  buildBindPayload,
  buildVerifyDevicePayload,
  buildVerifyResultPayload,
} from '../enrollmentPayload';

describe('enrollmentPayload', () => {
  it('buildBindPayload joins fields and NFC-normalizes user-facing segments', () => {
    const p = buildBindPayload({
      enrollmentProofToken: 'pt',
      enrollmentId: 1,
      integrationPublicKey: 'pk',
      integrationKeyAlgorithm: 'ed25519',
      integrationName: 'caf\u0065\u0301',
      integrationDescription: 'd',
      enrollmentName: 'n',
      tenantId: 2,
      tenantName: 'tn',
      tenantDescription: 'td',
    });
    expect(p.startsWith('pt|1|pk|ed25519|')).toBe(true);
    expect(p.endsWith('|d|n|2|tn|td')).toBe(true);
    expect(p).toContain('caf\u00e9');
  });

  it('buildVerifyDevicePayload matches server format', () => {
    expect(buildVerifyDevicePayload('tok', 42, 123456, 'spki')).toBe('tok|42|123456|spki');
  });

  it('buildVerifyResultPayload NFC-normalizes message', () => {
    const p = buildVerifyResultPayload('tok', 9, 'VERIFIED', 'caf\u0301');
    expect(p).toBe(`tok|9|VERIFIED|${'caf\u0301'.normalize('NFC')}`);
  });
});
