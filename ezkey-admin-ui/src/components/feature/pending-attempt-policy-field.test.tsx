/** @vitest-environment jsdom */

import { cleanup, render, screen } from '@testing-library/react';
import i18n from 'i18next';
import { I18nextProvider, initReactI18next } from 'react-i18next';
import { afterEach, describe, expect, it } from 'vitest';
import enIntegrations from '@/locales/en/integrations.json';
import frIntegrations from '@/locales/fr/integrations.json';
import { PendingAttemptPolicyField } from './pending-attempt-policy-field';

const EN_HELP =
  'Refuse is recommended for interactive integrations, where a person is waiting to sign in. Saving this does not change a request that is already waiting. The next request uses this setting: Replace ends the waiting one; Refuse keeps it and rejects the new request.';
const FR_HELP =
  "Refuser est recommandé pour les intégrations interactives, où une personne attend pour se connecter. Enregistrer ce réglage ne change pas une demande déjà en attente. La prochaine demande l'applique : Remplacer met fin à celle qui attend ; Refuser la garde et rejette la nouvelle.";

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
    expect(screen.getByText('When a request is already waiting')).toBeTruthy();
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
    expect(screen.getByText('Quand une demande attend déjà')).toBeTruthy();
    expect((screen.getByRole('combobox') as HTMLSelectElement).value).toBe('REFUSE');
  });
});
