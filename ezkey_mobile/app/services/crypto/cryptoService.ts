import {Platform} from 'react-native';
import {mockCrypto} from './mockCrypto';
import {isNativeCryptoLinked, nativeCrypto} from './nativeCrypto';

type CryptoDelegate = {
  generateKeyPair(alias: string): Promise<boolean>;
  getPublicKey(alias: string): Promise<string>;
  sign(alias: string, payloadBase64: string): Promise<string>;
  deleteKey(alias: string): Promise<boolean>;
};

type CryptoProvider = 'native' | 'mock';

class CryptoService {
  private delegate: CryptoDelegate;
  private provider: CryptoProvider;

  constructor(delegate: CryptoDelegate, provider: CryptoProvider) {
    this.delegate = delegate;
    this.provider = provider;
  }

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

  get activeProvider(): CryptoProvider {
    return this.provider;
  }

  async ensureKeyPair(alias: string): Promise<string> {
    try {
      const publicKey = await this.delegate.getPublicKey(alias);
      return publicKey;
    } catch (error) {
      await this.delegate.generateKeyPair(alias);
      return this.delegate.getPublicKey(alias);
    }
  }

  generateKeyPair(alias: string) {
    return this.delegate.generateKeyPair(alias);
  }

  getPublicKey(alias: string) {
    return this.delegate.getPublicKey(alias);
  }

  sign(alias: string, payloadBase64: string) {
    return this.delegate.sign(alias, payloadBase64);
  }

  deleteKey(alias: string) {
    return this.delegate.deleteKey(alias);
  }
}

export const cryptoService = CryptoService.create();

export type CryptoServiceInstance = CryptoService;

