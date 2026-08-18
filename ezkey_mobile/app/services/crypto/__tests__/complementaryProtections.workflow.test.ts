/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2026 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Complementary Keystore families — MOBILE_DATA_MODEL.md Cornerstone.
 * Signing uses the local enrollment id; sealing uses the installation scope id.
 * Native AES isolation stays on instrumented androidTest (MOB-017). Jest never asserts StrongBox STRONG.
 */

jest.mock('react-native-keychain', () => ({
  ACCESSIBLE: {
    AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY: 'AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY',
  },
  setGenericPassword: jest.fn(),
  getGenericPassword: jest.fn().mockResolvedValue(false),
  resetGenericPassword: jest.fn().mockResolvedValue(true),
}));

jest.mock('../../storage/secureStorage', () => {
  const actual = jest.requireActual('../../storage/secureStorage') as typeof import('../../storage/secureStorage');
  const AsyncStorage = require('@react-native-async-storage/async-storage').default;
  const Keychain = require('react-native-keychain');
  const {nativeCrypto} = require('../nativeCrypto') as typeof import('../nativeCrypto');
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

import {NativeModules} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  deriveInstallationScopeId,
  deriveLocalEnrollmentId,
} from '../../../utils/localEnrollmentIdentity';
import {enrollmentStorage} from '../../storage/enrollmentStorage';
import {nativeCrypto} from '../nativeCrypto';

const nativeModule = NativeModules.EzkeyCryptoModule as {
  sealSecret: jest.Mock;
  sign: jest.Mock;
  deleteKeyPair: jest.Mock;
  deleteAllSealKeys?: jest.Mock;
};

const AUTH_A = 'https://auth.acme.example';
const AUTH_B = 'https://auth.other.example';
const localId = deriveLocalEnrollmentId(AUTH_A, '1');
const localIdB = deriveLocalEnrollmentId(AUTH_B, '1');
const scopeId = deriveInstallationScopeId(AUTH_A);

describe('complementary protections (signing vs per-installation seal)', () => {
  beforeEach(async () => {
    jest.clearAllMocks();
    await AsyncStorage.clear();
    nativeModule.deleteAllSealKeys = jest.fn().mockResolvedValue(true);
    nativeModule.deleteKeyPair.mockResolvedValue(true);
    nativeModule.sign.mockResolvedValue('device-sig');
  });

  it('forwards sign(localEnrollmentId) independently from sealSecret(installationScopeId)', async () => {
    expect(localId).not.toBe(scopeId);

    await nativeCrypto.sign(localId, 'pending-payload');
    await nativeCrypto.sealSecret(scopeId, 'ezkey-mobile/enrollment-proof-token.x', 'token-a');

    expect(nativeModule.sign).toHaveBeenCalledWith(localId, 'pending-payload');
    expect(nativeModule.sealSecret).toHaveBeenCalledWith(
      scopeId,
      'ezkey-mobile/enrollment-proof-token.x',
      'token-a',
    );
    expect(nativeModule.sign.mock.calls[0][0]).not.toBe(scopeId);
    expect(nativeModule.sealSecret.mock.calls[0][0]).not.toBe(localId);
  });

  it('clear-all deletes signing aliases and sweeps every installation seal key', async () => {
    await enrollmentStorage.saveEnrollment({
      id: localId,
      integrationId: '1',
      integrationName: 'Acme',
      createdAt: '2026-08-18T00:00:00.000Z',
      lastActivityAt: '2026-08-18T00:00:00.000Z',
      enrollmentProofToken: 'token-a',
      enrollmentId: '1',
      integrationPublicKey: 'pk-a',
      installation: {
        id: AUTH_A,
        authUrl: AUTH_A,
        host: 'auth.acme.example',
        name: 'Acme',
      },
    });
    await enrollmentStorage.saveEnrollment({
      id: localIdB,
      integrationId: '1',
      integrationName: 'Other',
      createdAt: '2026-08-18T00:00:00.000Z',
      lastActivityAt: '2026-08-18T00:00:00.000Z',
      enrollmentProofToken: 'token-b',
      enrollmentId: '1',
      integrationPublicKey: 'pk-b',
      installation: {
        id: AUTH_B,
        authUrl: AUTH_B,
        host: 'auth.other.example',
        name: 'Other',
      },
    });

    await enrollmentStorage.clearAll();

    expect(nativeModule.deleteKeyPair).toHaveBeenCalledWith(localId);
    expect(nativeModule.deleteKeyPair).toHaveBeenCalledWith(localIdB);
    expect(nativeModule.deleteAllSealKeys).toHaveBeenCalledTimes(1);
  });
});
