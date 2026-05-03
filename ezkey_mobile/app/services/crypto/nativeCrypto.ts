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

type NativeModuleShape = {
  generateEnrollmentKeyPair(enrollmentId: string): Promise<boolean>;
  getPublicKey(enrollmentId: string): Promise<string>;
  /** NONE | STANDARD | STRONG — matches Auth API DevicePrivateKeyStorageTier */
  getEnrollmentPrivateKeyStorageTier(enrollmentId: string): Promise<string>;
  sign(enrollmentId: string, data: string): Promise<string>;
  verify(data: string, signatureBase64: string, publicKeyBase64: string): Promise<boolean>;
  deleteKeyPair(enrollmentId: string): Promise<boolean>;
  /** UTC ISO-8601 string set at native build time (Android `BuildConfig`); iOS uses bundle mtime proxy. */
  getBuildTimestamp(): Promise<string>;
  /** Same wire format as `SignatureService.generateProofToken()`; uses platform CSPRNG (no `RNGetRandomValues`). */
  generateProofToken(): Promise<string>;
  sealSecret(logicalKey: string, plaintext: string): Promise<string>;
  unsealSecret(logicalKey: string, sealedPayload: string): Promise<string>;
};

const {EzkeyCryptoModule} = NativeModules;

const fallback = {
  async generateEnrollmentKeyPair(): Promise<boolean> {
    throw new Error(
      'EzkeyCryptoModule is not linked. Unable to generate enrollment key pair.',
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
  async verify(): Promise<boolean> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to verify signature.');
  },
  async deleteKeyPair(): Promise<boolean> {
    throw new Error(
      'EzkeyCryptoModule is not linked. Unable to delete key pair.',
    );
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

const cryptoModule =
  (EzkeyCryptoModule as NativeModuleShape | undefined) ?? fallback;

export const isNativeCryptoLinked = Boolean(EzkeyCryptoModule);

/**
 * Delegate exposing the native crypto module through a stable interface.
 *
 * Uses EC P-256 (Elliptic Curve P-256) through the native platform keystore integration.
 *
 * @since 2025
 */
export const nativeCrypto = {
  generateEnrollmentKeyPair: (enrollmentId: string) =>
    cryptoModule.generateEnrollmentKeyPair(enrollmentId),
  getPublicKey: (enrollmentId: string) =>
    cryptoModule.getPublicKey(enrollmentId),
  sign: (enrollmentId: string, data: string) =>
    cryptoModule.sign(enrollmentId, data),
  verify: (data: string, signatureBase64: string, publicKeyBase64: string) =>
    cryptoModule.verify(data, signatureBase64, publicKeyBase64),
  deleteKeyPair: (enrollmentId: string) =>
    cryptoModule.deleteKeyPair(enrollmentId),
  getBuildTimestamp: () => cryptoModule.getBuildTimestamp(),
  generateProofToken: () => cryptoModule.generateProofToken(),
  getEnrollmentPrivateKeyStorageTier: (enrollmentId: string) =>
    cryptoModule.getEnrollmentPrivateKeyStorageTier(enrollmentId),
  sealSecret: (logicalKey: string, plaintext: string) =>
    cryptoModule.sealSecret(logicalKey, plaintext),
  unsealSecret: (logicalKey: string, sealedPayload: string) =>
    cryptoModule.unsealSecret(logicalKey, sealedPayload),
};

export type NativeCrypto = typeof nativeCrypto;
