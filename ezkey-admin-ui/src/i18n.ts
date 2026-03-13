import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import enCommon from '@/locales/en/common.json';
import enLayout from '@/locales/en/layout.json';
import enLogin from '@/locales/en/login.json';
import frCommon from '@/locales/fr/common.json';
import frLayout from '@/locales/fr/layout.json';
import frLogin from '@/locales/fr/login.json';

const STORAGE_KEY = 'ezkey-admin-ui-lang';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: {
        common: enCommon as Record<string, unknown>,
        layout: enLayout as Record<string, unknown>,
        login: enLogin as Record<string, unknown>,
      },
      fr: {
        common: frCommon as Record<string, unknown>,
        layout: frLayout as Record<string, unknown>,
        login: frLogin as Record<string, unknown>,
      },
    },
    fallbackLng: 'en',
    defaultNS: 'common',
    ns: ['common', 'layout', 'login'],
    interpolation: {
      escapeValue: false,
    },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: STORAGE_KEY,
      caches: ['localStorage'],
    },
  });

export const I18N_STORAGE_KEY = STORAGE_KEY;
