import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import enCommon from '@/locales/en/common.json';
import enDashboard from '@/locales/en/dashboard.json';
import enIntegrations from '@/locales/en/integrations.json';
import enLayout from '@/locales/en/layout.json';
import enLogin from '@/locales/en/login.json';
import enTenants from '@/locales/en/tenants.json';
import frCommon from '@/locales/fr/common.json';
import frDashboard from '@/locales/fr/dashboard.json';
import frIntegrations from '@/locales/fr/integrations.json';
import frLayout from '@/locales/fr/layout.json';
import frLogin from '@/locales/fr/login.json';
import frTenants from '@/locales/fr/tenants.json';

const STORAGE_KEY = 'ezkey-admin-ui-lang';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: {
        common: enCommon as Record<string, unknown>,
        dashboard: enDashboard as Record<string, unknown>,
        integrations: enIntegrations as Record<string, unknown>,
        layout: enLayout as Record<string, unknown>,
        login: enLogin as Record<string, unknown>,
        tenants: enTenants as Record<string, unknown>,
      },
      fr: {
        common: frCommon as Record<string, unknown>,
        dashboard: frDashboard as Record<string, unknown>,
        integrations: frIntegrations as Record<string, unknown>,
        layout: frLayout as Record<string, unknown>,
        login: frLogin as Record<string, unknown>,
        tenants: frTenants as Record<string, unknown>,
      },
    },
    fallbackLng: 'en',
    defaultNS: 'common',
    ns: ['common', 'dashboard', 'integrations', 'layout', 'login', 'tenants'],
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
