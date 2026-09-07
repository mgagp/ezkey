/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: localEnrollmentIdentity tests
 * @since 2026
 */

import {
  deriveInstallationScopeId,
  deriveLocalEnrollmentId,
  resolveServerEnrollmentId,
} from '../localEnrollmentIdentity';
import type {StoredEnrollment} from '../../services/storage/enrollmentStorage';

describe('deriveInstallationScopeId', () => {
  it('produces distinct scope ids for distinct installations', () => {
    const a = deriveInstallationScopeId('https://auth-a.example.com');
    const b = deriveInstallationScopeId('https://auth-b.example.com');
    expect(a).not.toBe(b);
    expect(a).toMatch(/^i[0-9a-f]{16}$/);
    expect(b).toMatch(/^i[0-9a-f]{16}$/);
  });

  it('is stable for the same installation id and matches the local enrollment id prefix', () => {
    const zone = 'https://ezkey.acme.com';
    const scope = deriveInstallationScopeId(zone);
    const localId = deriveLocalEnrollmentId(zone, 1);
    expect(localId).toBe(`${scope}_e1`);
  });

  it('rejects empty input', () => {
    expect(() => deriveInstallationScopeId('')).toThrow();
  });
});

describe('deriveLocalEnrollmentId', () => {
  it('produces distinct local ids for the same server enrollment on two installations', () => {
    const a = deriveLocalEnrollmentId('https://auth-a.example.com', 1);
    const b = deriveLocalEnrollmentId('https://auth-b.example.com', 1);
    expect(a).not.toBe(b);
    expect(a).toMatch(/^i[0-9a-f]{16}_e1$/);
    expect(b).toMatch(/^i[0-9a-f]{16}_e1$/);
  });

  it('is stable for equivalent normalized installation ids', () => {
    const first = deriveLocalEnrollmentId('https://ezkey.acme.com', '42');
    const second = deriveLocalEnrollmentId('https://ezkey.acme.com', 42);
    expect(first).toBe(second);
  });

  it('differs when server enrollment ids differ on the same installation', () => {
    const zone = 'https://ezkey.acme.com';
    expect(deriveLocalEnrollmentId(zone, 1)).not.toBe(deriveLocalEnrollmentId(zone, 2));
  });

  it('rejects empty inputs', () => {
    expect(() => deriveLocalEnrollmentId('', 1)).toThrow();
    expect(() => deriveLocalEnrollmentId('https://ezkey.acme.com', '')).toThrow();
  });
});

describe('resolveServerEnrollmentId', () => {
  const base: StoredEnrollment = {
    id: 'iabc_e1',
    integrationId: '1',
    integrationName: 'Acme',
    createdAt: '2026-01-01T00:00:00.000Z',
    lastActivityAt: '2026-01-01T00:00:00.000Z',
    enrollmentProofToken: 'tok',
  };

  it('prefers explicit enrollmentId (server) over local id', () => {
    expect(resolveServerEnrollmentId({...base, enrollmentId: '7'})).toBe('7');
  });

  it('falls back to id for legacy rows', () => {
    expect(resolveServerEnrollmentId({...base, id: '3', enrollmentId: undefined})).toBe('3');
  });

  it('accepts display metadata without a proof token', () => {
    expect(resolveServerEnrollmentId({id: base.id, enrollmentId: '9'})).toBe('9');
  });
});
