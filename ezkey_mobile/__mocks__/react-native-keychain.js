/**
 * Manual Jest mock for react-native-keychain 11.
 *
 * v11 imports TurboModuleRegistry.getEnforcing('RNKeychainManager') at module load.
 * Jest has no native TurboModule, so every suite must resolve this mock instead of
 * the real package. Per-test jest.mock factories still override this module.
 */
const ACCESSIBLE = {
  AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY: 'AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY',
};

const setGenericPassword = jest.fn().mockResolvedValue({
  service: 'mock',
  storage: 'keychain',
});
const getGenericPassword = jest.fn().mockResolvedValue(false);
const resetGenericPassword = jest.fn().mockResolvedValue(true);
const getAllGenericPasswordServices = jest.fn().mockResolvedValue([]);

const keychain = {
  ACCESSIBLE,
  setGenericPassword,
  getGenericPassword,
  resetGenericPassword,
  getAllGenericPasswordServices,
};

module.exports = {
  __esModule: true,
  ...keychain,
  default: keychain,
};
