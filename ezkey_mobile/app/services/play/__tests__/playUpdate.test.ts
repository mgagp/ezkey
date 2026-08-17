import {NativeModules, Platform} from 'react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  checkFlexiblePlayUpdate,
  getDismissedPlayUpdateVersionCode,
  setDismissedPlayUpdateVersionCode,
  startFlexiblePlayUpdate,
} from '../playUpdate';

describe('playUpdate', () => {
  const originalOs = Platform.OS;

  afterEach(() => {
    Object.defineProperty(Platform, 'OS', {
      configurable: true,
      writable: true,
      value: originalOs,
    });
    (NativeModules.EzkeyPlayUpdateModule.checkFlexibleUpdate as jest.Mock).mockResolvedValue({
      available: false,
    });
    (NativeModules.EzkeyPlayUpdateModule.startFlexibleUpdate as jest.Mock).mockResolvedValue(false);
  });

  it('returns unavailable when Platform.OS is not android', async () => {
    Object.defineProperty(Platform, 'OS', {configurable: true, writable: true, value: 'ios'});
    await expect(checkFlexiblePlayUpdate()).resolves.toEqual({available: false});
    await expect(startFlexiblePlayUpdate()).resolves.toBe(false);
  });

  it('returns unavailable when the native check throws', async () => {
    Object.defineProperty(Platform, 'OS', {configurable: true, writable: true, value: 'android'});
    (NativeModules.EzkeyPlayUpdateModule.checkFlexibleUpdate as jest.Mock).mockRejectedValue(
      new Error('play missing'),
    );
    await expect(checkFlexiblePlayUpdate()).resolves.toEqual({available: false});
  });

  it('passes through an available Play flexible update', async () => {
    Object.defineProperty(Platform, 'OS', {configurable: true, writable: true, value: 'android'});
    (NativeModules.EzkeyPlayUpdateModule.checkFlexibleUpdate as jest.Mock).mockResolvedValue({
      available: true,
      availableVersionCode: 3,
    });
    await expect(checkFlexiblePlayUpdate()).resolves.toEqual({
      available: true,
      availableVersionCode: 3,
    });
  });

  it('remembers a dismissed Play versionCode', async () => {
    await setDismissedPlayUpdateVersionCode(3);
    await expect(getDismissedPlayUpdateVersionCode()).resolves.toBe(3);
    await AsyncStorage.removeItem('ezkey.playUpdate.dismissedVersionCode');
  });
});
