/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: secureStorage
 * Description: Thin wrapper around react-native-keychain used to protect enrollment aliases and proof tokens.
 * Security Context: Aligns with storage guidance in docs/features/AUTH_SECURITY.md by scoping entries to the device and
 *                   preventing synchronization across iCloud/Google backups.
 * @since 2025
 */

import * as Keychain from 'react-native-keychain';

const SERVICE_PREFIX = 'org.ezkey.mobile.secure';

const serviceFor = (key: string) => `${SERVICE_PREFIX}.${key}`;

/**
 * Secure storage delegate backed by platform credential stores.
 *
 * @since 2025
 */
export const secureStorage = {
  async setItem(key: string, value: string) {
    await Keychain.setGenericPassword(key, value, {
      service: serviceFor(key),
      accessible: Keychain.ACCESSIBLE.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
    });
  },

  async getItem(key: string): Promise<string | undefined> {
    const record = await Keychain.getGenericPassword({service: serviceFor(key)});
    if (!record) {
      return undefined;
    }
    return record.password;
  },

  async removeItem(key: string) {
    await Keychain.resetGenericPassword({service: serviceFor(key)});
  },
};

export type SecureStorage = typeof secureStorage;
