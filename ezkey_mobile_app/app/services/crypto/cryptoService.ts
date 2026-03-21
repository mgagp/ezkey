/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: cryptoService
 * Description: Factory-backed facade that abstracts native cryptographic primitives for enrollment and authentication.
 * Security Context: Uses EC P-256 with hardware-backed storage as described in docs/MOBILE_CRYPTO_REFERENCE.md.
 * @since 2025
 */

import {Platform} from 'react-native';
import {isNativeCryptoLinked, nativeCrypto} from './nativeCrypto';

/**
 * Provides a uniform cryptographic interface across platforms.
 *
 * Uses EC P-256 (Elliptic Curve P-256) with hardware-backed storage.
 * Requires native crypto module - no fallbacks or mocks.
 *
 * @since 2025
 */
class CryptoService {
  /**
   * Builds a crypto service using the native EC P-256 implementation.
   *
   * **Security Policy**: The application requires the native crypto module to function.
   * This ensures hardware-backed keys are always used.
   *
   * @return Crypto service instance backed by native implementation.
   * @throws Error if native module unavailable or platform not supported.
   * @since 2025
   */
  static create(): CryptoService {
    // Verify platform is supported
    if (Platform.OS !== 'android' && Platform.OS !== 'ios') {
      throw new Error(
        'CRITICAL: Mobile platform required for secure crypto operations. ' +
        'Ezkey requires Android or iOS for hardware-backed key storage.',
      );
    }

    // Verify native module is available
    if (!isNativeCryptoLinked) {
      throw new Error(
        'CRITICAL: Native crypto module not available. ' +
        'Application cannot function securely without hardware-backed keys. ' +
        'Please ensure the native module is properly linked.',
      );
    }

    return new CryptoService();
  }

  /**
   * Lazy singleton for use when native module may not be linked at startup (e.g. iOS or dev).
   * Call this when performing enrollment/auth; throws if native crypto is not available.
   *
   * @return Crypto service instance.
   * @throws Error if native module unavailable or platform not supported.
   */
  static getInstance(): CryptoService {
    if (Platform.OS !== 'android' && Platform.OS !== 'ios') {
      throw new Error(
        'CRITICAL: Mobile platform required for secure crypto operations.',
      );
    }
    if (!isNativeCryptoLinked) {
      throw new Error(
        'CRITICAL: Native crypto module not available. Please ensure the native module is properly linked.',
      );
    }
    return new CryptoService();
  }

  /**
   * Ensures an EC P-256 key pair exists for the given enrollment, generating it if necessary.
   *
   * The key pair is stored in hardware-backed storage (StrongBox/Secure Enclave).
   * Each enrollment gets its own key pair.
   *
   * @param enrollmentId The enrollment ID to ensure the key pair for.
   * @return Whether the key pair exists after the call.
   * @since 2025
   */
  async ensureEnrollmentKeyPair(enrollmentId: string): Promise<boolean> {
    return nativeCrypto.generateEnrollmentKeyPair(enrollmentId);
  }

  /**
   * Retrieves the EC P-256 public key for a given enrollment ID.
   *
   * The public key is stored in hardware-backed storage and encoded as X.509 SubjectPublicKeyInfo.
   *
   * @param enrollmentId The enrollment ID to get the public key for.
   * @return Base64-encoded X.509 public key (ASN.1 DER format).
   * @since 2025
   */
  getPublicKey(enrollmentId: string): Promise<string> {
    return nativeCrypto.getPublicKey(enrollmentId);
  }

  /**
   * Signs the provided data using EC P-256 with ECDSA-SHA256.
   *
   * @param enrollmentId The enrollment ID to sign with.
   * @param data UTF-8 string data to sign.
   * @return Base64-encoded ECDSA signature (ASN.1 DER format).
   * @since 2025
   */
  sign(enrollmentId: string, data: string): Promise<string> {
    return nativeCrypto.sign(enrollmentId, data);
  }

  /**
   * Verifies an ECDSA-SHA256 signature over the given data with the given public key.
   * Used to verify the integration signature on the Pending response payload.
   *
   * @param data The exact payload that was signed (UTF-8).
   * @param signatureBase64 Base64-encoded ECDSA signature.
   * @param publicKeyBase64 Base64-encoded X.509 public key (e.g. integration public key).
   * @return true if the signature is valid.
   * @since 2025
   */
  verify(
    data: string,
    signatureBase64: string,
    publicKeyBase64: string,
  ): Promise<boolean> {
    return nativeCrypto.verify(data, signatureBase64, publicKeyBase64);
  }
}

let _instance: CryptoService | null = null;

/**
 * Returns the crypto service instance. Lazy-initialized so the app can load without
 * the native module (e.g. on iOS or in dev). Throws when first called if native crypto is not linked.
 *
 * @return Crypto service instance.
 * @since 2025
 */
export function getCryptoService(): CryptoService {
  if (_instance == null) {
    _instance = CryptoService.create();
  }
  return _instance;
}

export type CryptoServiceInstance = CryptoService;
