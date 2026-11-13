import { expect, type BrowserContext, type Page } from '@playwright/test';
import { testEnv } from './test-env';

const DEMO_DEVICE_PENDING_TIMEOUT_MS = 55_000;
/** Safety net when Auth API runs without `docker-test` (prod-like pending rate limit ~10/min). */
const DEMO_DEVICE_PENDING_INTERVAL_MS = 6_000;

/**
 * Auth URLs of the Demo Device enrollments to watch.
 *
 * The Demo Device is shared state: specs may bind more enrollments to it (#1271 does), and its
 * home list groups rows by tenant, so the first row is not necessarily `admin.docker`. Without a
 * pin we watch every row and stop on the one that receives the request.
 */
async function demoDeviceAuthUrls(demoPage: Page): Promise<string[]> {
  await demoPage.goto(`${testEnv.demoDeviceUrl}/phone/ezkey`);

  const links = testEnv.demoDeviceTestEnrollmentId
    ? demoPage.locator(
        `[data-testid="demo-device-enrollment-link"][data-enrollment-id="${testEnv.demoDeviceTestEnrollmentId}"]`,
      )
    : demoPage.getByTestId('demo-device-enrollment-link');

  await expect(links.first()).toBeVisible({ timeout: 30_000 });

  const hrefs = await links.evaluateAll((elements) =>
    elements.map((element) => element.getAttribute('href') ?? ''),
  );
  if (hrefs.some((href) => href.trim() === '')) {
    throw new Error('A Demo Device enrollment link is missing its href attribute.');
  }
  return hrefs.map((href) => new URL(href, testEnv.demoDeviceUrl).toString());
}

async function openDemoDevicePendingRequest(demoPage: Page): Promise<void> {
  const authUrls = await demoDeviceAuthUrls(demoPage);
  const deadline = Date.now() + DEMO_DEVICE_PENDING_TIMEOUT_MS;

  while (Date.now() < deadline) {
    for (const authUrl of authUrls) {
      await demoPage.goto(authUrl);
      if (await demoPage.getByTestId('demo-device-auth-pending').isVisible().catch(() => false)) {
        return;
      }
    }
    await demoPage.waitForTimeout(DEMO_DEVICE_PENDING_INTERVAL_MS);
  }

  throw new Error(
    `The Demo Device never reached a pending authentication request on any of its ${authUrls.length} enrollment(s). Confirm clean-start completed and the demo-device is pre-seeded.`,
  );
}

async function startPasswordlessLogin(adminPage: Page): Promise<void> {
  await adminPage.goto('/login');
  await expect(adminPage.getByTestId('login-page')).toBeVisible();
  await adminPage.getByTestId('login-username-input').fill(testEnv.adminUsername);

  const challengeToggle = adminPage.getByTestId('login-challenge-toggle');
  if (await challengeToggle.isChecked()) {
    await challengeToggle.uncheck();
  }

  await adminPage.getByTestId('login-submit-button').click();
  await expect(adminPage.getByTestId('login-waiting-state')).toBeVisible({ timeout: 20_000 });
}

export async function loginViaDemoDevice(
  adminPage: Page,
  context: BrowserContext,
  decision: 'approve' | 'deny'
): Promise<Page> {
  await startPasswordlessLogin(adminPage);

  const demoPage = await context.newPage();
  await openDemoDevicePendingRequest(demoPage);

  if (decision === 'approve') {
    await demoPage.getByTestId('demo-device-approve-button').click();
    await expect(demoPage.getByTestId('demo-device-auth-result')).toBeVisible();
    await expect(demoPage.getByTestId('demo-device-result-success')).toBeVisible();
    await demoPage.getByTestId('demo-device-back-to-enrollments').click();
    await expect(demoPage).toHaveURL(/\/phone\/ezkey\/?$/);
    await expect(adminPage).toHaveURL(/\/dashboard$/, { timeout: 60_000 });
    await expect(adminPage.getByTestId('app-shell')).toBeVisible();
  } else {
    await demoPage.getByTestId('demo-device-deny-button').click();
    await expect(demoPage.getByTestId('demo-device-auth-result')).toBeVisible();
    await expect(demoPage.getByTestId('demo-device-result-denied')).toBeVisible();
    await demoPage.getByTestId('demo-device-back-to-enrollments').click();
    await expect(demoPage).toHaveURL(/\/phone\/ezkey\/?$/);
    await expect(adminPage.getByTestId('login-rejected-state')).toBeVisible({ timeout: 60_000 });
  }

  return demoPage;
}
