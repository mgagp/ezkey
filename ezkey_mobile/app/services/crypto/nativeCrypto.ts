/*
 * Ezkey - Open Source Cryptographic MFA Platform
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: nativeCrypto
 * Description: Bridge to the Android/iOS native crypto module implementing EC P-256 key management.
 * Security Context: Ensures the React Native layer invokes the native platform keystore implementations described in docs/MOBILE_CRYPTO_REFERENCE.md.
 * @since 2025
 */

import {NativeModules} from 'react-native';
import type {SecurityLevel} from '../storage/securityPreferenceStorage';

type NativeModuleShape = {
  generateEnrollmentKeyPair(enrollmentId: string, securityLevel?: SecurityLevel): Promise<boolean>;
  canUseProtectedSigning(): Promise<boolean>;
  authenticateSecurityPreferenceDowngrade(): Promise<boolean>;
  getPublicKey(enrollmentId: string): Promise<string>;
  /** NONE | STANDARD | STRONG — matches Auth API DevicePrivateKeyStorageTier */
  getEnrollmentPrivateKeyStorageTier(enrollmentId: string): Promise<string>;
  sign(enrollmentId: string, data: string): Promise<string>;
  signWithAuthentication(enrollmentId: string, data: string): Promise<string>;
  verify(data: string, signatureBase64: string, publicKeyBase64: string): Promise<boolean>;
  deleteKeyPair(enrollmentId: string): Promise<boolean>;
  /**
   * Deletes every installation-scoped seal key (clear-all true reset, MOB-015/MOB-017). Resolves
   * false when none were present or on platforms without the Android seal-key model.
   */
  deleteAllSealKeys(): Promise<boolean>;
  /** UTC ISO-8601 string set at native build time (Android `BuildConfig`); iOS uses bundle mtime proxy. */
  getBuildTimestamp(): Promise<string>;
  /** Same wire format as `SignatureService.generateProofToken()`; uses platform CSPRNG (no `RNGetRandomValues`). */
  generateProofToken(): Promise<string>;
  /** installationScopeId scopes the AES seal key to one installation trust zone (MOB-017). */
  sealSecret(installationScopeId: string, logicalKey: string, plaintext: string): Promise<string>;
  unsealSecret(
    installationScopeId: string,
    logicalKey: string,
    sealedPayload: string,
  ): Promise<string>;
};

const {EzkeyCryptoModule} = NativeModules;

const fallback: NativeModuleShape = {
  async generateEnrollmentKeyPair(): Promise<boolean> {
    throw new Error(
      'EzkeyCryptoModule is not linked. Unable to generate enrollment key pair.',
    );
  },
  async canUseProtectedSigning(): Promise<boolean> {
    return false;
  },
  async authenticateSecurityPreferenceDowngrade(): Promise<boolean> {
    throw new Error(
      'EzkeyCryptoModule does not support confirmation for security setting changes on this platform.',
    );
  },
  async getPublicKey(): Promise<string> {
    throw new Error(
      'EzkeyCryptoModule is not linked. Unable to retrieve public key.',
    );
  },
  async sign(): Promise<string> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to sign payload.');
  },
  async signWithAuthentication(): Promise<string> {
    throw new Error(
      'EzkeyCryptoModule does not support authenticated signing on this platform.',
    );
  },
  async verify(): Promise<boolean> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to verify signature.');
  },
  async deleteKeyPair(): Promise<boolean> {
    throw new Error(
      'EzkeyCryptoModule is not linked. Unable to delete key pair.',
    );
  },
  async deleteAllSealKeys(): Promise<boolean> {
    // Platforms without the Android seal-key model have nothing to delete.
    return false;
  },
  async getBuildTimestamp(): Promise<string> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to read build timestamp.');
  },
  async generateProofToken(): Promise<string> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to generate proof token.');
  },
  async getEnrollmentPrivateKeyStorageTier(): Promise<string> {
    throw new Error(
      'EzkeyCryptoModule is not linked. Unable to read enrollment key storage tier.',
    );
  },
  async sealSecret(): Promise<string> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to seal secret.');
  },
  async unsealSecret(): Promise<string> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to unseal secret.');
  },
} satisfies NativeModuleShape;

const cryptoModule = EzkeyCryptoModule as Partial<NativeModuleShape> | undefined;

export const isNativeCryptoLinked = Boolean(EzkeyCryptoModule);

/**
 * Delegate exposing the native crypto module through a stable interface.
 *
 * Uses EC P-256 (Elliptic Curve P-256) through the native platform keystore integration.
 *
 * @since 2025
 */
export const nativeCrypto = {
  generateEnrollmentKeyPair: (enrollmentId: string, securityLevel?: SecurityLevel) =>
    cryptoModule?.generateEnrollmentKeyPair?.(enrollmentId, securityLevel) ??
    fallback.generateEnrollmentKeyPair(enrollmentId, securityLevel),
  canUseProtectedSigning: () =>
    cryptoModule?.canUseProtectedSigning?.() ?? fallback.canUseProtectedSigning(),
  authenticateSecurityPreferenceDowngrade: () =>
    cryptoModule?.authenticateSecurityPreferenceDowngrade?.() ??
    fallback.authenticateSecurityPreferenceDowngrade(),
  getPublicKey: (enrollmentId: string) =>
    cryptoModule?.getPublicKey?.(enrollmentId) ?? fallback.getPublicKey(enrollmentId),
  sign: (enrollmentId: string, data: string) =>
    cryptoModule?.sign?.(enrollmentId, data) ?? fallback.sign(enrollmentId, data),
  signWithAuthentication: (enrollmentId: string, data: string) =>
    cryptoModule?.signWithAuthentication?.(enrollmentId, data) ??
    fallback.signWithAuthentication(enrollmentId, data),
  verify: (data: string, signatureBase64: string, publicKeyBase64: string) =>
    cryptoModule?.verify?.(data, signatureBase64, publicKeyBase64) ??
    fallback.verify(data, signatureBase64, publicKeyBase64),
  deleteKeyPair: (enrollmentId: string) =>
    cryptoModule?.deleteKeyPair?.(enrollmentId) ?? fallback.deleteKeyPair(enrollmentId),
  deleteAllSealKeys: () =>
    cryptoModule?.deleteAllSealKeys?.() ?? fallback.deleteAllSealKeys(),
  getBuildTimestamp: () =>
    cryptoModule?.getBuildTimestamp?.() ?? fallback.getBuildTimestamp(),
  generateProofToken: () =>
    cryptoModule?.generateProofToken?.() ?? fallback.generateProofToken(),
  getEnrollmentPrivateKeyStorageTier: (enrollmentId: string) =>
    cryptoModule?.getEnrollmentPrivateKeyStorageTier?.(enrollmentId) ??
    fallback.getEnrollmentPrivateKeyStorageTier(enrollmentId),
  sealSecret: (installationScopeId: string, logicalKey: string, plaintext: string) =>
    cryptoModule?.sealSecret?.(installationScopeId, logicalKey, plaintext) ??
    fallback.sealSecret(installationScopeId, logicalKey, plaintext),
  unsealSecret: (installationScopeId: string, logicalKey: string, sealedPayload: string) =>
    cryptoModule?.unsealSecret?.(installationScopeId, logicalKey, sealedPayload) ??
    fallback.unsealSecret(installationScopeId, logicalKey, sealedPayload),
};

export type NativeCrypto = typeof nativeCrypto;
