import {
  buildPendingPayload,
  buildRespondPayload,
  buildRespondResultPayload,
} from '../authAttemptPayload';

/**
 * Regression guard for docs/AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md canonical strings.
 * Golden vectors that prove ECDSA over a real integration key live in Android
 * IntegrationKeyVerifierTest + optional files under android/.../fixtures/ (see README there).
 */
describe('authAttemptPayload', () => {
  describe('buildPendingPayload', () => {
    it('uses proofToken|challengeRequired|contextTitle|contextMessage with true/false and NFC on context', () => {
      expect(
        buildPendingPayload('apt', true, 'caf\u0301e', 'caf\u0301'),
      ).toBe(`apt|true|${'caf\u0301e'.normalize('NFC')}|${'caf\u0301'.normalize('NFC')}`);
    });

    it('uses false when challenge not required and empty NFC segments for missing context', () => {
      expect(buildPendingPayload('x', false, undefined, undefined)).toBe('x|false||');
    });
  });

  describe('buildRespondPayload', () => {
    it('uses proofToken|accepted with lowercase true/false', () => {
      expect(buildRespondPayload('tok', true)).toBe('tok|true');
      expect(buildRespondPayload('tok', false)).toBe('tok|false');
    });
  });

  describe('buildRespondResultPayload', () => {
    it('matches proofToken|id|result|message with NFC on message', () => {
      const s = buildRespondResultPayload('pt', 42, 'APPROVED', 'caf\u0301');
      expect(s).toBe(`pt|42|APPROVED|${'caf\u0301'.normalize('NFC')}`);
    });

    it('uses empty segments for nullish proof token, id, result', () => {
      expect(buildRespondResultPayload(null, null, null, null)).toBe('|||');
    });
  });
});
