/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: mockSecureStorage
 * Description: Volatile in-memory replacement for secure storage used in development and automated tests.
 * Security Context: Clearly identified as non-production compliant to prevent accidental deployment, complementing
 *                   the constraints described in docs/features/AUTH_SECURITY.md.
 * @since 2025
 */

const secureStore = new Map<string, string>();

/**
 * Mock secure storage adapter exposing the same contract as the platform implementation.
 *
 * @since 2025
 */
export const mockSecureStorage = {
  async setItem(key: string, value: string) {
    secureStore.set(key, value);
  },
  async getItem(key: string): Promise<string | undefined> {
    return secureStore.get(key);
  },
  async removeItem(key: string) {
    secureStore.delete(key);
  },
  async clearAll() {
    secureStore.clear();
  },
};

export type MockSecureStorage = typeof mockSecureStorage;

