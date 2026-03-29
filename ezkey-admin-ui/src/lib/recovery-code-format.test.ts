import { describe, expect, it } from 'vitest';
import { isValidRecoveryCodeFormat, normalizeRecoveryCodeInput } from './recovery-code-format';

describe('normalizeRecoveryCodeInput', () => {
  it('formats 32 digits without dashes', () => {
    const digits = '47438097042659147438418080105825';
    expect(normalizeRecoveryCodeInput(digits)).toBe(
      '4743-8097-0426-5914-7438-4180-8010-5825',
    );
  });

  it('leaves already dashed codes unchanged when valid', () => {
    const s = '4743-8097-0426-5914-7438-4180-8010-5825';
    expect(normalizeRecoveryCodeInput(s)).toBe(s);
  });
});

describe('isValidRecoveryCodeFormat', () => {
  it('accepts canonical format', () => {
    expect(isValidRecoveryCodeFormat('4743-8097-0426-5914-7438-4180-8010-5825')).toBe(true);
  });

  it('rejects wrong length', () => {
    expect(isValidRecoveryCodeFormat('4743-8097')).toBe(false);
  });
});
