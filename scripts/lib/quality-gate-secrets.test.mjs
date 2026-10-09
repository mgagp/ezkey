/**
 * Unit tests for quality-gate secret detection.
 * Run: node --test scripts/lib/quality-gate-secrets.test.mjs
 */
import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import {
  detectSecrets,
  isMaskedOrNonSecretValue,
} from './quality-gate-secrets.mjs';

describe('isMaskedOrNonSecretValue', () => {
  it('treats redaction placeholders as masked', () => {
    assert.equal(isMaskedOrNonSecretValue('***REDACTED***'), true);
    assert.equal(isMaskedOrNonSecretValue('[REDACTED]'), true);
    assert.equal(isMaskedOrNonSecretValue('<redacted>'), true);
    assert.equal(isMaskedOrNonSecretValue('****'), true);
  });
  it('treats booleans and ID= labels as non-secret', () => {
    assert.equal(isMaskedOrNonSecretValue('true'), true);
    assert.equal(isMaskedOrNonSecretValue('false'), true);
    assert.equal(isMaskedOrNonSecretValue('ID=1'), true);
  });
  it('treats real values as present', () => {
    assert.equal(isMaskedOrNonSecretValue('ABCD1234'), false);
    assert.equal(isMaskedOrNonSecretValue('46'), false);
    assert.equal(isMaskedOrNonSecretValue('Z6tww70xHBi8OZ'), false);
  });
});

describe('detectSecrets', () => {
  it('ignores challenge=***REDACTED*** (already masked)', () => {
    const r = detectSecrets('Passwordless with challenge=***REDACTED*** for user');
    assert.equal(r.kinds.length, 0);
    assert.equal(r.severity, null);
  });

  it('hits challenge=ABCD1234 (unmasked)', () => {
    const r = detectSecrets('Passwordless with challenge=ABCD1234 for user');
    assert.ok(r.kinds.includes('kv-secret'));
    assert.equal(r.severity, 'RED');
    assert.equal(r.valuePresent, true);
  });

  it('ignores challenge: true boolean on auth-attempt-created line', () => {
    const r = detectSecrets(
      '🔐 Passwordless auth attempt created (ID: 1, challenge: true)',
    );
    assert.equal(r.kinds.length, 0, `unexpected kinds: ${r.kinds}`);
  });

  it('hits real challenge code challenge: 46', () => {
    const r = detectSecrets(
      '📋 Passwordless with challenge: returning auth attempt info (challenge: 46)',
    );
    assert.ok(r.kinds.includes('kv-secret'));
    assert.equal(r.severity, 'RED');
  });

  it('ignores proof token: ID=1 label (not the token value)', () => {
    const r = detectSecrets(
      'Enrollment found with valid proof token: ID=1, Status=CREATED',
    );
    assert.equal(r.kinds.length, 0, `unexpected kinds: ${r.kinds}`);
  });

  it('hits unmasked Enrollment Proof Token value', () => {
    const r = detectSecrets(
      '   Enrollment Proof Token: Z6tww70xHBi8OZ-cfOOrvMu_VEzZBgQhZA7K0ikSO28.J_Dd3j59JwanLqWIFOXMgQ',
    );
    assert.ok(r.kinds.includes('kv-secret'));
    assert.equal(r.severity, 'RED');
    assert.equal(r.valuePresent, true);
  });

  it('classifies email as AMBER low-PII', () => {
    const r = detectSecrets(
      '✅ Global Admin Created: admin.docker (admin@ezkey.local) - Admin Docker',
    );
    assert.ok(r.kinds.includes('email'));
    assert.equal(r.severity, 'AMBER');
    assert.equal(r.valuePresent, false);
  });

  it('ignores JSON password already masked', () => {
    const r = detectSecrets('{"password":"***REDACTED***"}');
    assert.equal(r.kinds.length, 0);
  });

  it('hits JSON password in clear', () => {
    const r = detectSecrets('{"password":"hunter2"}');
    assert.ok(r.kinds.includes('json-secret'));
    assert.equal(r.severity, 'RED');
  });
});
