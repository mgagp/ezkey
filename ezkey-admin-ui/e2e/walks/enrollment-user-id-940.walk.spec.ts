import { expect, test } from '@playwright/test';

import { playwrightArtifactPath } from '../support/artifact-path';
import { injectSession, mockAdminApi, paged, type WalkLocale } from '../support/mock-admin-api';

/**
 * Walk Gate WALK-2026-11-23-enrollment-user-id-940 (#940, PR #1455): enrollment Name / User ID.
 * Record: product-docs/global/backlog/walk-gates/WALK-2026-11-23-enrollment-user-id-940.md.
 * Step names carry the checklist row number; captures land next to the test output.
 */

const integrations = [
  { id: 1, code: 'portail-rh', name: 'Portail RH', integrationName: 'Portail RH', isSystemIntegration: false, active: true, tenantId: 1 },
  { id: 2, code: 'ezkey-admin', name: 'Ezkey Admin MFA', integrationName: 'Ezkey Admin MFA', isSystemIntegration: true, active: true, tenantId: 1 },
];
const base = {
  enrollmentStatus: 'VERIFIED',
  enrollmentActive: true,
  operational: true,
  tenantId: 1,
  tenantName: 'Garage du coin',
  createdAt: '2026-11-20T14:00:00Z',
  lastUsedAt: '2026-11-22T14:00:00Z',
  authAttemptChallengeRequired: false,
};
const enrollments: Record<string, object> = {
  10: { ...base, enrollmentId: 10, enrollmentName: 'iPhone 15 de Marie', userIdentifier: null, integrationId: 1, integrationName: 'Portail RH', isSystemIntegration: false },
  11: { ...base, enrollmentId: 11, enrollmentName: 'Pixel de Paul', userIdentifier: 'pdupuis', integrationId: 1, integrationName: 'Portail RH', isSystemIntegration: false },
  12: { ...base, enrollmentId: 12, enrollmentName: 'Admin phone', userIdentifier: null, integrationId: 2, integrationName: 'Ezkey Admin MFA', isSystemIntegration: true },
};

const copy = {
  en: {
    label: 'User ID',
    notSet: 'Not set',
    warn: 'Without a User ID, an integration that finds the user by username cannot sign this user in.',
    help: 'Username in the protected application. Integrations that find the user by username rely on it.',
    helpSys: 'Optional reference for this administrator enrollment.',
    nameHelp: 'Device label shown on the phone.',
    placeholder: "e.g. Marie's iPhone 15",
    optional: '(optional)',
    create: /new enrollment|create enrollment/i,
  },
  fr: {
    label: 'Identifiant utilisateur',
    notSet: 'Non défini',
    warn: "Sans identifiant utilisateur, une intégration qui retrouve l'utilisateur par son nom d'usager ne pourra pas le connecter.",
    help: "Nom d'usager dans l'application protégée. Les intégrations qui retrouvent l'utilisateur par son nom d'usager s'en servent.",
    helpSys: 'Référence facultative pour cet enrôlement administrateur.',
    nameHelp: "Libellé de l'appareil, affiché sur le téléphone.",
    placeholder: 'ex. iPhone 15 de Marie',
    optional: '(optionnel)',
    create: /nouvel enrôlement|créer un enrôlement|nouvel/i,
  },
} satisfies Record<WalkLocale, unknown>;

for (const locale of ['en', 'fr'] as const) {
  test(`walk #940 enrollment Name / User ID (${locale.toUpperCase()})`, async ({ context, page }, testInfo) => {
    const T = copy[locale];
    const shot = (name: string) =>
      page.screenshot({ path: playwrightArtifactPath(testInfo, `walk-940-${locale}-${name}.png`), fullPage: true });
    const check = (row: number, label: string, body: () => Promise<void>) =>
      test.step(`#${row} ${label}`, body);

    await page.setViewportSize({ width: 1280, height: 900 });
    await injectSession(context, locale);
    const api = await mockAdminApi(page, [
      ({ path }) => (/\/integrations\/?$/.test(path) ? paged(integrations) : undefined),
      ({ path }) => {
        const m = path.match(/\/integrations\/(\d+)$/);
        return m ? integrations.find((i) => i.id === Number(m[1])) : undefined;
      },
      ({ path }) => {
        const m = path.match(/\/enrollments\/(\d+)$/);
        return m ? enrollments[m[1]] : undefined;
      },
      ({ path }) => (/\/enrollments\/?$/.test(path) ? paged(Object.values(enrollments)) : undefined),
    ]);

    // List
    await page.goto('/enrollments');
    await expect(page.getByText('iPhone 15 de Marie').first()).toBeVisible();

    await check(1, 'list: User ID column right after Name', async () => {
      const headers = (await page.locator('thead th').allTextContents()).map((h) => h.trim());
      const iName = headers.findIndex((h) => /name|nom/i.test(h));
      expect.soft(headers[iName + 1]?.toLowerCase(), headers.join(' | ')).toBe(T.label.toLowerCase());
    });
    await check(2, `list: empty User ID reads "${T.notSet}", a set one shows its value`, async () => {
      const cells = (await page.getByTestId('enrollment-user-id-cell').allTextContents()).map((c) => c.trim());
      expect.soft(cells).toContain(T.notSet);
      expect.soft(cells).toContain('pdupuis');
    });
    await shot('01-list');

    // Create dialog
    await page.getByRole('button', { name: T.create }).first().click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    const nameBox = dialog.getByTestId('enrollment-field-name');
    const uidBox = dialog.getByTestId('enrollment-field-userId');
    const nameInput = dialog.locator('#enr-name');
    const uidInput = dialog.locator('#enr-uid');
    const warning = dialog.getByTestId('enrollment-user-id-warning');
    const uidHelp = uidBox.getByTestId('enrollment-user-id-help');

    await check(3, 'create: User ID directly under Name', async () => {
      const ordered = await nameBox.evaluate(
        (a, b) => !!b && !!(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING),
        await uidBox.elementHandle(),
      );
      expect.soft(ordered).toBe(true);
    });
    await check(4, 'create: Name help and device-label placeholder', async () => {
      await expect.soft(nameBox.getByTestId('enrollment-name-help')).toHaveText(T.nameHelp);
      await expect.soft(nameInput).toHaveAttribute('placeholder', T.placeholder);
    });
    await check(5, `create: User ID label with "${T.optional}" and help`, async () => {
      // Labels are uppercased by CSS: compare textContent, not innerText.
      expect.soft((await uidBox.locator('label').textContent())?.trim()).toBe(`${T.label} ${T.optional}`);
      await expect.soft(uidHelp).toHaveText(T.help);
    });
    await check(6, 'create: no warning before an integration is chosen', async () => {
      await expect.soft(warning).toHaveCount(0);
    });
    await shot('02-create-no-integration');

    const select = dialog.locator('select').first();
    await select.selectOption('1');
    await check(7, 'create: warning once an application integration is chosen', async () => {
      await expect.soft(warning).toHaveCount(1);
      await expect.soft(warning).toContainText(T.warn);
    });
    await shot('03-create-app-integration');

    await uidInput.fill('mdupont');
    await check(8, 'create: warning disappears once a User ID is typed', async () => {
      await expect.soft(warning).toHaveCount(0);
    });

    await uidInput.fill('');
    await select.selectOption('2');
    await check(9, 'create: system integration has no warning and a neutral help', async () => {
      await expect.soft(warning).toHaveCount(0);
      await expect.soft(uidHelp).toHaveText(T.helpSys);
    });
    await shot('04-create-system-integration');

    await check(10, 'create: Name capped at 64, User ID at 255', async () => {
      await expect.soft(nameInput).toHaveAttribute('maxlength', '64');
      await expect.soft(uidInput).toHaveAttribute('maxlength', '255');
    });
    await page.keyboard.press('Escape');

    // Records
    const openRecord = async (id: number, capture: string) => {
      await page.goto(`/enrollments/${id}`);
      const value = page.getByTestId('enrollment-user-id-value');
      await expect(value).toBeVisible();
      const result = {
        value: (await value.textContent())?.trim(),
        warnings: await page.getByTestId('enrollment-user-id-warning').count(),
      };
      await shot(capture);
      return result;
    };
    const app = await openRecord(10, '05-detail-app-empty');
    const sys = await openRecord(12, '06-detail-system-empty');
    await check(11, `record: "${T.notSet}" + warning on application, "${T.notSet}" without warning on system`, async () => {
      expect.soft(app).toEqual({ value: T.notSet, warnings: 1 });
      expect.soft(sys).toEqual({ value: T.notSet, warnings: 0 });
    });

    await check(12, 'no legacy labels (User Identifier, ID utilisateur) or product names on the record', async () => {
      await expect.soft(page.locator('body')).not.toContainText(/User Identifier|User identifier|ID utilisateur\b|Demo Login, PAM/);
    });

    await testInfo.attach('unmocked-requests', { body: [...api.unmocked].join('\n') || '(none)', contentType: 'text/plain' });
  });
}
