jest.mock('@react-native-async-storage/async-storage', () => ({
  __esModule: true,
  default: {
    getItem: jest.fn(),
    setItem: jest.fn(),
    removeItem: jest.fn(),
  },
}));

import AsyncStorage from '@react-native-async-storage/async-storage';
import {
  DEFAULT_LOCALE,
  localeStorage,
  normalizeLocale,
} from '../localeStorage';

const mockAsyncStorage = jest.mocked(AsyncStorage);

describe('localeStorage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAsyncStorage.getItem.mockResolvedValue(null);
    mockAsyncStorage.setItem.mockResolvedValue();
    mockAsyncStorage.removeItem.mockResolvedValue();
  });

  it('returns English by default when no preference exists', async () => {
    await expect(localeStorage.getLocale()).resolves.toBe(DEFAULT_LOCALE);
  });

  it('returns English when storage contains an unsupported locale', async () => {
    mockAsyncStorage.getItem.mockResolvedValue('es');

    await expect(localeStorage.getLocale()).resolves.toBe(DEFAULT_LOCALE);
  });

  it('persists supported locale values', async () => {
    await localeStorage.setLocale('fr');

    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/preferences/language',
      'fr',
    );
  });

  it('normalizes missing or invalid locale values to English', () => {
    expect(normalizeLocale()).toBe(DEFAULT_LOCALE);
    expect(normalizeLocale('de')).toBe(DEFAULT_LOCALE);
    expect(normalizeLocale('fr')).toBe('fr');
  });
});