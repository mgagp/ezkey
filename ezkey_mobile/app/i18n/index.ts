import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import {
  DEFAULT_LOCALE,
  LanguagePreference,
  localeStorage,
  pickSupportedLocale,
  SupportedLocale,
} from '../services/storage/localeStorage';
import {
  readDeviceLanguages,
  readSystemAppLanguage,
  supportsSystemAppLanguage,
  writeSystemAppLanguage,
} from '../services/locale/appLanguage';
import {setFormattingDeviceLocales} from '../utils/formatDateTime';
import {resources} from './resources';

let initialized = false;

/**
 * Current language setting. On iOS and Android 13+ the system per-app language wins, so a change
 * made in system settings shows up here; an older in-app choice is pushed to the system once, then the
 * in-app store is cleared. Elsewhere the in-app store is used. Unset means follow the phone.
 *
 * Fail-open: a native failure falls back to the in-app store.
 *
 * @returns the language preference
 */
export const getLanguagePreference = async (): Promise<LanguagePreference> => {
  const stored = await localeStorage.getPreference();
  if (!supportsSystemAppLanguage()) {
    return stored ?? 'system';
  }

  try {
    const system = await readSystemAppLanguage();
    if (system != null) {
      return pickSupportedLocale([system]);
    }
    if (stored != null && stored !== 'system') {
      await writeSystemAppLanguage(stored);
    }
    if (stored != null) {
      await localeStorage.clearPreference();
    }
    return stored ?? 'system';
  } catch (error) {
    console.warn('[i18n] Failed to read the system app language:', error);
    return stored ?? 'system';
  }
};

/**
 * Locale to render for a preference: `system` follows the phone when it is English or French.
 *
 * @param preference language setting
 * @returns the locale to render
 */
export const resolveLocale = async (preference: LanguagePreference): Promise<SupportedLocale> => {
  const deviceLanguages = await readDeviceLanguages();
  // Dates keep the phone's regional variant when it matches the app language (fr-CA → "15 h 34").
  setFormattingDeviceLocales(deviceLanguages);
  return preference === 'system' ? pickSupportedLocale(deviceLanguages) : preference;
};

export const initializeI18n = async () => {
  const locale = await resolveLocale(await getLanguagePreference());

  if (!initialized) {
    await i18n.use(initReactI18next).init({
      compatibilityJSON: 'v4',
      lng: locale,
      fallbackLng: DEFAULT_LOCALE,
      defaultNS: 'translation',
      interpolation: {
        escapeValue: false,
      },
      resources,
    });
    initialized = true;
    return locale;
  }

  if (i18n.language !== locale) {
    await i18n.changeLanguage(locale);
  }

  return locale;
};

/**
 * Re-applies the language setting, for example after the user changed it in system settings
 * while the app was in the background.
 *
 * @returns the rendered locale
 */
export const syncAppLanguage = async (): Promise<SupportedLocale> => {
  const locale = await resolveLocale(await getLanguagePreference());
  if (i18n.isInitialized && i18n.language !== locale) {
    await i18n.changeLanguage(locale);
  }
  return locale;
};

/**
 * Saves and applies a language chosen in the app. On iOS and Android 13+ it is written to the
 * system per-app language, so system settings show the same choice.
 *
 * @param preference `system`, `en` or `fr`
 * @returns the rendered locale
 */
export const changeAppLanguage = async (
  preference: LanguagePreference,
): Promise<SupportedLocale> => {
  let savedToSystem = false;
  if (supportsSystemAppLanguage()) {
    try {
      await writeSystemAppLanguage(preference);
      savedToSystem = true;
    } catch (error) {
      console.warn('[i18n] Failed to set the system app language:', error);
    }
  }
  if (!savedToSystem) {
    await localeStorage.setPreference(preference);
  }

  const nextLocale = await resolveLocale(preference);
  if (i18n.isInitialized && i18n.language !== nextLocale) {
    await i18n.changeLanguage(nextLocale);
  }

  return nextLocale;
};

export {i18n};
export type {LanguagePreference, SupportedLocale};
