/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: nativeCrypto
 * Description: Bridge to the Android/iOS native crypto module implementing RSA key management.
 * Security Context: Ensures the React Native layer invokes the hardened implementations derived from docs/CRYPTO.md.
 * @since 2025
 */

import {NativeModules} from 'react-native';

type NativeModuleShape = {
  generateRsaKeyPair(alias: string): Promise<boolean>;
  getPublicKey(alias: string): Promise<string>;
  sign(alias: string, dataBase64: string): Promise<string>;
  deleteKey(alias: string): Promise<boolean>;
};

const {EzkeyCryptoModule} = NativeModules;

const fallback = {
  async generateRsaKeyPair(): Promise<boolean> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to generate key pair.');
  },
  async getPublicKey(): Promise<string> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to retrieve public key.');
  },
  async sign(): Promise<string> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to sign payload.');
  },
  async deleteKey(): Promise<boolean> {
    throw new Error('EzkeyCryptoModule is not linked. Unable to delete key.');
  },
} satisfies NativeModuleShape;

const cryptoModule = (EzkeyCryptoModule as NativeModuleShape | undefined) ?? fallback;

export const isNativeCryptoLinked = Boolean(EzkeyCryptoModule);

/**
 * Delegate exposing the native crypto module through a stable interface.
 *
 * @since 2025
 */
export const nativeCrypto = {
  generateKeyPair: (alias: string) => cryptoModule.generateRsaKeyPair(alias),
  getPublicKey: (alias: string) => cryptoModule.getPublicKey(alias),
  sign: (alias: string, payloadBase64: string) => cryptoModule.sign(alias, payloadBase64),
  deleteKey: (alias: string) => cryptoModule.deleteKey(alias),
};

export type NativeCrypto = typeof nativeCrypto;
