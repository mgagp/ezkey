import { expect, test } from '@playwright/test';
import { playwrightArtifactPath } from './support/artifact-path';
import { loginViaDemoDevice } from './support/auth-flow';

test('@smoke public shell supports language switch and contextual help', async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-page')).toBeVisible();

  await page.getByTestId('login-language-fr').click();
  await expect
    .poll(() => page.evaluate(() => window.localStorage.getItem('ezkey-admin-ui-lang')))
    .toBe('fr');

  await page.getByTestId('login-help-button').click();
  await expect(page.getByRole('dialog')).toBeVisible();
});

test('@smoke unknown routes resolve to the 404 shell', async ({ page }) => {
  await page.goto('/definitely-not-a-real-route');
  await expect(page.getByTestId('not-found-page')).toBeVisible();
  await expect(page.getByText('404')).toBeVisible();
  await expect(page.getByTestId('not-found-back-button')).toBeVisible();
});

test('@smoke @device-backed real login through Demo Device reaches the authenticated shell', async ({
  page,
  context,
}) => {
  const demoPage = await loginViaDemoDevice(page, context, 'approve');

  await expect(page.getByTestId('app-shell')).toBeVisible();
  await expect(page.getByTestId('app-sidebar')).toBeVisible();
  await expect(page.getByTestId('sidebar-link-dashboard')).toBeVisible();
  await expect(page.getByTestId('sidebar-link-tenants')).toBeVisible();
  await expect(page.getByTestId('sidebar-link-encryptionKeys')).toBeVisible();
  await expect(page.getByTestId('sidebar-link-integrity')).toBeVisible();

  await demoPage.close();
});

test('@smoke @device-backed representative authenticated workflow loads admins and logs out', async ({
  page,
  context,
}) => {
  const demoPage = await loginViaDemoDevice(page, context, 'approve');

  await page.getByTestId('sidebar-link-admins').click();
  await expect(page).toHaveURL(/\/admins$/);
  await expect(page.locator('table').first()).toBeVisible();

  // #1399: a row opens the record page; prev/next walks the list; the breadcrumb goes back.
  const rows = page.locator('table tbody tr');
  const firstIdCell = rows.first().locator('td').first();
  await expect(firstIdCell).toHaveText(/^\d+$/);
  const rowCount = await rows.count();
  await firstIdCell.click();
  await expect(page).toHaveURL(/\/admins\/\d+$/);
  await expect(page.getByTestId('admin-record')).toBeVisible();
  if (rowCount > 1) {
    const firstUrl = page.url();
    await page.getByRole('button', { name: /next entry|entrée suivante/i }).click();
    await expect(page).not.toHaveURL(firstUrl);
    await expect(page.getByTestId('admin-record')).toBeVisible();
  }

  // #1428: the enrollment row follows enrollmentStatus; the record links to its audit-log context.
  await expect(page.getByTestId('admin-detail-enrollment-state')).toHaveAttribute(
    'data-state',
    /^(CREATED|BOUND|VERIFIED|INVALID|REVOKED|EXPIRED|NONE)$/,
  );
  const recordAdminId = new URL(page.url()).pathname.split('/').pop();
  await page.getByTestId('admin-detail-view-audits-actor').click();
  await expect(page).toHaveURL(new RegExp(`/audit-logs\\?.*adminId=${recordAdminId}(&|$)`));
  await expect(page).toHaveURL(/source=admin-detail/);
  await expect(page.getByTestId('audit-logs-context-admin')).toContainText(`#${recordAdminId}`);
  await page.goBack();
  await expect(page.getByTestId('admin-record')).toBeVisible();

  await page.getByRole('navigation', { name: 'Breadcrumb' }).getByRole('link').first().click();
  await expect(page).toHaveURL(/\/admins$/);

  await page.getByTestId('app-logout-button').click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-page')).toBeVisible();

  await demoPage.close();
});

test('@smoke @device-backed Integrity cut 3 micro-dashboard is the glance strip', async ({
  page,
  context,
}, testInfo) => {
  const demoPage = await loginViaDemoDevice(page, context, 'approve');

  await page.getByTestId('sidebar-link-integrity').click();
  await expect(page).toHaveURL(/\/integrity$/);

  // After D1 settles (fresh verdict or AUTO job), the micro-dashboard owns the strip.
  const dashboard = page.getByTestId('integrity-micro-dashboard');
  await expect(dashboard).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('integrity-async-job-banner')).toHaveCount(0);
  // Do not screenshot mid-flight RUNNING — wait for a terminal glance tone.
  await expect
    .poll(async () => dashboard.getAttribute('data-tone'), { timeout: 60_000 })
    .not.toMatch(/^(running|nightly-running)$/);
  await expect(page.getByTestId('integrity-micro-dashboard-verdict')).toBeVisible();
  await expect(page.getByTestId('integrity-micro-dashboard-scope')).toBeVisible();
  await expect(page.getByTestId('integrity-micro-dashboard-reverify')).toBeVisible();

  await page.screenshot({
    path: playwrightArtifactPath(testInfo, 'integrity-micro-dashboard-smoke.png'),
    fullPage: true,
  });

  await demoPage.close();
});

test('@elective @device-backed denying on Demo Device surfaces the retry path', async ({
  page,
  context,
}) => {
  const demoPage = await loginViaDemoDevice(page, context, 'deny');

  await expect(page.getByTestId('login-rejected-state')).toBeVisible();
  await expect(page.getByRole('button').filter({ hasText: /try again/i })).toBeVisible();

  await demoPage.close();
});

test('@elective @device-backed denying a challenge-code request needs no code (#1466)', async ({
  page,
  context,
}) => {
  const demoPage = await loginViaDemoDevice(page, context, 'deny', { requireChallenge: true });

  await expect(page.getByTestId('login-rejected-state')).toBeVisible();

  await demoPage.close();
});
