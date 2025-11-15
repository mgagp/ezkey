/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: cryptoService
 * Description: Factory-backed facade that abstracts native cryptographic primitives for enrollment and authentication.
 * Security Context: Complies with the RSA requirements detailed in docs/CRYPTO.md by preferring native modules and
 *                   gracefully downgrading to deterministic mocks for development-only flows.
 * @since 2025
 */

import {Platform} from 'react-native';
import {mockCrypto} from './mockCrypto';
import {isNativeCryptoLinked, nativeCrypto} from './nativeCrypto';

/**
 * Contract implemented by crypto delegates.
 *
 * @since 2025
 */
type CryptoDelegate = {
  generateKeyPair(alias: string): Promise<boolean>;
  getPublicKey(alias: string): Promise<string>;
  sign(alias: string, payloadBase64: string): Promise<string>;
  deleteKey(alias: string): Promise<boolean>;
};

type CryptoProvider = 'native' | 'mock';

/**
 * Provides a uniform cryptographic interface across platforms.
 *
 * @since 2025
 */
class CryptoService {
  private delegate: CryptoDelegate;
  private provider: CryptoProvider;

  constructor(delegate: CryptoDelegate, provider: CryptoProvider) {
    this.delegate = delegate;
    this.provider = provider;
  }

  /**
   * Builds a crypto service that prefers the native RSA implementation.
   *
   * @return Crypto service instance backed by native or mock implementation.
   * @since 2025
   */
  static create(): CryptoService {
    if (Platform.OS === 'android' || Platform.OS === 'ios') {
      try {
        if (isNativeCryptoLinked) {
          return new CryptoService(nativeCrypto, 'native');
        }
        throw new Error('Native crypto module unavailable');
      } catch (error) {
        console.warn('[cryptoService] Falling back to mock crypto adapter:', error);
        return new CryptoService(mockCrypto, 'mock');
      }
    }
    return new CryptoService(mockCrypto, 'mock');
  }

  /**
   * Indicates which crypto delegate is currently active.
   *
   * @return Active provider label.
   * @since 2025
   */
  get activeProvider(): CryptoProvider {
    return this.provider;
  }

  /**
   * Ensures an RSA key pair exists for the given alias, generating one if necessary.
   *
   * This helper is aligned with the key management workflow described in `docs/CRYPTO.md`, guaranteeing that
   * authentication signatures reuse the same device key pair.
   *
   * @param alias Android keystore alias.
   * @return Base64-encoded RSA public key compliant with X.509 format.
   * @since 2025
   */
  async ensureKeyPair(alias: string): Promise<string> {
    try {
      const publicKey = await this.delegate.getPublicKey(alias);
      return publicKey;
    } catch (error) {
      await this.delegate.generateKeyPair(alias);
      return this.delegate.getPublicKey(alias);
    }
  }

  /**
   * Delegates RSA key pair generation to the active provider.
   *
   * @param alias Android keystore alias.
   * @return Whether the key pair exists after the call.
   * @since 2025
   */
  generateKeyPair(alias: string) {
    return this.delegate.generateKeyPair(alias);
  }

  /**
   * Retrieves the X.509 encoded public key for the requested alias.
   *
   * @param alias Android keystore alias.
   * @return Base64 encoded public key.
   * @since 2025
   */
  getPublicKey(alias: string) {
    return this.delegate.getPublicKey(alias);
  }

  /**
   * Signs the provided payload using SHA256withRSA, mirroring the backend `SignatureService`.
   *
   * @param alias Android keystore alias.
   * @param payloadBase64 Base64-encoded payload to sign.
   * @return Base64 encoded signature.
   * @since 2025
   */
  sign(alias: string, payloadBase64: string) {
    return this.delegate.sign(alias, payloadBase64);
  }

  /**
   * Deletes the key material associated with the alias.
   *
   * @param alias Android keystore alias.
   * @return Whether the deletion completed successfully.
   * @since 2025
   */
  deleteKey(alias: string) {
    return this.delegate.deleteKey(alias);
  }
}

export const cryptoService = CryptoService.create();

export type CryptoServiceInstance = CryptoService;

