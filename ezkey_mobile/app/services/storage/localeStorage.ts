import AsyncStorage from '@react-native-async-storage/async-storage';

export const DEFAULT_LOCALE = 'en';

export const SUPPORTED_LOCALES = ['en', 'fr'] as const;

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

const LOCALE_PREFERENCE_KEY = 'ezkey-mobile/preferences/language';

const isSupportedLocale = (value: string): value is SupportedLocale =>
  SUPPORTED_LOCALES.includes(value as SupportedLocale);

class LocaleStorage {
  async getLocale(): Promise<SupportedLocale> {
    try {
      const value = await AsyncStorage.getItem(LOCALE_PREFERENCE_KEY);
      if (value && isSupportedLocale(value)) {
        return value;
      }
    } catch (error) {
      console.warn('[localeStorage] Failed to read locale preference:', error);
    }

    return DEFAULT_LOCALE;
  }

  async setLocale(locale: SupportedLocale) {
    try {
      await AsyncStorage.setItem(LOCALE_PREFERENCE_KEY, locale);
    } catch (error) {
      console.warn('[localeStorage] Failed to persist locale preference:', error);
    }
  }
}

export const localeStorage = new LocaleStorage();

export const normalizeLocale = (value?: string): SupportedLocale =>
  value && isSupportedLocale(value) ? value : DEFAULT_LOCALE;