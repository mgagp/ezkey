/** @vitest-environment jsdom */

import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import i18n from 'i18next';
import { I18nextProvider, initReactI18next } from 'react-i18next';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import enAdmins from '@/locales/en/admins.json';
import enEnrollments from '@/locales/en/enrollments.json';
import { AdminBindCard } from './admin-bind-card';

const mocks = vi.hoisted(() => ({
  useGetAdminOnboarding: vi.fn(),
  fetchBlobUrl: vi.fn(),
}));

vi.mock('@/generated/admin-api/administrator-provisioning/administrator-provisioning', () => ({
  useGetAdminOnboarding: mocks.useGetAdminOnboarding,
}));

vi.mock('@/lib/api-client', () => ({
  fetchBlobUrl: mocks.fetchBlobUrl,
}));

function i18nEn() {
  const instance = i18n.createInstance();
  void instance.use(initReactI18next).init({
    lng: 'en',
    resources: { en: { admins: enAdmins, enrollments: enEnrollments } },
  });
  return instance;
}

function renderCard(enrollmentStatus: 'CREATED' | 'BOUND') {
  return render(
    <I18nextProvider i18n={i18nEn()}>
      <AdminBindCard adminId={7} enrollmentStatus={enrollmentStatus} />
    </I18nextProvider>,
  );
}

/** Behaves like the Orval hook: returns data only while the query is enabled. */
function onboardingReturns(data: { enrollmentProofToken: string | null; enrollmentChallenge: number | null }) {
  mocks.useGetAdminOnboarding.mockImplementation(
    (_id: number, options: { query: { enabled: boolean } }) => ({
      data: options.query.enabled ? data : undefined,
      isFetching: false,
      isError: false,
    }),
  );
}

function enabledFlags(): boolean[] {
  return mocks.useGetAdminOnboarding.mock.calls.map(
    (call) => (call[1] as { query: { enabled: boolean } }).query.enabled,
  );
}

describe('AdminBindCard (#1313)', () => {
  beforeEach(() => {
    mocks.useGetAdminOnboarding.mockReset();
    mocks.fetchBlobUrl.mockReset();
    mocks.fetchBlobUrl.mockResolvedValue('blob:qr');
    vi.stubGlobal('URL', { ...URL, revokeObjectURL: vi.fn() });
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it('loads nothing secret until the operator clicks Show QR', async () => {
    onboardingReturns({ enrollmentProofToken: 'token-abc', enrollmentChallenge: 123456 });
    renderCard('CREATED');

    expect(screen.getByTestId('admin-bind-in-person').textContent).toContain('Ezkey never sends');
    expect(enabledFlags().every((enabled) => !enabled)).toBe(true);
    expect(mocks.fetchBlobUrl).not.toHaveBeenCalled();
    expect(screen.queryByTestId('admin-bind-challenge')).toBeNull();

    await act(async () => {
      fireEvent.click(screen.getByTestId('admin-bind-reveal'));
    });

    expect(enabledFlags().at(-1)).toBe(true);
    expect(mocks.fetchBlobUrl).toHaveBeenCalledWith('/api/v1/admins/7/onboarding/qrcode');
    expect(screen.getByTestId('admin-bind-qr')).toBeTruthy();
    expect(screen.getByTestId('admin-bind-challenge').textContent).toBe('123456');
    expect(screen.getByTestId('admin-bind-token').textContent).toContain('token-abc');
  });

  it('at BOUND asks for the code only, with the guard warning and no QR', async () => {
    onboardingReturns({ enrollmentProofToken: null, enrollmentChallenge: 654321 });
    renderCard('BOUND');

    expect(screen.getByTestId('admin-bind-reveal').textContent).toContain('Show binding code');
    await act(async () => {
      fireEvent.click(screen.getByTestId('admin-bind-reveal'));
    });

    expect(mocks.fetchBlobUrl).not.toHaveBeenCalled();
    expect(screen.getByTestId('admin-bind-bound-warning')).toBeTruthy();
    expect(screen.getByTestId('admin-bind-challenge').textContent).toBe('654321');
    expect(screen.queryByTestId('admin-bind-token')).toBeNull();
  });

  it('hides the material again on a second click', async () => {
    onboardingReturns({ enrollmentProofToken: 'token-abc', enrollmentChallenge: 123456 });
    renderCard('CREATED');

    await act(async () => {
      fireEvent.click(screen.getByTestId('admin-bind-reveal'));
    });
    await act(async () => {
      fireEvent.click(screen.getByTestId('admin-bind-reveal'));
    });

    expect(screen.queryByTestId('admin-bind-qr')).toBeNull();
    expect(screen.queryByTestId('admin-bind-challenge')).toBeNull();
  });
});
