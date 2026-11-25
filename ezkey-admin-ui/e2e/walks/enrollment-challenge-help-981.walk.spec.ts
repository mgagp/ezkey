import { expect, test, type Locator } from '@playwright/test';

import { playwrightArtifactPath } from '../support/artifact-path';
import { injectSession, mockAdminApi, paged, type WalkLocale } from '../support/mock-admin-api';

/**
 * Interface gate for #981 (mini-gate in the PR description, no separate WALK record):
 * neutral help text under "Require challenge code" in the create and edit enrollment dialogs.
 * Step names carry the gate row number; captures land next to the test output.
 */

const integrations = [
  { id: 1, code: 'portail-rh', name: 'Portail RH', integrationName: 'Portail RH', isSystemIntegration: false, active: true, tenantId: 1 },
];
const enrollment = {
  enrollmentId: 10,
  enrollmentName: 'iPhone 15 de Marie',
  userIdentifier: 'mdupont',
  integrationId: 1,
  integrationName: 'Portail RH',
  isSystemIntegration: false,
  enrollmentStatus: 'VERIFIED',
  enrollmentActive: true,
  operational: true,
  tenantId: 1,
  tenantName: 'Garage du coin',
  createdAt: '2026-11-20T14:00:00Z',
  lastUsedAt: '2026-11-22T14:00:00Z',
  authAttemptChallengeRequired: true,
};

const copy = {
  en: {
    help: 'Ezkey enforces it on every authentication for this enrollment, whatever the integration requests. When off, the integration can still request one.',
    create: /new enrollment|create enrollment/i,
  },
  fr: {
    help: "Ezkey l'impose à chaque authentification de cet enrôlement, quoi que demande l'intégration. Désactivé, l'intégration peut quand même en demander un.",
    create: /nouvel enrôlement|créer un enrôlement|nouvel/i,
  },
} satisfies Record<WalkLocale, unknown>;

/** Integration product names and a hard-coded code length must never appear in this help. */
const forbidden = /PAM|SSH|Jenkins|SDK|\d/;

for (const locale of ['en', 'fr'] as const) {
  test(`walk #981 challenge help text (${locale.toUpperCase()})`, async ({ context, page }, testInfo) => {
    const T = copy[locale];
    const shot = (name: string) =>
      page.screenshot({ path: playwrightArtifactPath(testInfo, `walk-981-${locale}-${name}.png`), fullPage: true });
    const check = (row: number, label: string, body: () => Promise<void>) => test.step(`#${row} ${label}`, body);
    const describedHelp = async (checkbox: Locator, help: Locator) => {
      await expect.soft(help).toHaveText(T.help);
      expect.soft(await checkbox.getAttribute('aria-describedby')).toBe(await help.getAttribute('id'));
      expect.soft(await help.textContent()).not.toMatch(forbidden);
    };

    await page.setViewportSize({ width: 1280, height: 900 });
    await injectSession(context, locale);
    const api = await mockAdminApi(page, [
      ({ path }) => (/\/integrations\/?$/.test(path) ? paged(integrations) : undefined),
      ({ path }) => (/\/integrations\/1$/.test(path) ? integrations[0] : undefined),
      ({ path }) => (/\/enrollments\/10$/.test(path) ? enrollment : undefined),
      ({ path }) => (/\/enrollments\/?$/.test(path) ? paged([enrollment]) : undefined),
    ]);

    // Create dialog
    await page.goto('/enrollments');
    await page.getByRole('button', { name: T.create }).first().click();
    const createDialog = page.getByRole('dialog');
    await expect(createDialog).toBeVisible();
    const createBox = createDialog.locator('#enr-challenge');
    const createHelp = createDialog.getByTestId('enrollment-challenge-help');

    await check(1, 'create: help under the checkbox, tied to it, no product name or length', async () => {
      await describedHelp(createBox, createHelp);
      const [box, help] = [await createBox.boundingBox(), await createHelp.boundingBox()];
      expect.soft(box && help && help.y > box.y).toBe(true);
    });
    await check(2, 'create: label still toggles the checkbox', async () => {
      await expect.soft(createBox).not.toBeChecked();
      await createDialog.locator('label[for="enr-challenge"]').click();
      await expect.soft(createBox).toBeChecked();
    });
    await shot('01-create');
    await page.keyboard.press('Escape');

    // Edit dialog (VERIFIED enrollment)
    await page.goto('/enrollments/10');
    await page.getByTestId('enrollment-edit-metadata').click();
    const editDialog = page.getByRole('dialog');
    await expect(editDialog).toBeVisible();
    const editBox = editDialog.locator('input[type="checkbox"][aria-describedby="edit-challenge-help"]');
    const editHelp = editDialog.getByTestId('enrollment-challenge-help');

    await check(3, 'edit: same help under the checkbox, tied to it', async () => {
      await expect.soft(editBox).toBeChecked();
      await describedHelp(editBox, editHelp);
    });
    await check(4, 'edit: clicking the help text toggles the checkbox (one label)', async () => {
      await editHelp.click();
      await expect.soft(editBox).not.toBeChecked();
    });
    await shot('02-edit');

    await testInfo.attach('unmocked-requests', { body: [...api.unmocked].join('\n') || '(none)', contentType: 'text/plain' });
  });
}
