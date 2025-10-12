/*
 * Ezkey - Open Source MFA/Passkey Alternative
 *
 * Copyright (c) 2025 Ezkey contributors
 * Licensed under the MIT License. See LICENSE file in the project root for full license information.
 *
 * Module: ExpoCryptoNative
 * Description: TypeScript bindings for native crypto module
 */

import { NativeModules } from 'react-native';

const { ExpoCryptoNative } = NativeModules;

export interface KeyPair {
  privateKey: string;
  publicKey: string;
}

export default {
  generateRsaKeyPair(keySize: number): KeyPair {
    return ExpoCryptoNative.generateRsaKeyPair(keySize);
  },
  
  generateProofToken(): string {
    return ExpoCryptoNative.generateProofToken();
  },
  
  generateSignature(data: string, privateKey: string): string {
    return ExpoCryptoNative.generateSignature(data, privateKey);
  },
  
  validateSignature(data: string, signature: string, publicKey: string): boolean {
    return ExpoCryptoNative.validateSignature(data, signature, publicKey);
  }
};
