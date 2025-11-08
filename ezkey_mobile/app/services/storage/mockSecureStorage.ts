const secureStore = new Map<string, string>();

export const mockSecureStorage = {
  async setItem(key: string, value: string) {
    secureStore.set(key, value);
  },
  async getItem(key: string): Promise<string | undefined> {
    return secureStore.get(key);
  },
  async removeItem(key: string) {
    secureStore.delete(key);
  },
  async clearAll() {
    secureStore.clear();
  },
};

export type MockSecureStorage = typeof mockSecureStorage;

