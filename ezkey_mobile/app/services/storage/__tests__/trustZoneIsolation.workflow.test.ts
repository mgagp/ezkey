/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Trust-zone isolation workflow — MOBILE_DATA_MODEL.md Cornerstone.
 * Two installations, same server enrollment id, must not share local ids, seal scopes, or claim routing.
 */

jest.mock('react-native-keychain', () => ({
  ACCESSIBLE: {
    AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY: 'AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY',
  },
  setGenericPassword: jest.fn(),
  getGenericPassword: jest.fn().mockResolvedValue(false),
  resetGenericPassword: jest.fn().mockResolvedValue(true),
}));

jest.mock('../secureStorage', () => {
  const actual = jest.requireActual('../secureStorage') as typeof import('../secureStorage');
  const AsyncStorage = require('@react-native-async-storage/async-storage').default;
  const Keychain = require('react-native-keychain');
  const {nativeCrypto} = require('../../crypto/nativeCrypto') as typeof import('../../crypto/nativeCrypto');
  return {
    createSecureStorage: actual.createSecureStorage,
    secureStorage: actual.createSecureStorage({
      metadata: AsyncStorage,
      keychain: Keychain,
      crypto: nativeCrypto,
      platformOs: 'android',
      nativeCryptoLinked: true,
    }),
  };
});

jest.mock('../../api/authAttempts', () => ({
  MALFORMED_PENDING_RESPONSE: 'MALFORMED_PENDING_RESPONSE',
  authAttemptsApi: {
    pending: jest.fn(),
  },
}));

import {NativeModules} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {authAttemptsApi} from '../../api/authAttempts';
import {claimPendingAttempt} from '../../pendingAuth/claimPendingAttempt';
import {
  deriveInstallationScopeId,
  deriveLocalEnrollmentId,
} from '../../../utils/localEnrollmentIdentity';
import {enrollmentStorage} from '../enrollmentStorage';
import type {StoredEnrollment} from '../enrollmentStorage';

const nativeModule = NativeModules.EzkeyCryptoModule as {
  sealSecret: jest.Mock;
  unsealSecret: jest.Mock;
  sign: jest.Mock;
  getPublicKey: jest.Mock;
};

const mockPending = jest.mocked(authAttemptsApi.pending);

const AUTH_A = 'https://auth.acme.example';
const AUTH_B = 'https://auth.other.example';
const SERVER_ENROLLMENT_ID = '1';

const idA = deriveLocalEnrollmentId(AUTH_A, SERVER_ENROLLMENT_ID);
const idB = deriveLocalEnrollmentId(AUTH_B, SERVER_ENROLLMENT_ID);
const scopeA = deriveInstallationScopeId(AUTH_A);
const scopeB = deriveInstallationScopeId(AUTH_B);

function enrollmentFor(
  authUrl: string,
  localId: string,
  proofToken: string,
  publicKey: string,
  name: string,
): StoredEnrollment {
  return {
    id: localId,
    integrationId: '1',
    integrationName: name,
    tenantName: name,
    createdAt: '2026-08-18T00:00:00.000Z',
    lastActivityAt: '2026-08-18T00:00:00.000Z',
    enrollmentProofToken: proofToken,
    enrollmentId: SERVER_ENROLLMENT_ID,
    integrationPublicKey: publicKey,
    installation: {
      id: authUrl,
      authUrl,
      host: authUrl.replace(/^https:\/\//, ''),
      name,
    },
  };
}

function installIsolatingUnseal() {
  nativeModule.unsealSecret.mockImplementation(
    async (installationScopeId: string, logicalKey: string, sealedPayload: string) => {
      const parsed = JSON.parse(sealedPayload) as {
        installationScopeId: string;
        key: string;
        plaintext: string;
      };
      if (parsed.installationScopeId !== installationScopeId || parsed.key !== logicalKey) {
        throw new Error('unseal_scope_mismatch');
      }
      return parsed.plaintext;
    },
  );
}

describe('trust-zone isolation (two installations, same server id)', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await AsyncStorage.clear();
    installIsolatingUnseal();
    mockPending.mockResolvedValue(undefined);
    nativeModule.getPublicKey.mockResolvedValue(
      'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
    );
    nativeModule.sign.mockResolvedValue('device-proof-sig');
  });

  it('rehydrates distinct local ids and seal scopes, and claim on A never touches B', async () => {
    expect(idA).not.toBe(idB);
    expect(scopeA).not.toBe(scopeB);

    await enrollmentStorage.saveEnrollment(
      enrollmentFor(AUTH_A, idA, 'token-a', 'pk-a', 'Acme'),
    );
    await enrollmentStorage.saveEnrollment(
      enrollmentFor(AUTH_B, idB, 'token-b', 'pk-b', 'Other'),
    );

    const sealScopes = nativeModule.sealSecret.mock.calls.map(call => call[0]);
    expect(sealScopes).toEqual(expect.arrayContaining([scopeA, scopeB]));
    expect(new Set(sealScopes).size).toBeGreaterThanOrEqual(2);

    const listed = await enrollmentStorage.listEnrollmentsDetailed();
    expect(listed.broken).toEqual([]);
    expect(listed.enrollments.map(item => item.id).sort()).toEqual([idA, idB].sort());

    const enrollmentA = listed.enrollments.find(item => item.id === idA);
    expect(enrollmentA?.enrollmentProofToken).toBe('token-a');
    expect(enrollmentA?.installation?.authUrl).toBe(AUTH_A);

    const result = await claimPendingAttempt(enrollmentA!);
    expect(result).toEqual({kind: 'none'});
    expect(mockPending).toHaveBeenCalledTimes(1);
    expect(mockPending).toHaveBeenCalledWith(
      expect.objectContaining({
        enrollmentId: SERVER_ENROLLMENT_ID,
        enrollmentProofToken: 'token-a',
      }),
      AUTH_A,
    );
    expect(nativeModule.sign).toHaveBeenCalledWith(idA, expect.any(String));
    expect(nativeModule.sign).not.toHaveBeenCalledWith(idB, expect.anything());
  });

  it('marks only zone A unusable when unseal fails there, and zone B remains claimable', async () => {
    await enrollmentStorage.saveEnrollment(
      enrollmentFor(AUTH_A, idA, 'token-a', 'pk-a', 'Acme'),
    );
    await enrollmentStorage.saveEnrollment(
      enrollmentFor(AUTH_B, idB, 'token-b', 'pk-b', 'Other'),
    );

    nativeModule.unsealSecret.mockImplementation(
      async (installationScopeId: string, logicalKey: string, sealedPayload: string) => {
        if (installationScopeId === scopeA) {
          throw new Error('unseal_failed_zone_a');
        }
        const parsed = JSON.parse(sealedPayload) as {
          installationScopeId: string;
          key: string;
          plaintext: string;
        };
        if (parsed.installationScopeId !== installationScopeId || parsed.key !== logicalKey) {
          throw new Error('unseal_scope_mismatch');
        }
        return parsed.plaintext;
      },
    );

    const listed = await enrollmentStorage.listEnrollmentsDetailed();
    expect(listed.broken.map(item => item.id)).toEqual([idA]);
    expect(listed.broken[0].reason).toBe('secret_rehydration_failed');
    expect(listed.enrollments).toHaveLength(1);
    expect(listed.enrollments[0].id).toBe(idB);
    expect(listed.enrollments[0].enrollmentProofToken).toBe('token-b');

    const result = await claimPendingAttempt(listed.enrollments[0]);
    expect(result).toEqual({kind: 'none'});
    expect(mockPending).toHaveBeenCalledWith(
      expect.objectContaining({
        enrollmentId: SERVER_ENROLLMENT_ID,
        enrollmentProofToken: 'token-b',
      }),
      AUTH_B,
    );
    expect(nativeModule.sign).toHaveBeenCalledWith(idB, expect.any(String));
    expect(nativeModule.sign).not.toHaveBeenCalledWith(idA, expect.anything());
  });
});
