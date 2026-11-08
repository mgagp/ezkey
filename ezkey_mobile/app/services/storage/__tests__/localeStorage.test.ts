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
  pickSupportedLocale,
} from '../localeStorage';

const mockAsyncStorage = jest.mocked(AsyncStorage);

describe('localeStorage', () => {
  beforeEach(() => {
    jest.clearAllMocks();
    mockAsyncStorage.getItem.mockResolvedValue(null);
    mockAsyncStorage.setItem.mockResolvedValue();
    mockAsyncStorage.removeItem.mockResolvedValue();
  });

  it('returns null when no preference exists', async () => {
    await expect(localeStorage.getPreference()).resolves.toBeNull();
  });

  it('ignores unsupported stored values', async () => {
    mockAsyncStorage.getItem.mockResolvedValue('es');

    await expect(localeStorage.getPreference()).resolves.toBeNull();
  });

  it('accepts the system default preference', async () => {
    mockAsyncStorage.getItem.mockResolvedValue('system');

    await expect(localeStorage.getPreference()).resolves.toBe('system');
  });

  it('persists and clears the preference', async () => {
    await localeStorage.setPreference('fr');
    await localeStorage.clearPreference();

    expect(mockAsyncStorage.setItem).toHaveBeenCalledWith(
      'ezkey-mobile/preferences/language',
      'fr',
    );
    expect(mockAsyncStorage.removeItem).toHaveBeenCalledWith('ezkey-mobile/preferences/language');
  });

  it('normalizes missing or invalid locale values to English', () => {
    expect(normalizeLocale()).toBe(DEFAULT_LOCALE);
    expect(normalizeLocale('de')).toBe(DEFAULT_LOCALE);
    expect(normalizeLocale('fr')).toBe('fr');
  });

  it('picks the first supported language from a device list', () => {
    expect(pickSupportedLocale(['de-DE', 'fr-CA', 'en-US'])).toBe('fr');
    expect(pickSupportedLocale(['en_GB'])).toBe('en');
    expect(pickSupportedLocale(['es-ES'])).toBe(DEFAULT_LOCALE);
    expect(pickSupportedLocale([])).toBe(DEFAULT_LOCALE);
  });
});
