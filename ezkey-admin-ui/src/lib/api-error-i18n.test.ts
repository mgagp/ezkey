import { describe, expect, it } from 'vitest';
import i18n from 'i18next';
import '@/i18n';
import { ApiError } from './api-client';
import { EZKEY_PROBLEM_TYPE_BASE, getTranslatedApiError, problemTypeToTranslationKey } from './api-error-i18n';

describe('problemTypeToTranslationKey', () => {
  it('returns null for missing or non-Ezkey URIs', () => {
    expect(problemTypeToTranslationKey(undefined)).toBeNull();
    expect(problemTypeToTranslationKey('')).toBeNull();
    expect(problemTypeToTranslationKey('https://example.com/problems/foo')).toBeNull();
    expect(problemTypeToTranslationKey(`${EZKEY_PROBLEM_TYPE_BASE}`)).toBeNull();
    expect(problemTypeToTranslationKey(`${EZKEY_PROBLEM_TYPE_BASE}/`)).toBeNull();
  });

  it('maps authentication and admin problem types (Option B)', () => {
    expect(
      problemTypeToTranslationKey(`${EZKEY_PROBLEM_TYPE_BASE}/authentication/invalid-credentials`),
    ).toBe('authentication.invalid-credentials');
    expect(
      problemTypeToTranslationKey(`${EZKEY_PROBLEM_TYPE_BASE}/admin/validation-failed`),
    ).toBe('admin.validation-failed');
  });

  it('trims whitespace', () => {
    expect(
      problemTypeToTranslationKey(`  ${EZKEY_PROBLEM_TYPE_BASE}/authentication/auth-timeout  `),
    ).toBe('authentication.auth-timeout');
  });

  it('rejects empty or ambiguous path segments', () => {
    expect(problemTypeToTranslationKey(`${EZKEY_PROBLEM_TYPE_BASE}/authentication//foo`)).toBeNull();
  });
});

describe('getTranslatedApiError', () => {
  it('uses errors namespace when key exists, else detail', async () => {
    await i18n.changeLanguage('en');
    const err = new ApiError(
      401,
      {},
      'English detail from server',
      {
        type: `${EZKEY_PROBLEM_TYPE_BASE}/authentication/invalid-credentials`,
        detail: 'English detail from server',
      },
    );
    const t = i18n.t.bind(i18n);
    expect(getTranslatedApiError(err, t, 'fallback')).toBe('Invalid username or password.');
  });

  it('prefers curated i18n over English detail for domain.integration-has-enrollments (quick win)', async () => {
    await i18n.changeLanguage('fr');
    const err = new ApiError(
      409,
      {},
      'Cannot delete integration: it has one or more enrollments...',
      {
        type: `${EZKEY_PROBLEM_TYPE_BASE}/domain/integration-has-enrollments`,
        detail:
          'Cannot delete integration: it has one or more enrollments. Remove or revoke enrollments first.',
      },
    );
    const t = i18n.t.bind(i18n);
    expect(getTranslatedApiError(err, t, 'fallback')).toBe(
      "Impossible de supprimer cette intégration tant qu'il existe des enrôlements. Retirez ou révoquez les enrôlements d'abord.",
    );
  });

  it('prefers server detail over generic i18n for admin.invalid-argument', async () => {
    await i18n.changeLanguage('fr');
    const err = new ApiError(
      400,
      {},
      'Tenant name already exists: X',
      {
        type: `${EZKEY_PROBLEM_TYPE_BASE}/admin/invalid-argument`,
        title: 'Invalid argument',
        detail: 'Tenant name already exists: La Ferme des Licornes',
      },
    );
    const t = i18n.t.bind(i18n);
    expect(getTranslatedApiError(err, t, 'fallback')).toBe(
      'Tenant name already exists: La Ferme des Licornes',
    );
  });

  it('falls back to getApiErrorMessage when no translation for type', () => {
    const err = new ApiError(
      400,
      {},
      'Only detail',
      {
        type: `${EZKEY_PROBLEM_TYPE_BASE}/domain/unknown-problem-for-pilot`,
        detail: 'Only detail',
      },
    );
    const t = i18n.t.bind(i18n);
    expect(getTranslatedApiError(err, t, 'fallback')).toBe('Only detail');
  });

  it('returns fallback for non-ApiError', () => {
    const t = i18n.t.bind(i18n);
    expect(getTranslatedApiError(new Error('x'), t, 'fb')).toBe('fb');
  });
});
