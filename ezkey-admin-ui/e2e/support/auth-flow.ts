import { expect, type BrowserContext, type Page } from '@playwright/test';
import { testEnv } from './test-env';

const DEMO_DEVICE_PENDING_TIMEOUT_MS = 55_000;
/** Safety net when Auth API runs without `docker-test` (prod-like pending rate limit ~10/min). */
const DEMO_DEVICE_PENDING_INTERVAL_MS = 6_000;

async function openDemoDevicePendingRequest(demoPage: Page): Promise<void> {
  await demoPage.goto(`${testEnv.demoDeviceUrl}/phone/ezkey`);

  const enrollmentLink = testEnv.demoDeviceTestEnrollmentId
    ? demoPage.locator(
        `[data-testid="demo-device-enrollment-link"][data-enrollment-id="${testEnv.demoDeviceTestEnrollmentId}"]`,
      )
    : demoPage.getByTestId('demo-device-enrollment-link').first();

  await expect(enrollmentLink).toBeVisible({ timeout: 30_000 });

  const enrollmentHref = await enrollmentLink.getAttribute('href');
  if (enrollmentHref == null || enrollmentHref.trim() === '') {
    throw new Error('Demo Device enrollment link is missing its href attribute.');
  }

  const authUrl = new URL(enrollmentHref, testEnv.demoDeviceUrl).toString();
  const deadline = Date.now() + DEMO_DEVICE_PENDING_TIMEOUT_MS;

  while (Date.now() < deadline) {
    await demoPage.goto(authUrl);
    if (await demoPage.getByTestId('demo-device-auth-pending').isVisible().catch(() => false)) {
      return;
    }
    await demoPage.waitForTimeout(DEMO_DEVICE_PENDING_INTERVAL_MS);
  }

  throw new Error(
    'The Demo Device never reached a pending authentication request. Confirm clean-start completed and the demo-device is pre-seeded.',
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
    await expect(adminPage).toHaveURL(/\/dashboard$/, { timeout: 60_000 });
    await expect(adminPage.getByTestId('app-shell')).toBeVisible();
  } else {
    await demoPage.getByTestId('demo-device-deny-button').click();
    await expect(demoPage.getByTestId('demo-device-auth-result')).toBeVisible();
    await expect(demoPage.getByTestId('demo-device-result-denied')).toBeVisible();
    await expect(adminPage.getByTestId('login-rejected-state')).toBeVisible({ timeout: 60_000 });
  }

  return demoPage;
}
