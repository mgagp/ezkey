import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { loginViaDemoDevice } from './support/auth-flow';
import { testEnv } from './support/test-env';

/** Reads the bearer token the SPA sends, so the spec can set up data through the Admin API. */
async function captureBearerToken(page: Page): Promise<string> {
  const request = page.waitForRequest(
    (candidate) =>
      candidate.url().includes('/api/v1/') && candidate.headers().authorization != null,
  );
  await page.goto('/admins');
  return (await request).headers().authorization;
}

/** Logs in as the new administrator and approves on the Demo Device they just enrolled on. */
async function assertAdminCanLogIn(
  page: Page,
  context: BrowserContext,
  username: string,
  enrollmentId: number,
): Promise<void> {
  const login = await page.request.post('/api/v1/admin/auth/login', {
    data: { username, challengeRequested: false, nonBlocking: true },
  });
  expect(login.ok()).toBe(true);
  const { authAttemptId, waiterSecret } = await login.json();

  const demoPage = await context.newPage();
  await expect(async () => {
    await demoPage.goto(`${testEnv.demoDeviceUrl}/phone/ezkey/enrollments/${enrollmentId}/auth`);
    await expect(demoPage.getByTestId('demo-device-auth-pending')).toBeVisible({ timeout: 1_000 });
  }).toPass({ timeout: 30_000 });
  await demoPage.getByTestId('demo-device-approve-button').click();
  await expect(demoPage.getByTestId('demo-device-result-success')).toBeVisible();
  await demoPage.close();

  const wait = await page.request.post('/api/v1/admin/auth/passwordless-wait', {
    data: { authAttemptId, waiterSecret },
  });
  expect(wait.ok()).toBe(true);
  expect((await wait.json()).token).toBeTruthy();
}

// #1266: an IMMEDIATE administrator is onboarded from the Admin UI, which shows the proof token and
// QR while CREATED, only the guarded code while BOUND, and no credentials action afterwards.
test('@elective @device-backed admin onboarding shows bind material only while needed', async ({
  page,
  context,
}) => {
  await loginViaDemoDevice(page, context, 'approve');
  const authorization = await captureBearerToken(page);
  const headers = { Authorization: authorization };

  const suffix = Date.now();
  const username = `e2e-1266-${suffix}`;
  const tenant = await page.request.post('/api/v1/tenants', {
    headers,
    data: { tenantName: `E2E onboarding ${suffix}`, tenantDescription: 'Playwright' },
  });
  expect(tenant.ok()).toBe(true);
  const { tenantId } = await tenant.json();

  await page.goto('/admins');
  await page.getByRole('button', { name: /new admin|nouvel admin/i }).click();
  await page.getByRole('dialog').getByRole('radio').first().check();
  await page.locator('#adm-tenant').selectOption(String(tenantId));
  await page.locator('#adm-username').fill(username);
  await page.locator('#adm-fname').fill('Alice');
  await page.locator('#adm-lname').fill('Chen');
  await page.locator('#adm-email').fill(`${username}@example.com`);
  const created = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/admins/tenant') && response.request().method() === 'POST',
  );
  await page.locator('form button[type="submit"]').click();
  const createResponse = await created;
  expect(createResponse.status()).toBe(201);
  const { adminId } = await createResponse.json();

  const credentialsButton = page.getByTestId('admin-detail-credentials');
  const token = page.getByTestId('admin-onboarding-token');
  const code = page.getByTestId('admin-onboarding-challenge');
  const boundWarning = page.getByTestId('admin-onboarding-bound-warning');
  const qrButton = page.getByRole('button', { name: /qr/i });
  const detailUrl = `/admins?adminId=${adminId}`;

  await page.goto(detailUrl);
  await credentialsButton.click();
  await expect(token).toBeVisible();
  await expect(qrButton).toBeVisible();
  await expect(boundWarning).toBeHidden();

  const material = await page.request.get(`/api/v1/admins/${adminId}/onboarding`, { headers });
  const { enrollmentId, enrollmentProofToken, enrollmentChallenge } = await material.json();
  await expect(token).toContainText(enrollmentProofToken);
  await expect(code).toHaveText(String(enrollmentChallenge));

  const form = { enrollmentId: String(enrollmentId), enrollmentProofToken };
  const bind = await context.request.post(`${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/bind`, {
    form,
  });
  expect(await bind.text()).toContain('demo-device-bind-success');

  await page.goto(detailUrl);
  await credentialsButton.click();
  await expect(code).toHaveText(String(enrollmentChallenge));
  await expect(boundWarning).toBeVisible();
  await expect(token).toBeHidden();
  await expect(qrButton).toBeHidden();

  const verify = await context.request.post(
    `${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/verify`,
    { form: { ...form, challengeResponse: String(enrollmentChallenge) } },
  );
  expect(await verify.text()).toContain('demo-device-verify-success');

  await page.goto(detailUrl);
  await expect(page.getByRole('dialog')).toContainText(username);
  await expect(credentialsButton).toBeHidden();
  const qr = await page.request.get(`/api/v1/admins/${adminId}/onboarding/qrcode`, { headers });
  expect(qr.status()).toBe(409);

  await assertAdminCanLogIn(page, context, username, enrollmentId);
});
