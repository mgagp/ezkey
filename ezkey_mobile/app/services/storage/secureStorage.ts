/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: secureStorage
 * Description: Platform-aware secret storage delegate using Android Keystore-backed sealed secrets on Android and Keychain fallback elsewhere.
 * Security Context: Aligns with storage guidance in docs/features/AUTH_SECURITY.md by preferring app-level keystore-backed secret sealing on Android while preserving device-scoped secure storage compatibility elsewhere.
 * @since 2025
 */

import AsyncStorage from '@react-native-async-storage/async-storage';
import {Platform} from 'react-native';
import * as Keychain from 'react-native-keychain';
import {isNativeCryptoLinked, nativeCrypto} from '../crypto/nativeCrypto';

const SERVICE_PREFIX = 'org.ezkey.mobile.secure';
const SEALED_SECRET_PREFIX = 'ezkey-mobile/sealed-secret';

const serviceFor = (key: string) => `${SERVICE_PREFIX}.${key}`;
const sealedStorageKeyFor = (key: string) => `${SEALED_SECRET_PREFIX}.${key}`;

type KeychainLike = Pick<
  typeof Keychain,
  'setGenericPassword' | 'getGenericPassword' | 'resetGenericPassword' | 'ACCESSIBLE'
>;

type MetadataStore = Pick<typeof AsyncStorage, 'setItem' | 'getItem' | 'removeItem'>;

type CryptoDelegate = Pick<typeof nativeCrypto, 'sealSecret' | 'unsealSecret'>;

type SecureStorageOptions = {
  metadata: MetadataStore;
  keychain: KeychainLike;
  crypto: CryptoDelegate;
  platformOs: string;
  nativeCryptoLinked: boolean;
};

class SecureStorageDelegate {
  private metadata: MetadataStore;
  private keychain: KeychainLike;
  private crypto: CryptoDelegate;
  private platformOs: string;
  private nativeCryptoLinked: boolean;

  constructor(options: SecureStorageOptions) {
    this.metadata = options.metadata;
    this.keychain = options.keychain;
    this.crypto = options.crypto;
    this.platformOs = options.platformOs;
    this.nativeCryptoLinked = options.nativeCryptoLinked;
  }

  private shouldUseAndroidSealedSecrets() {
    return this.platformOs === 'android' && this.nativeCryptoLinked;
  }

  private async readLegacyKeychainValue(key: string): Promise<string | undefined> {
    const record = await this.keychain.getGenericPassword({service: serviceFor(key)});
    if (!record) {
      return undefined;
    }
    return record.password;
  }

  /**
   * @param installationScopeId Installation trust-zone scope for the AES seal key on Android
   *     (MOB-017 — `deriveInstallationScopeId`); unused on the Keychain (non-Android) path.
   */
  async setItem(key: string, value: string, installationScopeId: string) {
    if (!this.shouldUseAndroidSealedSecrets()) {
      await this.keychain.setGenericPassword(key, value, {
        service: serviceFor(key),
        accessible: this.keychain.ACCESSIBLE.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
      });
      return;
    }

    const sealedPayload = await this.crypto.sealSecret(installationScopeId, key, value);
    await this.metadata.setItem(sealedStorageKeyFor(key), sealedPayload);
    await this.keychain.resetGenericPassword({service: serviceFor(key)});
  }

  /**
   * @param installationScopeId Installation trust-zone scope for the AES seal key on Android
   *     (MOB-017); must match the scope used on {@link setItem} for this key, or unsealing fails.
   */
  async getItem(key: string, installationScopeId: string): Promise<string | undefined> {
    if (!this.shouldUseAndroidSealedSecrets()) {
      return this.readLegacyKeychainValue(key);
    }

    const sealedPayload = await this.metadata.getItem(sealedStorageKeyFor(key));
    if (sealedPayload) {
      return this.crypto.unsealSecret(installationScopeId, key, sealedPayload);
    }

    const legacyValue = await this.readLegacyKeychainValue(key);
    if (!legacyValue) {
      return undefined;
    }

    await this.setItem(key, legacyValue, installationScopeId);
    return legacyValue;
  }

  async removeItem(key: string) {
    if (this.shouldUseAndroidSealedSecrets()) {
      await this.metadata.removeItem(sealedStorageKeyFor(key));
      await this.keychain.resetGenericPassword({service: serviceFor(key)});
      return;
    }

    await this.keychain.resetGenericPassword({service: serviceFor(key)});
  }
}

export const createSecureStorage = (options: SecureStorageOptions) =>
  new SecureStorageDelegate(options);

export const secureStorage = createSecureStorage({
  metadata: AsyncStorage,
  keychain: Keychain,
  crypto: nativeCrypto,
  platformOs: Platform.OS,
  nativeCryptoLinked: isNativeCryptoLinked,
});

export type SecureStorage = typeof secureStorage;
