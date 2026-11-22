// Demo Login end-to-end matrix: the browser drives Demo Login and the Demo Device plays the phone.
// Each case leaves screenshots in docs/e2e/screenshots/ (see docs/e2e/README.md).
import { test, expect, type Browser, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { answerOnDevice, appLogs, CONTAINER, openOnDevice, provision, SHOTS, startApp, TTL_SECONDS } from './support';

const p = provision();
const e = p.enrollments;

let device: Page;

async function phone(browser: Browser): Promise<Page> {
  const ctx = await browser.newContext({ viewport: { width: 430, height: 860 } });
  return ctx.newPage();
}

async function shot(page: Page, name: string) {
  await page.screenshot({ path: `${SHOTS}/${name}.png` });
}

async function startLogin(page: Page, username: string, challenge = true) {
  await page.goto('/');
  await page.getByTestId('login-username').fill(username);
  const toggle = page.getByTestId('login-challenge-toggle');
  if ((await toggle.isChecked()) !== challenge) await toggle.click();
  await page.getByTestId('login-submit').click();
}

async function expectError(page: Page, code: string, name: string) {
  await expect(page.getByTestId('login-error')).toBeVisible();
  await expect(page.getByTestId('login-error-code')).toContainText(code);
  await shot(page, name);
}

test.describe.configure({ mode: 'serial' });

test.beforeAll(async ({ browser }) => {
  device = await phone(browser);
});

test.describe('outcomes on the device', () => {
  test('01 approved with a challenge code', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('login-form')).toBeVisible();
    await shot(page, '01a-login-form');

    await startLogin(page, 'alice');
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    const code = (await page.getByTestId('login-challenge').locator('p').nth(1).innerText()).trim();
    expect(code).toMatch(/^\d{2}$/);
    await shot(page, '01b-waiting-with-code');

    await openOnDevice(device, e.alice.enrollmentId);
    await answerOnDevice(device, true, code, '01c-device-approve');
    await shot(device, '01d-device-result');

    await expect(page.getByTestId('home')).toBeVisible();
    await expect(page.getByTestId('home-welcome')).toContainText('Alice Martin');
    await shot(page, '01e-signed-in');

    // The session survives a reload (HttpOnly cookie, server-side session).
    await page.reload();
    await expect(page.getByTestId('home')).toBeVisible();

    await page.getByTestId('home-logout').click();
    await expect(page.getByTestId('login-form')).toBeVisible();
    await page.reload();
    await expect(page.getByTestId('login-form')).toBeVisible();
  });

  test('02 approved without a challenge code', async ({ page }) => {
    await startLogin(page, 'alice', false);
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    await expect(page.getByTestId('login-challenge')).toHaveCount(0);
    await shot(page, '02a-waiting-no-code');
    await openOnDevice(device, e.alice.enrollmentId);
    await answerOnDevice(device, true, undefined, '02b-device-approve');
    await expect(page.getByTestId('home')).toBeVisible();
    await page.getByTestId('home-logout').click();
  });

  test('03 refused on the device', async ({ page }) => {
    await startLogin(page, 'alice');
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    await openOnDevice(device, e.alice.enrollmentId);
    await answerOnDevice(device, false, undefined, '03a-device-deny');
    await expect(page.getByTestId('login-refused')).toBeVisible();
    await shot(page, '03b-refused');
  });

  test('04 wrong challenge code picked on the device', async ({ page }) => {
    await startLogin(page, 'alice');
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    const code = Number((await page.getByTestId('login-challenge').locator('p').nth(1).innerText()).trim());
    const wrong = String((code + 1) % 100).padStart(2, '0');
    await openOnDevice(device, e.alice.enrollmentId);
    await answerOnDevice(device, true, wrong, '04a-device-wrong-code');
    await shot(device, '04b-device-result');
    // Ezkey marks the attempt INVALID: the device answered, but not with the code shown here.
    await expect(page.getByTestId('login-invalid')).toBeVisible();
    await shot(page, '04c-app-result');
  });

  test('05 nobody answers: the request expires', async ({ page }) => {
    test.setTimeout((TTL_SECONDS + 60) * 1000);
    await startLogin(page, 'alice');
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    await page.waitForTimeout(4000);
    await shot(page, '05a-waiting-countdown');
    await expect(page.getByTestId('login-expired')).toBeVisible({ timeout: (TTL_SECONDS + 30) * 1000 });
    await shot(page, '05b-expired');
  });

  test('06 the user cancels: the request is withdrawn from the device', async ({ page }) => {
    await startLogin(page, 'alice');
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    // A person takes a moment before cancelling. Cancelling within milliseconds of the start can
    // collide with the back end's first wait call on the same API key and get a 401: #1433.
    await page.waitForTimeout(1500);
    await page.getByTestId('login-cancel').click();
    await expect(page.getByTestId('login-cancelled')).toBeVisible();
    await shot(page, '06a-cancelled');
    await expect
      .poll(() => appLogs(), { timeout: 10_000 })
      .toMatch(/login_cancelled user=alice/);
    await device.goto(`${process.env.E2E_DEVICE_URL ?? 'http://localhost:8083'}/phone/ezkey/enrollments/${e.alice.enrollmentId}/auth`);
    await expect(device.getByTestId('demo-device-auth-form')).toHaveCount(0);
    await shot(device, '06b-device-nothing-pending');
  });
});

test.describe('account and enrollment errors', () => {
  test('07 username unknown to the application (Ezkey is never called)', async ({ page }) => {
    await startLogin(page, 'mallory');
    await expectError(page, 'account-unavailable', '07-unknown-user');
    expect(appLogs()).toMatch(/login_refused_by_app reason=user-unknown/);
  });

  test('08 user disabled in the application', async ({ page }) => {
    await startLogin(page, 'frank');
    await expectError(page, 'account-unavailable', '08-disabled-user');
    expect(appLogs()).toMatch(/login_refused_by_app reason=user-inactive/);
  });

  test('09 application user with no Ezkey enrollment', async ({ page }) => {
    await startLogin(page, 'erin');
    await expectError(page, 'enrollment-unavailable', '09-no-enrollment');
  });

  test('10 enrollment created but never activated on a device', async ({ page }) => {
    await startLogin(page, 'bob');
    await expectError(page, 'enrollment-unavailable', '10-enrollment-not-activated');
  });

  test('11 enrollment deactivated by an administrator', async ({ page }) => {
    await startLogin(page, 'carol');
    await expectError(page, 'enrollment-unavailable', '11-enrollment-deactivated');
  });

  test('12 enrollment revoked by an administrator', async ({ page }) => {
    await startLogin(page, 'dave');
    await expectError(page, 'enrollment-unavailable', '12-enrollment-revoked');
  });

  test('13 enrollment that belongs to another application', async ({ page }) => {
    await startLogin(page, 'oscar');
    await expectError(page, 'enrollment-unavailable', '13-enrollment-other-integration');
  });

  test('14 a second request while one already waits on the device', async ({ browser, page }) => {
    await startLogin(page, 'alice');
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    const second = await (await browser.newContext({ viewport: { width: 900, height: 760 } })).newPage();
    await startLogin(second, 'alice');
    // Integration policy REPLACE (the default): the new request supersedes the one waiting.
    await expect(second.getByTestId('login-waiting')).toBeVisible();
    await shot(second, '14a-second-request-waits');
    await expect(page.getByTestId('login-expired')).toBeVisible();
    await shot(page, '14b-first-request-superseded');
    await second.waitForTimeout(1500);
    await second.getByTestId('login-cancel').click();
    await expect(second.getByTestId('login-cancelled')).toBeVisible();
  });
});

test.describe('application configuration and service errors', () => {
  test.afterAll(async () => {
    await startApp();
  });

  test('15 wrong API key secret', async ({ page }) => {
    await startApp({ secretKey: 'ezkey_skey_wrong' });
    await startLogin(page, 'alice');
    await expectError(page, 'configuration-error', '15-wrong-api-key');
    expect(appLogs()).toMatch(/ezkey_call_refused step=create user=alice status=401/);
  });

  test('16 Integration API unreachable', async ({ page }) => {
    await startApp({ integrationApiUrl: 'http://integration-api:7999' });
    await startLogin(page, 'alice');
    await expectError(page, 'service-unavailable', '16-ezkey-unreachable');
  });

  test('17 application not configured (no API key)', async ({ page }) => {
    await startApp({ integrationKey: null, secretKey: null });
    await page.goto('/');
    await expect(page.getByTestId('login-not-configured')).toBeVisible();
    await startLogin(page, 'alice');
    await expectError(page, 'not-configured', '17-not-configured');
  });

  test('18 too many sign-in attempts from one address', async ({ page }) => {
    await startApp({ rateLimitPerMinute: 2 });
    for (const name of ['mallory', 'mallory']) {
      await startLogin(page, name);
      await expect(page.getByTestId('login-error')).toBeVisible();
    }
    await startLogin(page, 'alice');
    await expectError(page, 'rate-limited', '18-rate-limited');
  });
});

test.describe('session', () => {
  async function signIn(page: Page) {
    await startLogin(page, 'alice', false);
    await expect(page.getByTestId('login-waiting')).toBeVisible();
    await openOnDevice(device, e.alice.enrollmentId);
    await answerOnDevice(device, true);
    await expect(page.getByTestId('home')).toBeVisible();
  }

  test('19 a forged session cookie is not a session', async ({ page, context }) => {
    await context.addCookies([{ name: 'demo_login_session', value: 'forged', url: 'http://localhost:18082' }]);
    await page.goto('/');
    await expect(page.getByTestId('login-form')).toBeVisible();
  });

  test('20 an expired session sends the user back to sign-in', async ({ page }) => {
    await signIn(page);
    execFileSync('docker', [
      'exec', CONTAINER, 'node', '-e',
      "const {DatabaseSync}=require('node:sqlite');new DatabaseSync('/app/data/demo-login.db').exec(\"UPDATE sessions SET expires_at='2000-01-01T00:00:00Z'\")",
    ]);
    await page.reload();
    await expect(page.getByTestId('login-form')).toBeVisible();
    await shot(page, '20-session-expired');
  });

  test('21 French', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('lang-fr').click();
    await shot(page, '21a-fr-login-form');
    await signIn(page);
    await shot(page, '21b-fr-signed-in');
    await page.getByTestId('home-logout').click();
    await page.getByTestId('lang-en').click();
  });
});
