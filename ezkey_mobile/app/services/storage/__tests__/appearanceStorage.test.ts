jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
  },
}));

import AsyncStorage from '@react-native-async-storage/async-storage';
import {appearanceStorage, DEFAULT_APPEARANCE} from '../appearanceStorage';

const mockAsyncStorage = jest.mocked(AsyncStorage);

describe('appearanceStorage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAsyncStorage.getItem.mockResolvedValue(null);
    mockAsyncStorage.setItem.mockResolvedValue();
  });

  it('follows the system by default when no preference exists', async () => {
    expect(DEFAULT_APPEARANCE).toBe('system');
    await expect(appearanceStorage.getAppearance()).resolves.toBe('system');
  });

  it('falls back to the system for unknown stored values', async () => {
    mockAsyncStorage.getItem.mockResolvedValue('sepia');

    await expect(appearanceStorage.getAppearance()).resolves.toBe('system');
  });

  it('falls back to the system when storage cannot be read', async () => {
    mockAsyncStorage.getItem.mockRejectedValue(new Error('io'));
    jest.spyOn(console, 'warn').mockImplementation(jest.fn());

    await expect(appearanceStorage.getAppearance()).resolves.toBe('system');
  });

  it('persists the chosen preference', async () => {
    await appearanceStorage.setAppearance('light');

    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/preferences/appearance',
      'light',
    );
  });
});
