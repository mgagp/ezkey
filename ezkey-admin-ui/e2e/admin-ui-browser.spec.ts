import { expect, test } from '@playwright/test';
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

  await page.getByTestId('app-logout-button').click();
  await expect(page).toHaveURL(/\/login$/);
  await expect(page.getByTestId('login-page')).toBeVisible();

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
