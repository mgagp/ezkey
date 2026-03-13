import i18n from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import { initReactI18next } from 'react-i18next';

import enApiKeys from '@/locales/en/api-keys.json';
import enAuditLogs from '@/locales/en/audit-logs.json';
import enEncryptionKeys from '@/locales/en/encryption-keys.json';
import enAuthAttempts from '@/locales/en/auth-attempts.json';
import enCommon from '@/locales/en/common.json';
import enDashboard from '@/locales/en/dashboard.json';
import enEnrollments from '@/locales/en/enrollments.json';
import enIntegrations from '@/locales/en/integrations.json';
import enLayout from '@/locales/en/layout.json';
import enLogin from '@/locales/en/login.json';
import enTenants from '@/locales/en/tenants.json';
import frApiKeys from '@/locales/fr/api-keys.json';
import frAuditLogs from '@/locales/fr/audit-logs.json';
import frEncryptionKeys from '@/locales/fr/encryption-keys.json';
import frAuthAttempts from '@/locales/fr/auth-attempts.json';
import frCommon from '@/locales/fr/common.json';
import frDashboard from '@/locales/fr/dashboard.json';
import frEnrollments from '@/locales/fr/enrollments.json';
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
        'api-keys': enApiKeys as Record<string, unknown>,
        'audit-logs': enAuditLogs as Record<string, unknown>,
        'encryption-keys': enEncryptionKeys as Record<string, unknown>,
        'auth-attempts': enAuthAttempts as Record<string, unknown>,
        common: enCommon as Record<string, unknown>,
        dashboard: enDashboard as Record<string, unknown>,
        enrollments: enEnrollments as Record<string, unknown>,
        integrations: enIntegrations as Record<string, unknown>,
        layout: enLayout as Record<string, unknown>,
        login: enLogin as Record<string, unknown>,
        tenants: enTenants as Record<string, unknown>,
      },
      fr: {
        'api-keys': frApiKeys as Record<string, unknown>,
        'audit-logs': frAuditLogs as Record<string, unknown>,
        'encryption-keys': frEncryptionKeys as Record<string, unknown>,
        'auth-attempts': frAuthAttempts as Record<string, unknown>,
        common: frCommon as Record<string, unknown>,
        dashboard: frDashboard as Record<string, unknown>,
        enrollments: frEnrollments as Record<string, unknown>,
        integrations: frIntegrations as Record<string, unknown>,
        layout: frLayout as Record<string, unknown>,
        login: frLogin as Record<string, unknown>,
        tenants: frTenants as Record<string, unknown>,
      },
    },
    fallbackLng: 'en',
    defaultNS: 'common',
    ns: ['api-keys', 'audit-logs', 'auth-attempts', 'common', 'encryption-keys', 'dashboard', 'enrollments', 'integrations', 'layout', 'login', 'tenants'],
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
