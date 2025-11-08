import * as Keychain from 'react-native-keychain';

const SERVICE_PREFIX = 'com.ezkeymobile.secure';

const serviceFor = (key: string) => `${SERVICE_PREFIX}.${key}`;

export const secureStorage = {
  async setItem(key: string, value: string) {
    await Keychain.setGenericPassword(key, value, {
      service: serviceFor(key),
      accessible: Keychain.ACCESSIBLE.AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY,
    });
  },

  async getItem(key: string): Promise<string | undefined> {
    const record = await Keychain.getGenericPassword({service: serviceFor(key)});
    if (!record) {
      return undefined;
    }
    return record.password;
  },

  async removeItem(key: string) {
    await Keychain.resetGenericPassword({service: serviceFor(key)});
  },
};

export type SecureStorage = typeof secureStorage;
