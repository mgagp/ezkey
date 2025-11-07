import {NativeModules} from 'react-native';

type NativeModuleShape = {
  generateRsaKeyPair(alias: string): Promise<boolean>;
  getPublicKey(alias: string): Promise<string>;
  sign(alias: string, dataBase64: string): Promise<string>;
  deleteKey(alias: string): Promise<boolean>;
};

const {EzkeyCryptoModule} = NativeModules;

if (!EzkeyCryptoModule) {
  throw new Error('EzkeyCryptoModule is not linked. Ensure native modules are properly installed.');
}

const cryptoModule = EzkeyCryptoModule as NativeModuleShape;

export const nativeCrypto = {
  generateKeyPair: (alias: string) => cryptoModule.generateRsaKeyPair(alias),
  getPublicKey: (alias: string) => cryptoModule.getPublicKey(alias),
  sign: (alias: string, payloadBase64: string) => cryptoModule.sign(alias, payloadBase64),
  deleteKey: (alias: string) => cryptoModule.deleteKey(alias),
};

export type NativeCrypto = typeof nativeCrypto;
