/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: cryptoService
 * Description: Factory-backed facade that abstracts native cryptographic primitives for enrollment and authentication.
 * Security Context: Uses EC P-256 with native platform keystore integration as described in docs/MOBILE_CRYPTO_REFERENCE.md.
 * @since 2025
 */

import {Platform} from 'react-native';
import type {DevicePrivateKeyStorageTier} from '../api/types';
import {isNativeCryptoLinked, nativeCrypto} from './nativeCrypto';

/**
 * Provides a uniform cryptographic interface across platforms.
 *
 * Uses EC P-256 (Elliptic Curve P-256) with native platform keystore integration.
 * Requires native crypto module - no fallbacks or mocks.
 *
 * @since 2025
 */
class CryptoService {
  /**
   * Builds a crypto service using the native EC P-256 implementation.
   *
   * **Security Policy**: The application requires the native crypto module to function.
   * This ensures keys are created and used through the supported native platform APIs.
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
        'Ezkey requires Android or iOS for native secure key storage.',
      );
    }

    // Verify native module is available
    if (!isNativeCryptoLinked) {
      throw new Error(
        'CRITICAL: Native crypto module not available. ' +
        'Application cannot function securely without native secure key storage. ' +
        'Please ensure the native module is properly linked.',
      );
    }

    return new CryptoService();
  }

  /**
   * Creates an EC P-256 key pair for the local enrollment handle if absent (enrollment wizard only).
   *
   * The key pair is stored through the native platform keystore integration.
   * Pass the installation-scoped local enrollment id (not the raw server id alone).
   *
   * @param localEnrollmentId Local enrollment handle (Keystore alias suffix).
   * @return Whether the key pair exists after the call.
   * @since 2025
   */
  async ensureEnrollmentKeyPair(localEnrollmentId: string): Promise<boolean> {
    return nativeCrypto.generateEnrollmentKeyPair(localEnrollmentId);
  }

  /**
   * Requires an existing enrollment key pair — never generates (MOB-013).
   *
   * Pending/respond must fail closed when the alias is missing; the Auth API is
   * already bound to the public key submitted at verify.
   *
   * @param localEnrollmentId Local enrollment handle (Keystore alias suffix).
   * @throws Error with message {@code ENROLLMENT_KEY_MISSING} when the key is absent.
   * @since 2026
   */
  async requireEnrollmentKeyPair(localEnrollmentId: string): Promise<void> {
    try {
      await nativeCrypto.getPublicKey(localEnrollmentId);
    } catch {
      const error = new Error('ENROLLMENT_KEY_MISSING');
      error.name = 'ENROLLMENT_KEY_MISSING';
      throw error;
    }
  }

  /**
   * Best-effort delete of the enrollment key pair (orphan cleanup / wipe).
   *
   * @param localEnrollmentId Local enrollment handle (Keystore alias suffix).
   * @return Whether native delete reported success.
   * @since 2026
   */
  async deleteEnrollmentKeyPair(localEnrollmentId: string): Promise<boolean> {
    return nativeCrypto.deleteKeyPair(localEnrollmentId);
  }

  canUseProtectedSigning(): Promise<boolean> {
    return nativeCrypto.canUseProtectedSigning();
  }

  authenticateSecurityPreferenceDowngrade(): Promise<boolean> {
    return nativeCrypto.authenticateSecurityPreferenceDowngrade();
  }

  /**
   * Retrieves the EC P-256 public key for a given enrollment ID.
   *
   * The public key is derived from the native key entry and encoded as X.509 SubjectPublicKeyInfo.
   *
   * @param enrollmentId The enrollment ID to get the public key for.
   * @return Base64-encoded X.509 public key (ASN.1 DER format).
   * @since 2025
   */
  getPublicKey(enrollmentId: string): Promise<string> {
    return nativeCrypto.getPublicKey(enrollmentId);
  }

  /**
   * Returns where the enrollment private key is stored (NONE / STANDARD / STRONG) for Auth API verify.
   * Android derives this from {@code KeyInfo} (StrongBox vs other secure hardware). iOS returns NONE until parity.
   *
   * @param enrollmentId Enrollment identifier used as keystore alias.
   */
  async getEnrollmentPrivateKeyStorageTier(
    enrollmentId: string,
  ): Promise<DevicePrivateKeyStorageTier> {
    const raw = await nativeCrypto.getEnrollmentPrivateKeyStorageTier(enrollmentId);
    if (raw === 'NONE' || raw === 'STANDARD' || raw === 'STRONG') {
      return raw;
    }
    return 'NONE';
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

  signForRespond(
    enrollmentId: string,
    data: string,
    requireAuthentication: boolean,
  ): Promise<string> {
    if (requireAuthentication) {
      return nativeCrypto.signWithAuthentication(enrollmentId, data);
    }

    return nativeCrypto.sign(enrollmentId, data);
  }

  /**
   * Verifies an Ed25519 signature over the given data with the integration public key.
   * Used for integration-signed fields on Pending and Respond responses (raw 32-byte key and
   * 64-byte signature on the wire; native layer uses flexible Base64 decode).
   *
   * @param data The exact payload that was signed (UTF-8).
   * @param signatureBase64 Base64-encoded raw Ed25519 signature (typically Base64URL from the API).
   * @param publicKeyBase64 Base64-encoded raw Ed25519 public key from enrollment bind.
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

export const cryptoService = CryptoService.create();

export type CryptoServiceInstance = CryptoService;
