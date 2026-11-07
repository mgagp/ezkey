/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import i18n from 'i18next';
import { useState } from 'react';
import { I18nextProvider, initReactI18next } from 'react-i18next';
import { afterEach, describe, expect, it } from 'vitest';
import {
  frozenIssuanceKindWhileOpen,
  recoveryCodesIssuanceKind,
  type RecoveryCodesIssuanceKind,
} from '@/lib/admin-recovery-codes-issuance';
import enAdmins from '@/locales/en/admins.json';
import frAdmins from '@/locales/fr/admins.json';
import { AdminRecoveryCodesDialog } from './admin-recovery-codes-dialog';

const ENGLISH_SERVER_REPLACEMENT_MESSAGE =
  'New recovery codes issued. Every code from the previous set had been used.';
const PLACEHOLDER_CODE = 'placeholder';

const FR_REPLACEMENT_TITLE = 'Émettre de nouveaux codes de récupération — ops.admin';
const FR_REPLACEMENT_INTRO =
  'Tous les codes de récupération émis précédemment pour cet administrateur ont été utilisés. Générez un nouveau lot pour les remplacer. Les codes seront affichés une seule fois et ne pourront plus être affichés après la fermeture de cette boîte.';
const FR_REPLACEMENT_CONFIRM = 'Émettre de nouveaux codes de récupération';
const FR_REPLACEMENT_SUCCESS = 'Nouveaux codes de récupération émis avec succès.';
const FR_INITIAL_TITLE = 'Émettre les premiers codes de récupération — ops.admin';
const FR_INITIAL_SUCCESS = 'Premiers codes de récupération émis avec succès.';
const EN_REPLACEMENT_TITLE = 'Issue new recovery codes — ops.admin';
const EN_REPLACEMENT_SUCCESS = 'New recovery codes issued successfully.';

function dialogTitle(): string | null {
  const labelledBy = screen.getByRole('dialog').getAttribute('aria-labelledby');
  return labelledBy != null ? document.getElementById(labelledBy)?.textContent ?? null : null;
}

function i18nFor(lng: 'en' | 'fr') {
  const instance = i18n.createInstance();
  void instance.use(initReactI18next).init({
    lng,
    fallbackLng: 'en',
    ns: ['admins'],
    defaultNS: 'admins',
    resources: {
      en: { admins: enAdmins },
      fr: { admins: frAdmins },
    },
    interpolation: { escapeValue: false },
  });
  return instance;
}

function FrozenIssuanceDialogHarness({
  locale,
  kindAtOpen,
  serverMessage = ENGLISH_SERVER_REPLACEMENT_MESSAGE,
}: {
  locale: 'en' | 'fr';
  kindAtOpen: RecoveryCodesIssuanceKind;
  serverMessage?: string;
}) {
  const [liveExhausted, setLiveExhausted] = useState(kindAtOpen === 'replacement');
  const [result, setResult] = useState<{ recoveryCodes: string[]; message: string } | null>(null);
  const issuanceKind = frozenIssuanceKindWhileOpen(true, kindAtOpen, {
    recoveryCodesExhausted: liveExhausted,
  });

  return (
    <I18nextProvider i18n={i18nFor(locale)}>
      <AdminRecoveryCodesDialog
        open
        onClose={() => undefined}
        mode="issue-initial"
        issuanceKind={issuanceKind}
        username="ops.admin"
        result={result}
        copied={false}
        savedConfirmed={false}
        onCopyAll={() => undefined}
        onSavedConfirmedChange={() => undefined}
        onConfirm={() => {
          setResult({ recoveryCodes: [PLACEHOLDER_CODE], message: serverMessage });
          setLiveExhausted(false);
        }}
        pending={false}
        errorMessage={null}
      />
    </I18nextProvider>
  );
}

afterEach(() => {
  cleanup();
});

describe('frozenIssuanceKindWhileOpen', () => {
  it('keeps replacement copy after a refetch clears recoveryCodesExhausted', () => {
    expect(
      frozenIssuanceKindWhileOpen(true, 'replacement', { recoveryCodesExhausted: false }),
    ).toBe('replacement');
  });

  it('follows live admin state once the dialog is closed', () => {
    expect(
      frozenIssuanceKindWhileOpen(false, 'replacement', { recoveryCodesExhausted: false }),
    ).toBe('initial');
  });
});

describe('AdminRecoveryCodesDialog localized success', () => {
  it('shows FR replacement success instead of the English server message after issuance and refetch', () => {
    expect(recoveryCodesIssuanceKind({ recoveryCodesExhausted: true })).toBe('replacement');

    render(<FrozenIssuanceDialogHarness locale="fr" kindAtOpen="replacement" />);

    expect(dialogTitle()).toBe(FR_REPLACEMENT_TITLE);
    expect(screen.getByRole('alert').textContent).toContain(FR_REPLACEMENT_INTRO);
    expect(screen.getByRole('button', { name: FR_REPLACEMENT_CONFIRM })).toBeTruthy();

    fireEvent.click(screen.getByRole('button', { name: FR_REPLACEMENT_CONFIRM }));

    expect(dialogTitle()).toBe(FR_REPLACEMENT_TITLE);
    const success = screen.getByTestId('recovery-codes-issuance-success');
    expect(success.textContent).toContain(FR_REPLACEMENT_SUCCESS);
    expect(success.textContent).not.toContain(ENGLISH_SERVER_REPLACEMENT_MESSAGE);
    expect(success.textContent).not.toContain(FR_INITIAL_SUCCESS);
    expect(screen.queryByText(ENGLISH_SERVER_REPLACEMENT_MESSAGE)).toBeNull();
  });

  it('keeps first-issuance FR success when no set was ever issued', () => {
    render(<FrozenIssuanceDialogHarness locale="fr" kindAtOpen="initial" />);

    expect(dialogTitle()).toBe(FR_INITIAL_TITLE);
    fireEvent.click(screen.getByRole('button', { name: 'Émettre les premiers codes de récupération' }));

    expect(dialogTitle()).toBe(FR_INITIAL_TITLE);
    expect(screen.getByTestId('recovery-codes-issuance-success').textContent).toContain(
      FR_INITIAL_SUCCESS,
    );
    expect(screen.getByTestId('recovery-codes-issuance-success').textContent).not.toContain(
      FR_REPLACEMENT_SUCCESS,
    );
  });

  it('shows EN replacement success from locale, not the server summary', () => {
    render(<FrozenIssuanceDialogHarness locale="en" kindAtOpen="replacement" />);

    fireEvent.click(screen.getByRole('button', { name: 'Issue new recovery codes' }));

    expect(dialogTitle()).toBe(EN_REPLACEMENT_TITLE);
    const success = screen.getByTestId('recovery-codes-issuance-success');
    expect(success.textContent).toContain(EN_REPLACEMENT_SUCCESS);
    expect(success.textContent).not.toContain(ENGLISH_SERVER_REPLACEMENT_MESSAGE);
  });
});
