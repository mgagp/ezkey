/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react';
import i18n from 'i18next';
import { I18nextProvider, initReactI18next } from 'react-i18next';
import { afterEach, describe, expect, it } from 'vitest';
import enIntegrations from '@/locales/en/integrations.json';
import frIntegrations from '@/locales/fr/integrations.json';
import { PendingAttemptPolicyField } from './pending-attempt-policy-field';

const EN_HELP = 'Refuse is recommended for integrations triggered repeatedly or automatically.';
const FR_HELP =
  'Refuser est recommandé pour les intégrations déclenchées de façon répétée ou automatisée.';

function i18nFor(lng: 'en' | 'fr') {
  const instance = i18n.createInstance();
  void instance.use(initReactI18next).init({
    lng,
    fallbackLng: 'en',
    resources: {
      en: { integrations: enIntegrations },
      fr: { integrations: frIntegrations },
    },
  });
  return instance;
}

describe('PendingAttemptPolicyField', () => {
  afterEach(() => {
    cleanup();
  });

  it('shows the English help without connector names', () => {
    render(
      <I18nextProvider i18n={i18nFor('en')}>
        <PendingAttemptPolicyField id="policy" defaultValue="REPLACE" />
      </I18nextProvider>,
    );
    const help = screen.getByTestId('pending-attempt-policy-help').textContent ?? '';
    expect(help).toContain(EN_HELP);
    expect(help.toLowerCase()).not.toMatch(/pam|ssh|jenkins/);
    expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('REPLACE');
  });

  it('shows the French help without connector names', () => {
    render(
      <I18nextProvider i18n={i18nFor('fr')}>
        <PendingAttemptPolicyField id="policy" defaultValue="REFUSE" />
      </I18nextProvider>,
    );
    const help = screen.getByTestId('pending-attempt-policy-help').textContent ?? '';
    expect(help).toContain(FR_HELP);
    expect(help.toLowerCase()).not.toMatch(/pam|ssh|jenkins/);
    expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('REFUSE');
  });
});
