import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { describe, expect, it } from 'vitest';
import { ApiError } from '@/lib/api-client';
import enLogin from '@/locales/en/login.json';
import frLogin from '@/locales/fr/login.json';
import { loginCooldownMessage } from './login-cooldown-message';

function i18nFor(lng: 'en' | 'fr') {
  const instance = i18n.createInstance();
  void instance.use(initReactI18next).init({
    lng,
    fallbackLng: 'en',
    defaultNS: 'login',
    resources: {
      en: { login: enLogin },
      fr: { login: frLogin },
    },
  });
  return instance;
}

function cooldownError(retryAfterSeconds: number | null): ApiError {
  return new ApiError(
    409,
    {},
    'Authentication attempt creation is temporarily suspended for this enrollment.',
    {
      type: 'https://ezkey.io/problems/domain/auth-attempt-creation-cooldown',
      detail: 'Authentication attempt creation is temporarily suspended for this enrollment.',
    },
    retryAfterSeconds,
  );
}

describe('loginCooldownMessage', () => {
  it('uses the French sentence and the Retry-After minutes, not a generic error', () => {
    const fr = i18nFor('fr');
    const message = loginCooldownMessage(cooldownError(299), fr.t.bind(fr));
    expect(message).toBe(
      'La connexion est suspendue encore 5 minutes après plusieurs demandes refusées ou codes erronés. Réessayez ensuite.',
    );
    expect(message).not.toContain('erreur inattendue');
  });

  it('uses the English sentence for a one-minute delay', () => {
    const en = i18nFor('en');
    expect(loginCooldownMessage(cooldownError(60), en.t.bind(en))).toBe(
      'Sign-in is paused for 1 more minute after several refused requests or wrong codes. Try again then.',
    );
  });

  it('stays explicit when Retry-After is missing', () => {
    const en = i18nFor('en');
    const message = loginCooldownMessage(cooldownError(null), en.t.bind(en));
    expect(message).toBe(
      'Sign-in is paused after several refused requests or wrong codes. Try again in a few minutes.',
    );
    expect(message).not.toBe('An unexpected error occurred.');
  });

  it('leaves other errors to the usual translation', () => {
    const en = i18nFor('en');
    expect(loginCooldownMessage(new Error('network'), en.t.bind(en))).toBeNull();
  });
});
