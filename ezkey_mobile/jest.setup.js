/**
 * Jest setup for React Native (see App.test.tsx and AsyncStorage Jest integration).
 */
import {webcrypto} from 'crypto';
import {NativeModules} from 'react-native';

/** Node/Jest: Web Crypto API for tests that still use `globalThis.crypto`. */
if (globalThis.crypto == null) {
  globalThis.crypto = webcrypto;
}

jest.mock('@react-native-async-storage/async-storage', () =>
  require('@react-native-async-storage/async-storage/jest/async-storage-mock'),
);

/** ESM in node_modules — mock so Jest does not parse the real module. */
jest.mock('react-native-config', () => ({
  __esModule: true,
  default: {},
}));

/**
 * App imports cryptoService at load time; without a native module, CryptoService.create() throws.
 * Stub EzkeyCryptoModule so the tree can mount in Jest (no hardware keystore in Node).
 */
NativeModules.EzkeyCryptoModule = {
  generateEnrollmentKeyPair: jest.fn().mockResolvedValue(true),
  getPublicKey: jest.fn().mockResolvedValue(
    'MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
  ),
  sign: jest.fn().mockResolvedValue('dGVzdA=='),
  verify: jest.fn().mockResolvedValue(true),
  deleteKeyPair: jest.fn().mockResolvedValue(true),
  getBuildTimestamp: jest.fn().mockResolvedValue('2025-01-01T00:00:00Z'),
  generateProofToken: jest
    .fn()
    .mockResolvedValue(
      'AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8.ICEiIyQlJicoKSorLC0uLw',
    ),
  getEnrollmentPrivateKeyStorageTier: jest.fn().mockResolvedValue('STANDARD'),
};

jest.mock('react-native-vision-camera', () => ({
  Camera: 'Camera',
  VisionCameraProxy: {initFrameProcessorPlugin: jest.fn()},
  useCameraDevice: jest.fn(() => undefined),
  useFrameProcessor: jest.fn(() => () => {}),
  useCodeScanner: jest.fn(() => ({})),
  useCameraPermission: jest.fn(() => ({
    hasPermission: true,
    requestPermission: jest.fn().mockResolvedValue(true),
  })),
}));

jest.mock('react-native-worklets-core', () => ({
  useRunOnJS: fn => fn,
  useSharedValue: v => ({value: v}),
}));

jest.mock('react-native-gesture-handler', () => {
  const {View} = require('react-native');
  return {
    GestureHandlerRootView: View,
    PanGestureHandler: View,
    TapGestureHandler: View,
    State: {},
  };
});
