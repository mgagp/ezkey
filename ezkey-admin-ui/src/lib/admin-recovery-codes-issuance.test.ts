import { describe, expect, it } from 'vitest';
import {
  RECOVERY_CODES_ISSUANCE_COPY_KEYS,
  recoveryCodesIssuanceKind,
} from './admin-recovery-codes-issuance';
import enAdmins from '@/locales/en/admins.json';
import frAdmins from '@/locales/fr/admins.json';

const locales = { en: enAdmins, fr: frAdmins } as const;

function resolve(bundle: unknown, key: string): unknown {
  return key.split('.').reduce<unknown>(
    (node, part) => (node != null && typeof node === 'object' ? (node as Record<string, unknown>)[part] : undefined),
    bundle,
  );
}

const FIRST_ISSUANCE_WORDING = /\b(initial|first)\b|premier|première/i;

describe('recoveryCodesIssuanceKind', () => {
  it('uses replacement wording once every previously issued code has been used', () => {
    expect(recoveryCodesIssuanceKind({ recoveryCodesExhausted: true })).toBe('replacement');
  });

  it('keeps first-issuance wording when no set was ever issued', () => {
    expect(recoveryCodesIssuanceKind({ recoveryCodesExhausted: false })).toBe('initial');
    expect(recoveryCodesIssuanceKind({})).toBe('initial');
  });
});

describe('RECOVERY_CODES_ISSUANCE_COPY_KEYS', () => {
  for (const [locale, bundle] of Object.entries(locales)) {
    it(`defines every issuance string in ${locale}`, () => {
      for (const copy of Object.values(RECOVERY_CODES_ISSUANCE_COPY_KEYS)) {
        for (const key of Object.values(copy)) {
          expect(typeof resolve(bundle, key), `${locale}:${key}`).toBe('string');
        }
      }
    });

    it(`does not describe a replacement set as a first issuance in ${locale}`, () => {
      for (const key of Object.values(RECOVERY_CODES_ISSUANCE_COPY_KEYS.replacement)) {
        expect(resolve(bundle, key), `${locale}:${key}`).not.toMatch(FIRST_ISSUANCE_WORDING);
      }
    });
  }
});
