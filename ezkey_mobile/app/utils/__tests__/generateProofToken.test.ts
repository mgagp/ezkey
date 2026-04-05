/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 */

import {NativeModules} from 'react-native';
import {generateProofToken} from '../generateProofToken';

const defaultMockToken =
  'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8.ICEiIyQlJicoKSorLC0uLw';

describe('generateProofToken', () => {
  afterEach(() => {
    (NativeModules.EzkeyCryptoModule.generateProofToken as jest.Mock).mockResolvedValue(
      defaultMockToken,
    );
  });

  it('delegates to EzkeyCryptoModule (wire format golden from native mock)', async () => {
    await expect(generateProofToken()).resolves.toBe(defaultMockToken);
  });

  it('returns distinct values when native returns distinct strings', async () => {
    (NativeModules.EzkeyCryptoModule.generateProofToken as jest.Mock)
      .mockResolvedValueOnce('partA.partB')
      .mockResolvedValueOnce('partC.partD');

    const first = await generateProofToken();
    const second = await generateProofToken();
    expect(first).not.toBe(second);
  });

  it('accepts plausible dotted URL-safe tokens from native', async () => {
    (NativeModules.EzkeyCryptoModule.generateProofToken as jest.Mock).mockResolvedValue(
      'AbCdEfGhIjKlMnOpQrStUvWxYz0123456789-_._ab',
    );
    const token = await generateProofToken();
    const parts = token.split('.');
    expect(parts).toHaveLength(2);
    expect(token).toMatch(/^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/);
  });
});
