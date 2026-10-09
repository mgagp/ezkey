/**
 * Unit tests for quality-gate secret detection / redaction.
 * Run: node --test scripts/lib/quality-gate-secrets.test.mjs
 */
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  detectSecrets,
  isMaskedOrNonSecretValue,
  sanitize,
  signature,
  ID_LABEL_VALUE_RE,
} from './quality-gate-secrets.mjs';

describe('isMaskedOrNonSecretValue', () => {
  it('treats redaction placeholders as masked', () => {
    assert.equal(isMaskedOrNonSecretValue('***REDACTED***'), true);
    assert.equal(isMaskedOrNonSecretValue('[REDACTED]'), true);
    assert.equal(isMaskedOrNonSecretValue('<redacted>'), true);
    assert.equal(isMaskedOrNonSecretValue('****'), true);
  });
  it('treats booleans and anchored ID= labels as non-secret', () => {
    assert.equal(isMaskedOrNonSecretValue('true'), true);
    assert.equal(isMaskedOrNonSecretValue('false'), true);
    assert.equal(isMaskedOrNonSecretValue('ID=1'), true);
    assert.equal(ID_LABEL_VALUE_RE.test('ID=5abc'), false);
    assert.equal(isMaskedOrNonSecretValue('ID=5abc'), false);
  });
  it('treats real values as present', () => {
    assert.equal(isMaskedOrNonSecretValue('ABCD1234'), false);
    assert.equal(isMaskedOrNonSecretValue('46'), false);
  });
});

describe('Christophe replay inputs (12)', () => {
  const cases = [
    ['Authorization: Bearer xyzTOKEN1234567890abcd', 'bearer'],
    ['X-Api-Key: sk_live_abcdefghijklmnopqrstuv', 'header-secret'],
    ['pwd=supersecretvalue99', 'kv-secret'],
    ['api_key=abc123def456ghi789jkl', 'header-secret'],
    ['code=ABC123XYZ', 'kv-secret'],
    ['token=abcdefghijklmnopqrstuvwxyz12', 'kv-secret'],
    ['proofToken=abc123secrettokenvalue', 'kv-secret'],
    ['--enrollment-proof-token "Z6tww70xHBi8OZ-cfOOrvMu_VEzZBgQhZA7K0ikSO28"', 'cli-secret'],
    ['admin@ezkey.local', 'email'],
    ['connected from 192.168.1.42', 'ip'],
    ['eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIn0.sig', 'jwt'],
    ['{"password":"hunter2secret"}', 'json-secret'],
  ];
  for (const [line, kind] of cases) {
    it(`detects ${kind}: ${line.slice(0, 40)}…`, () => {
      const r = detectSecrets(line);
      assert.ok(r.kinds.length, `expected kinds for: ${line}`);
      assert.ok(r.kinds.includes(kind), `expected ${kind} in ${r.kinds} for: ${line}`);
      const redacted = sanitize(line);
      assert.notEqual(redacted, line);
      if (kind === 'email' || kind === 'ip') {
        assert.equal(r.severity, 'AMBER');
      } else {
        assert.equal(r.severity, 'RED');
      }
    });
  }
});

describe('detectSecrets product cases', () => {
  it('ignores challenge=***REDACTED*** (already masked)', () => {
    const r = detectSecrets('Passwordless with challenge=***REDACTED*** for user');
    assert.equal(r.kinds.length, 0);
  });

  it('hits challenge=ABCD1234 (unmasked)', () => {
    const r = detectSecrets('Passwordless with challenge=ABCD1234 for user');
    assert.equal(r.severity, 'RED');
  });

  it('ignores challenge: true boolean', () => {
    const r = detectSecrets(
      '🔐 Passwordless auth attempt created (ID: 1, challenge: true)',
    );
    assert.equal(r.kinds.length, 0, `unexpected kinds: ${r.kinds}`);
  });

  it('hits Enrollment Challenge Code: 123456', () => {
    const r = detectSecrets('   Enrollment Challenge Code: 123456');
    assert.equal(r.severity, 'RED');
    assert.ok(r.kinds.includes('kv-secret'));
  });

  it('hits recovery code shape', () => {
    const r = detectSecrets('   1. 3711-0244-2145-1666-3366-1916-2965-8688');
    assert.equal(r.severity, 'RED');
    assert.ok(r.kinds.includes('recovery-code'));
  });

  it('hits --enrollment-proof-token CLI', () => {
    const r = detectSecrets(
      '       --enrollment-proof-token "f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w"',
    );
    assert.equal(r.severity, 'RED');
  });

  it('Bearer before kv — token not left in clear', () => {
    const line = 'Authorization: Bearer abcdefghijklmnopqrstuvwxyz012345';
    const out = sanitize(line);
    assert.match(out, /Bearer \*\*\*REDACTED\*\*\*/);
    assert.equal(out.includes('abcdefghijklmnopqrstuvwxyz012345'), false);
  });
});

describe('TOKEN / Java class names', () => {
  it('does NOT tokenize DataIntegrityViolationException', () => {
    const line =
      'org.springframework.dao.DataIntegrityViolationException: could not execute';
    const out = sanitize(line);
    assert.match(out, /DataIntegrityViolationException/);
    assert.equal(out.includes('<TOKEN>'), false);
  });

  it('does tokenize mixed alphanumeric opaque secrets ≥20', () => {
    const line = 'opaque=AbCdEfGhIjKlMnOpQr123456';
    const out = sanitize(line);
    assert.match(out, /<TOKEN>|REDACTED/);
  });
});

describe('allowlist signature match', () => {
  it('ezkey_encryption_key_pkey survives sanitize (no digit → not <TOKEN>)', () => {
    const line =
      'ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"';
    const sig = signature(line);
    assert.match(sig, /ezkey_encryption_key_pkey/);
    assert.ok(new RegExp('ezkey_encryption_key_pkey').test(sig));
  });

  it('bootstrap proof-token signature matches allowlist-style anchored regex', () => {
    const line =
      '2026-10-09T19:58:56.790Z  WARN 1 --- [ezkey-admin-api] [           main] o.e.admin.service.AdminBootstrapService  :    Enrollment Proof Token: f7zMmUzbJSV1WPjXD_jLuoDNucM559Qr7MWIo_MC0yo.0KvCfXqW9ydHGisXjxMz2w';
    const sig = signature(line);
    const re = /^.*AdminBootstrapService.*Enrollment Proof Token=/;
    assert.ok(re.test(sig), `sig=${sig}`);
  });

  it('ezkey_encryption_key_pkey allowlist pattern is anchored', () => {
    const line =
      'ERROR: duplicate key value violates unique constraint "ezkey_encryption_key_pkey"';
    const sig = signature(line);
    assert.ok(new RegExp('^.*ezkey_encryption_key_pkey').test(sig), `sig=${sig}`);
  });
});

