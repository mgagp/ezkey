/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: mockCrypto
 * Description: Deterministic mock crypto adapter used exclusively for development and automated testing scenarios.
 * Security Context: Explicitly deviates from the hardened RSA implementation in docs/CRYPTO.md and must never ship in
 *                   production builds. Documentation clarifies limitations to avoid misconstrued guarantees.
 * @since 2025
 */

const BASE64_ALPHABET =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';

const keyStore = new Map<
  string,
  {
    publicKey: string;
    privateKey: string;
  }
>();

const randomBase64 = (length: number) => {
  let output = '';
  for (let index = 0; index < length; index += 1) {
    const charIndex = Math.floor(Math.random() * BASE64_ALPHABET.length);
    output += BASE64_ALPHABET[charIndex];
  }
  return output;
};

const generatePlaceholderKeyPair = () => {
  const publicKey = randomBase64(344);
  const privateKey = randomBase64(344);
  return {publicKey, privateKey};
};

/**
 * Mock crypto delegate mirroring the native module surface while generating placeholder material.
 *
 * @since 2025
 */
export const mockCrypto = {
  async generateKeyPair(alias: string): Promise<boolean> {
    const pair = generatePlaceholderKeyPair();
    keyStore.set(alias, pair);
    return true;
  },
  async getPublicKey(alias: string): Promise<string> {
    const entry = keyStore.get(alias);
    if (!entry) {
      throw new Error(`No key pair found for alias ${alias}`);
    }
    return entry.publicKey;
  },
  async sign(alias: string, payloadBase64: string): Promise<string> {
    const entry = keyStore.get(alias);
    if (!entry) {
      throw new Error(`No key pair found for alias ${alias}`);
    }
    return randomBase64(64) + payloadBase64.slice(0, 8);
  },
  async deleteKey(alias: string): Promise<boolean> {
    return keyStore.delete(alias);
  },
};

export type MockCrypto = typeof mockCrypto;

