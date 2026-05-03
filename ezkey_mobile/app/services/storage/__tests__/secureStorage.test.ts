jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
  },
}));

jest.mock('react-native-keychain', () => ({
  ACCESSIBLE: {
    AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY: 'AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY',
  },
  setGenericPassword: jest.fn(),
  getGenericPassword: jest.fn(),
  resetGenericPassword: jest.fn(),
}));

import AsyncStorage from '@react-native-async-storage/async-storage';
import * as Keychain from 'react-native-keychain';
import {createSecureStorage} from '../secureStorage';

const mockAsyncStorage = jest.mocked(AsyncStorage);
const mockKeychain = jest.mocked(Keychain);

describe('secureStorage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAsyncStorage.getItem.mockResolvedValue(null);
    mockAsyncStorage.setItem.mockResolvedValue();
    mockAsyncStorage.removeItem.mockResolvedValue();
    mockKeychain.getGenericPassword.mockResolvedValue(false as never);
    mockKeychain.setGenericPassword.mockResolvedValue({service: 'ignored'} as never);
    mockKeychain.resetGenericPassword.mockResolvedValue(true as never);
  });

  it('stores sealed payloads in AsyncStorage on Android', async () => {
    const crypto = {
      sealSecret: jest
        .fn()
        .mockResolvedValue('{"version":1,"algorithm":"AES/GCM/NoPadding"}'),
      unsealSecret: jest.fn(),
    };
    const storage = createSecureStorage({
      metadata: AsyncStorage,
      keychain: Keychain,
      crypto,
      platformOs: 'android',
      nativeCryptoLinked: true,
    });

    await storage.setItem('secret-key', 'secret-value');

    expect(crypto.sealSecret).toHaveBeenCalledWith('secret-key', 'secret-value');
    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/sealed-secret.secret-key',
      '{"version":1,"algorithm":"AES/GCM/NoPadding"}',
    );
    expect(mockKeychain.resetGenericPassword).toHaveBeenCalledWith({
      service: 'org.ezkey.mobile.secure.secret-key',
    });
  });

  it('unseals AsyncStorage payloads on Android', async () => {
    const crypto = {
      sealSecret: jest.fn(),
      unsealSecret: jest.fn().mockResolvedValue('secret-value'),
    };
    mockAsyncStorage.getItem.mockResolvedValue(
      '{"version":1,"algorithm":"AES/GCM/NoPadding"}',
    );
    const storage = createSecureStorage({
      metadata: AsyncStorage,
      keychain: Keychain,
      crypto,
      platformOs: 'android',
      nativeCryptoLinked: true,
    });

    await expect(storage.getItem('secret-key')).resolves.toBe('secret-value');

    expect(crypto.unsealSecret).toHaveBeenCalledWith(
      'secret-key',
      '{"version":1,"algorithm":"AES/GCM/NoPadding"}',
    );
    expect(mockKeychain.getGenericPassword).not.toHaveBeenCalled();
  });

  it('migrates legacy keychain values into sealed AsyncStorage on Android', async () => {
    const crypto = {
      sealSecret: jest
        .fn()
        .mockResolvedValue('{"version":1,"algorithm":"AES/GCM/NoPadding"}'),
      unsealSecret: jest.fn(),
    };
    mockKeychain.getGenericPassword.mockResolvedValue({password: 'legacy-value'} as never);
    const storage = createSecureStorage({
      metadata: AsyncStorage,
      keychain: Keychain,
      crypto,
      platformOs: 'android',
      nativeCryptoLinked: true,
    });

    await expect(storage.getItem('secret-key')).resolves.toBe('legacy-value');

    expect(crypto.sealSecret).toHaveBeenCalledWith('secret-key', 'legacy-value');
    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/sealed-secret.secret-key',
      '{"version":1,"algorithm":"AES/GCM/NoPadding"}',
    );
    expect(mockKeychain.resetGenericPassword).toHaveBeenCalledWith({
      service: 'org.ezkey.mobile.secure.secret-key',
    });
  });

  it('falls back to Keychain on iOS', async () => {
    const crypto = {
      sealSecret: jest.fn(),
      unsealSecret: jest.fn(),
    };
    mockKeychain.getGenericPassword.mockResolvedValue({password: 'ios-value'} as never);
    const storage = createSecureStorage({
      metadata: AsyncStorage,
      keychain: Keychain,
      crypto,
      platformOs: 'ios',
      nativeCryptoLinked: true,
    });

    await storage.setItem('secret-key', 'ios-value');
    await expect(storage.getItem('secret-key')).resolves.toBe('ios-value');
    await storage.removeItem('secret-key');

    expect(mockKeychain.setGenericPassword).toHaveBeenCalledWith('secret-key', 'ios-value', {
      service: 'org.ezkey.mobile.secure.secret-key',
      accessible: 'AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY',
    });
    expect(mockAsyncStorage.setItem).not.toHaveBeenCalled();
    expect(mockKeychain.resetGenericPassword).toHaveBeenCalledWith({
      service: 'org.ezkey.mobile.secure.secret-key',
    });
  });
});