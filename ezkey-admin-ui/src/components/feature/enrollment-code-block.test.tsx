/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import i18n from 'i18next';
import { I18nextProvider, initReactI18next } from 'react-i18next';
import { afterEach, describe, expect, it, vi } from 'vitest';
import enCommon from '@/locales/en/common.json';
import frCommon from '@/locales/fr/common.json';
import { EnrollmentCodeBlock } from './enrollment-code-block';

const CODE = '{"enrollmentProofToken":"tok-1","authUrl":"https://auth.example.test"}';

function renderBlock(code: string | null, lng: 'en' | 'fr' = 'en') {
  const instance = i18n.createInstance();
  void instance.use(initReactI18next).init({
    lng,
    resources: { en: { common: enCommon }, fr: { common: frCommon } },
  });
  return render(
    <I18nextProvider i18n={instance}>
      <EnrollmentCodeBlock code={code} />
    </I18nextProvider>,
  );
}

describe('EnrollmentCodeBlock (#1494)', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('is masked by default and copies the exact server text', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    vi.stubGlobal('navigator', { clipboard: { writeText } });
    renderBlock(CODE);

    expect(screen.getByTestId('enrollment-code-value').textContent).not.toContain('tok-1');
    await act(async () => {
      fireEvent.click(screen.getByTestId('enrollment-code-copy'));
    });
    expect(writeText).toHaveBeenCalledWith(CODE);

    fireEvent.click(screen.getByTestId('enrollment-code-reveal'));
    expect(screen.getByTestId('enrollment-code-value').textContent).toBe(CODE);
  });

  it('says the binding challenge goes through another channel', () => {
    renderBlock(CODE);
    expect(screen.getByTestId('enrollment-code').textContent).toContain('another channel');
  });

  it('offers no copy and says why when there is no code', () => {
    renderBlock(null, 'fr');
    expect(screen.queryByTestId('enrollment-code-copy')).toBeNull();
    expect(screen.getByTestId('enrollment-code-unavailable').textContent).toContain(
      "adresse publique d'Auth API",
    );
  });
});
