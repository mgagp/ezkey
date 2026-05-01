import i18n from 'i18next';
import {initReactI18next} from 'react-i18next';
import {
  DEFAULT_LOCALE,
  localeStorage,
  normalizeLocale,
  SupportedLocale,
} from '../services/storage/localeStorage';
import {resources} from './resources';

let initialized = false;

export const initializeI18n = async () => {
  const locale = await localeStorage.getLocale();

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

export const changeAppLanguage = async (locale: SupportedLocale) => {
  const nextLocale = normalizeLocale(locale);
  await localeStorage.setLocale(nextLocale);

  return nextLocale;
};

export {i18n};
export type {SupportedLocale};