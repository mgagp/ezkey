/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: nativeCrypto
 * Description: Bridge to the Android/iOS native crypto module implementing EC P-256 key management.
 * Security Context: Ensures the React Native layer invokes the hardware-backed implementations as described in docs/MOBILE_CRYPTO_REFERENCE.md.
 * @since 2025
 */

import {NativeModules} from 'react-native';

type NativeModuleShape = {
  generateEnrollmentKeyPair(enrollmentId: string): Promise<boolean>;
  getPublicKey(enrollmentId: string): Promise<string>;
  sign(enrollmentId: string, data: string): Promise<string>;
  verify(data: string, signatureBase64: string, publicKeyBase64: string): Promise<boolean>;
  deleteKeyPair(enrollmentId: string): Promise<boolean>;
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
} satisfies NativeModuleShape;

const cryptoModule =
  (EzkeyCryptoModule as NativeModuleShape | undefined) ?? fallback;

export const isNativeCryptoLinked = Boolean(EzkeyCryptoModule);

/**
 * Delegate exposing the native crypto module through a stable interface.
 *
 * Uses EC P-256 (Elliptic Curve P-256) with hardware-backed storage.
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
};

export type NativeCrypto = typeof nativeCrypto;
