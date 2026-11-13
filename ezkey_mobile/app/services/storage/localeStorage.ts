import AsyncStorage from '@react-native-async-storage/async-storage';

export const DEFAULT_LOCALE = 'en';

export const SUPPORTED_LOCALES = ['en', 'fr'] as const;

export type SupportedLocale = (typeof SUPPORTED_LOCALES)[number];

/** Language setting: an explicit locale, or follow the phone (#1157). */
export type LanguagePreference = SupportedLocale | 'system';

const LOCALE_PREFERENCE_KEY = 'ezkey-mobile/preferences/language';

const isSupportedLocale = (value: string): value is SupportedLocale =>
  SUPPORTED_LOCALES.includes(value as SupportedLocale);

const isLanguagePreference = (value: string): value is LanguagePreference =>
  value === 'system' || isSupportedLocale(value);

/**
 * In-app language store. On iOS and Android 13+ the system per-app language is the source of truth and
 * this store is only read once to migrate an older in-app choice (see `app/i18n`).
 */
class LocaleStorage {
  async getPreference(): Promise<LanguagePreference | null> {
    try {
      const value = await AsyncStorage.getItem(LOCALE_PREFERENCE_KEY);
      if (value && isLanguagePreference(value)) {
        return value;
      }
    } catch (error) {
      console.warn('[localeStorage] Failed to read locale preference:', error);
    }

    return null;
  }

  async setPreference(preference: LanguagePreference) {
    try {
      await AsyncStorage.setItem(LOCALE_PREFERENCE_KEY, preference);
    } catch (error) {
      console.warn('[localeStorage] Failed to persist locale preference:', error);
    }
  }

  async clearPreference() {
    try {
      await AsyncStorage.removeItem(LOCALE_PREFERENCE_KEY);
    } catch (error) {
      console.warn('[localeStorage] Failed to clear locale preference:', error);
    }
  }
}

export const localeStorage = new LocaleStorage();

export const normalizeLocale = (value?: string): SupportedLocale =>
  value && isSupportedLocale(value) ? value : DEFAULT_LOCALE;

/**
 * First supported locale in a BCP 47 list (`fr-CA` counts as `fr`), else the default.
 *
 * @param tags language tags in preference order
 * @returns a supported locale
 */
export const pickSupportedLocale = (tags: readonly string[]): SupportedLocale => {
  for (const tag of tags) {
    const primary = tag.split(/[-_]/)[0]?.toLowerCase() ?? '';
    if (isSupportedLocale(primary)) {
      return primary;
    }
  }
  return DEFAULT_LOCALE;
};
