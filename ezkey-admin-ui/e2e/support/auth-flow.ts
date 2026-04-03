import { expect, type BrowserContext, type Page } from '@playwright/test';
import { testEnv } from './test-env';

const DEMO_DEVICE_PENDING_ATTEMPTS = 12;
const DEMO_DEVICE_PENDING_DELAY_MS = 1_500;

async function openDemoDevicePendingRequest(demoPage: Page): Promise<void> {
  await demoPage.goto(`${testEnv.demoDeviceUrl}/phone/ezkey`);

  const firstEnrollment = demoPage.getByTestId('demo-device-enrollment-link').first();
  await expect(firstEnrollment).toBeVisible({ timeout: 30_000 });

  const enrollmentHref = await firstEnrollment.getAttribute('href');
  if (enrollmentHref == null || enrollmentHref.trim() === '') {
    throw new Error('Demo Device enrollment link is missing its href attribute.');
  }

  const authUrl = new URL(enrollmentHref, testEnv.demoDeviceUrl).toString();

  for (let attempt = 0; attempt < DEMO_DEVICE_PENDING_ATTEMPTS; attempt += 1) {
    await demoPage.goto(authUrl);
    if (await demoPage.getByTestId('demo-device-auth-pending').isVisible().catch(() => false)) {
      return;
    }
    await demoPage.waitForTimeout(DEMO_DEVICE_PENDING_DELAY_MS);
  }

  throw new Error(
    'The Demo Device never reached a pending authentication request. Confirm clean-start completed and the demo-device is pre-seeded.'
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
