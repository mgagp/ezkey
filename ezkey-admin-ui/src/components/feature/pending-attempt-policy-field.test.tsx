/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import i18n from 'i18next';
import { I18nextProvider, initReactI18next } from 'react-i18next';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '@/lib/api-client';
import enIntegrations from '@/locales/en/integrations.json';
import frIntegrations from '@/locales/fr/integrations.json';
import { PendingAttemptPolicyEditor } from './pending-attempt-policy-editor';
import { PendingAttemptPolicyField } from './pending-attempt-policy-field';
import { policySaveErrorMessage } from './pending-attempt-policy-save';

const EN_HELP =
  'Replace suits most applications: if the person starts signing in again, the new request replaces the waiting one. Refuse suits cases where several sessions can ask the same person to sign in at once, and a new request must not interrupt the one in progress. Saving this does not change a request that is already waiting: it applies from the next request.';
const FR_HELP =
  "Remplacer convient à la plupart des applications : si la personne relance sa connexion, la nouvelle demande remplace celle qui attend. Refuser convient quand plusieurs sessions peuvent demander une connexion à la même personne en même temps, et qu'une nouvelle demande ne doit pas interrompre celle en cours. Enregistrer ce réglage ne change pas une demande déjà en attente : il s'applique à partir de la prochaine.";

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
    const select = screen.getByRole('combobox') as HTMLSelectElement;
    expect(select.value).toBe('REPLACE');
    expect(select.getAttribute('aria-describedby')).toBe('policy-help');
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
    expect(screen.getByRole('combobox').getAttribute('aria-describedby')).toBe('policy-help');
  });
});

describe('policy save', () => {
  afterEach(() => {
    cleanup();
  });

  it('uses the lock sentence for a system-integration lifecycle 403 on policy save', () => {
    const fr = i18nFor('fr');
    const error = new ApiError(403, {}, 'fallback detail', {
      type: 'https://ezkey.io/problems/domain/system-integration-lifecycle',
      detail:
        'System integration pending-attempt policy cannot be changed because it is reserved for administrator MFA.',
    });
    const saveFailed = fr.t('pendingAttemptPolicy.saveFailed', { ns: 'integrations' });
    expect(policySaveErrorMessage(error, fr.t.bind(fr), saveFailed)).toBe(
      'Fixé à Remplacer pour la connexion des administrateurs : une nouvelle connexion remplace celle qui attend.',
    );
  });

  it('uses the French saveFailed sentence for a 500 instead of the English server detail', () => {
    const fr = i18nFor('fr');
    const error = new ApiError(500, {}, 'An unexpected error occurred', {
      type: 'https://ezkey.io/problems/admin/internal-error',
      detail: 'An unexpected error occurred',
    });
    const saveFailed = fr.t('pendingAttemptPolicy.saveFailed', { ns: 'integrations' });
    expect(policySaveErrorMessage(error, fr.t.bind(fr), saveFailed)).toBe(
      'Impossible de mettre à jour le réglage « demande en attente ». Réessayez.',
    );
  });

  it('keeps the chosen value, disables the select while saving, and reverts on failure', async () => {
    let rejectSave: (error: unknown) => void = () => {};
    const onSave = vi.fn(
      () =>
        new Promise<void>((_resolve, reject) => {
          rejectSave = reject;
        }),
    );
    render(
      <I18nextProvider i18n={i18nFor('fr')}>
        <PendingAttemptPolicyEditor
          id="policy"
          serverValue="REPLACE"
          onSave={onSave}
          onSaved={() => {}}
          onSaveError={() => {}}
        />
      </I18nextProvider>,
    );
    const select = screen.getByRole('combobox') as HTMLSelectElement;
    fireEvent.change(select, { target: { value: 'REFUSE' } });
    expect(select.value).toBe('REFUSE');
    expect(select.disabled).toBe(true);
    expect(onSave).toHaveBeenCalledTimes(1);
    fireEvent.change(select, { target: { value: 'REPLACE' } });
    expect(onSave).toHaveBeenCalledTimes(1);

    await act(async () => {
      rejectSave(new TypeError('Failed to fetch'));
    });

    expect(select.disabled).toBe(false);
    expect(select.value).toBe('REPLACE');
  });

  it('disables the select and shows the administrator sign-in hint', () => {
    const fr = i18nFor('fr');
    const hint = fr.t('pendingAttemptPolicy.systemLocked', { ns: 'integrations' });
    expect(hint).toBe(
      'Fixé à Remplacer pour la connexion des administrateurs : une nouvelle connexion remplace celle qui attend.',
    );
    const onSave = vi.fn();
    render(
      <I18nextProvider i18n={fr}>
        <PendingAttemptPolicyEditor
          id="policy"
          serverValue="REPLACE"
          disabled
          hint={hint}
          onSave={onSave}
          onSaved={() => {}}
          onSaveError={() => {}}
        />
      </I18nextProvider>,
    );
    const select = screen.getByRole('combobox') as HTMLSelectElement;
    expect(select.disabled).toBe(true);
    expect(select.value).toBe('REPLACE');
    expect(select.getAttribute('aria-describedby')).toBe('policy-lock');
    expect(screen.queryByTestId('pending-attempt-policy-help')).toBeNull();
    expect(screen.getByTestId('pending-attempt-policy-lock').textContent).toBe(hint);
    fireEvent.change(select, { target: { value: 'REFUSE' } });
    expect(onSave).not.toHaveBeenCalled();
    expect(select.value).toBe('REPLACE');
  });
});
