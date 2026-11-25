import { expect, test } from '@playwright/test';

import { playwrightArtifactPath } from '../support/artifact-path';
import { injectSession, mockAdminApi, type WalkLocale } from '../support/mock-admin-api';

/**
 * Walk Gate WALK-2026-11-25-enrollment-code-1494 (#1494): copy the enrollment code (the QR JSON).
 * Record: product-docs/global/backlog/walk-gates/WALK-2026-11-25-enrollment-code-1494.md.
 * Step names carry the checklist row number; captures land next to the test output.
 */

const TOKEN = 'ezkey_proof_walk1494';
const CODE = `{"enrollmentProofToken":"${TOKEN}","authUrl":"https://auth.garage.example"}`;

const base = {
  integrationId: 1,
  integrationName: 'Portail RH',
  isSystemIntegration: false,
  tenantId: 1,
  tenantName: 'Garage du coin',
  enrollmentActive: true,
  operational: false,
  createdAt: '2026-11-24T14:00:00Z',
  authAttemptChallengeRequired: false,
};
const enrollments: Record<string, object> = {
  // CREATED, address configured: the server returns the code.
  20: { ...base, enrollmentId: 20, enrollmentName: 'iPhone de Marie', enrollmentStatus: 'CREATED', enrollmentProofToken: TOKEN, enrollmentChallenge: 482913, enrollmentCode: CODE },
  // CREATED, no address configured: token but no code.
  21: { ...base, enrollmentId: 21, enrollmentName: 'Pixel de Paul', enrollmentStatus: 'CREATED', enrollmentProofToken: TOKEN, enrollmentChallenge: 482913, enrollmentCode: null },
  // VERIFIED: no bind material at all.
  22: { ...base, enrollmentId: 22, enrollmentName: 'Galaxy de Léa', enrollmentStatus: 'VERIFIED', operational: true, enrollmentProofToken: null, enrollmentChallenge: null, enrollmentCode: null },
};
const admin = {
  adminId: 7,
  username: 'claire.tremblay',
  adminType: 'TENANT_ADMIN',
  tenantId: 1,
  tenantName: 'Garage du coin',
  active: true,
  lifecycleStatus: 'ACTIVE',
  enrollmentId: 30,
  enrollmentStatus: 'CREATED',
  createdAt: '2026-11-24T14:00:00Z',
};

const copy = {
  en: {
    title: 'Enrollment code',
    copy: 'Copy enrollment code',
    otherChannel: 'another channel',
    unavailable: 'ezkey.qr.auth-base-url',
    legacy: /^(copy token|enrollment token|enrollment proof token.*)$/i,
    reset: 'Reset MFA enrollment',
  },
  fr: {
    title: "Code d'enrôlement",
    copy: "Copier le code d'enrôlement",
    otherChannel: 'autre canal',
    unavailable: 'ezkey.qr.auth-base-url',
    legacy: /^(copier le jeton|jeton d'enrôlement|jeton de preuve d'inscription.*)$/i,
    reset: "Réinitialiser l'inscription MFA",
  },
} satisfies Record<WalkLocale, unknown>;

for (const locale of ['en', 'fr'] as const) {
  test(`walk #1494 enrollment code (${locale.toUpperCase()})`, async ({ context, page }, testInfo) => {
    const T = copy[locale];
    await context.grantPermissions(['clipboard-read', 'clipboard-write']);
    const shot = (name: string) =>
      page.screenshot({ path: playwrightArtifactPath(testInfo, `walk-1494-${locale}-${name}.png`), fullPage: true });
    const check = (row: number, label: string, body: () => Promise<void>) =>
      test.step(`#${row} ${label}`, body);

    await page.setViewportSize({ width: 1280, height: 900 });
    await injectSession(context, locale);
    await mockAdminApi(page, [
      ({ path }) => {
        const m = path.match(/\/enrollments\/(\d+)$/);
        return m ? enrollments[m[1]] : undefined;
      },
      ({ path }) => (/\/admins\/7$/.test(path) ? admin : undefined),
      ({ path }) =>
        /\/admins\/7\/onboarding$/.test(path)
          ? { enrollmentId: 30, enrollmentProofToken: TOKEN, enrollmentChallenge: 731205, recoveryCodes: [], enrollmentCode: CODE }
          : undefined,
    ]);

    // Enrollment record, CREATED, address configured
    await page.goto('/enrollments/20');
    const card = page.getByTestId('enrollment-token-card');
    await expect(card).toBeVisible();

    await check(1, `record: card titled "${T.title}", value masked, no bare token on screen`, async () => {
      await expect.soft(card).toContainText(T.title);
      await expect.soft(page.getByTestId('enrollment-code-value')).not.toContainText(TOKEN);
      await expect.soft(page.getByText(TOKEN)).toHaveCount(0);
    });
    await check(2, 'record: hint says the binding challenge goes through another channel', async () => {
      await expect.soft(card).toContainText(T.otherChannel);
    });
    await shot('01-record-masked');

    await check(3, `record: "${T.copy}" copies the exact server text`, async () => {
      await page.getByTestId('enrollment-code-copy').click();
      await expect.soft(page.getByTestId('enrollment-code-copy')).toBeVisible();
      expect.soft(await page.evaluate(() => navigator.clipboard.readText())).toBe(CODE);
      await expect.soft(page.getByRole('button', { name: T.copy })).toHaveCount(1);
    });
    await check(4, 'record: Show reveals the JSON, without the challenge', async () => {
      await page.getByTestId('enrollment-code-reveal').click();
      await expect.soft(page.getByTestId('enrollment-code-value')).toHaveText(CODE);
      await expect.soft(page.getByTestId('enrollment-code-value')).not.toContainText('482913');
    });
    await shot('02-record-revealed');

    await check(5, 'record: no legacy token label or "Copy token" button', async () => {
      await expect.soft(page.getByText(T.legacy)).toHaveCount(0);
    });

    // Enrollment record, CREATED, no address configured
    await page.goto('/enrollments/21');
    await expect(page.getByTestId('enrollment-token-card')).toBeVisible();
    await check(6, 'record without an Auth API address: no copy, and the reason is shown', async () => {
      await expect.soft(page.getByTestId('enrollment-code-copy')).toHaveCount(0);
      await expect.soft(page.getByTestId('enrollment-code-unavailable')).toContainText(T.unavailable);
    });
    await shot('03-record-no-address');

    // Enrollment record, VERIFIED
    await page.goto('/enrollments/22');
    await expect(page.getByText('Galaxy de Léa').first()).toBeVisible();
    await check(7, 'record after bind: no enrollment code card', async () => {
      await expect.soft(page.getByTestId('enrollment-token-card')).toHaveCount(0);
    });

    // Administrator record: only after the audited reveal
    await page.goto('/admins/7');
    const bindCard = page.getByTestId('admin-bind-card');
    await expect(bindCard).toBeVisible();
    await check(8, 'administrator: nothing before the audited reveal', async () => {
      await expect.soft(page.getByTestId('admin-bind-code')).toHaveCount(0);
    });
    await page.getByTestId('admin-bind-reveal').click();
    await check(9, 'administrator: after the reveal, the enrollment code, masked, with its copy', async () => {
      await expect.soft(page.getByTestId('admin-bind-code')).toContainText(T.title);
      await expect.soft(page.getByTestId('admin-bind-code-value')).not.toContainText(TOKEN);
      await expect.soft(page.getByTestId('admin-bind-code-copy')).toHaveText(T.copy);
      await expect.soft(bindCard.getByText(T.legacy)).toHaveCount(0);
    });
    await shot('04-admin-revealed');
  });

  test(`walk #1494 enrollment code at activation (${locale.toUpperCase()})`, async ({ context, page }, testInfo) => {
    const T = copy[locale];
    await context.addInitScript((lng) => localStorage.setItem('ezkey-admin-ui-lang', lng), locale);
    await page.setViewportSize({ width: 1280, height: 900 });
    await mockAdminApi(page, [
      ({ path }) => (/\/public\/instance-info$/.test(path) ? { instanceName: 'Walk' } : undefined),
      ({ path, method }) =>
        method === 'POST' && /\/admin\/auth\/activate$/.test(path)
          ? { success: true, username: 'claire.tremblay', enrollmentId: 30, enrollmentProofToken: TOKEN, enrollmentChallenge: 731205, enrollmentCode: CODE }
          : undefined,
    ]);

    await page.goto('/login');
    await page.getByTestId('login-activation-link').click();
    await page.locator('input[name="activationCode"]').fill('ezkey_activation_walk1494');
    await page.getByTestId('login-activation-form').locator('button[type="submit"]').click();
    await expect(page.getByTestId('login-activation-success')).toBeVisible();
    await page.locator('details summary').first().click();

    await test.step('#10 activation: manual entry offers the enrollment code, masked, no "Copy token"', async () => {
      const block = page.getByTestId('login-activation-enrollment-code');
      await expect.soft(block).toContainText(T.title);
      await expect.soft(page.getByTestId('login-activation-enrollment-code-value')).not.toContainText(TOKEN);
      await expect.soft(page.getByTestId('login-activation-enrollment-code-copy')).toHaveText(T.copy);
      await expect.soft(page.getByText(T.legacy)).toHaveCount(0);
    });
    await page.screenshot({
      path: playwrightArtifactPath(testInfo, `walk-1494-${locale}-05-activation.png`),
      fullPage: true,
    });
  });

  test(`walk #1494 enrollment code at recovery (${locale.toUpperCase()})`, async ({ context, page }, testInfo) => {
    const T = copy[locale];
    await context.addInitScript((lng) => localStorage.setItem('ezkey-admin-ui-lang', lng), locale);
    await page.setViewportSize({ width: 1280, height: 900 });
    await mockAdminApi(page, [
      ({ path }) => (/\/public\/instance-info$/.test(path) ? { instanceName: 'Walk' } : undefined),
      ({ path, method }) =>
        method === 'POST' && /\/admin\/auth\/recover$/.test(path)
          ? { success: true, recoveryToken: 'ezkey_recovery_walk', expiresAt: new Date(Date.now() + 1_800_000).toISOString(), codesRemaining: 9, enrollmentId: 30 }
          : undefined,
      ({ path, method }) =>
        method === 'POST' && /\/admin\/enrollments\/reset$/.test(path)
          ? { success: true, enrollmentId: 30, enrollmentProofToken: TOKEN, enrollmentChallenge: 731205, integrationId: 1, enrollmentCode: CODE }
          : undefined,
    ]);

    await page.goto('/login');
    await page.getByTestId('login-recovery-link').click();
    await page.locator('#recovery-username').fill('claire.tremblay');
    await page.locator('input[name="recoveryCode"]').fill('1111-2222-3333-4444-5555-6666-7777-8888');
    await page.locator('form button[type="submit"]').click();
    await page.getByRole('button', { name: T.reset }).click();
    await page.locator('details summary').first().click();

    await test.step('#11 recovery: manual entry offers the enrollment code, masked, no "Copy token"', async () => {
      const block = page.getByTestId('login-recovery-enrollment-code');
      await expect.soft(block).toContainText(T.title);
      await expect.soft(page.getByTestId('login-recovery-enrollment-code-value')).not.toContainText(TOKEN);
      await expect.soft(page.getByTestId('login-recovery-enrollment-code-copy')).toHaveText(T.copy);
      await expect.soft(page.getByText(T.legacy)).toHaveCount(0);
    });
    await page.screenshot({
      path: playwrightArtifactPath(testInfo, `walk-1494-${locale}-06-recovery.png`),
      fullPage: true,
    });
  });
}
