const BASE64_ALPHABET =
  'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/=';

const keyStore = new Map<
  string,
  {
    publicKey: string;
    privateKey: string;
  }
>();

const randomBase64 = (length: number) => {
  let output = '';
  for (let index = 0; index < length; index += 1) {
    const charIndex = Math.floor(Math.random() * BASE64_ALPHABET.length);
    output += BASE64_ALPHABET[charIndex];
  }
  return output;
};

const generatePlaceholderKeyPair = () => {
  const publicKey = randomBase64(344);
  const privateKey = randomBase64(344);
  return {publicKey, privateKey};
};

export const mockCrypto = {
  async generateKeyPair(alias: string): Promise<boolean> {
    const pair = generatePlaceholderKeyPair();
    keyStore.set(alias, pair);
    return true;
  },
  async getPublicKey(alias: string): Promise<string> {
    const entry = keyStore.get(alias);
    if (!entry) {
      throw new Error(`No key pair found for alias ${alias}`);
    }
    return entry.publicKey;
  },
  async sign(alias: string, payloadBase64: string): Promise<string> {
    const entry = keyStore.get(alias);
    if (!entry) {
      throw new Error(`No key pair found for alias ${alias}`);
    }
    return randomBase64(64) + payloadBase64.slice(0, 8);
  },
  async deleteKey(alias: string): Promise<boolean> {
    return keyStore.delete(alias);
  },
};

export type MockCrypto = typeof mockCrypto;

