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

/** Creates a tenant through the API, so the create dialog has a tenant to pick. */
async function createTenant(page: Page, headers: Record<string, string>, suffix: number): Promise<number> {
  const tenant = await page.request.post('/api/v1/tenants', {
    headers,
    data: { tenantName: `E2E onboarding ${suffix}`, tenantDescription: 'Playwright' },
  });
  expect(tenant.ok()).toBe(true);
  return (await tenant.json()).tenantId;
}

/** Fills the create dialog for a Tenant Admin; the caller picks the onboarding mode first. */
async function fillTenantAdminForm(page: Page, tenantId: number, username: string): Promise<void> {
  await page.getByRole('dialog').getByRole('radio').first().check();
  await page.locator('#adm-tenant').selectOption(String(tenantId));
  await page.locator('#adm-username').fill(username);
  await page.locator('#adm-fname').fill('Alice');
  await page.locator('#adm-lname').fill('Chen');
  await page.locator('#adm-email').fill(`${username}@example.com`);
}

/** Submits the create form and returns the new adminId from the API response. */
async function submitCreate(page: Page): Promise<number> {
  const created = page.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/admins/tenant') && response.request().method() === 'POST',
  );
  await page.locator('form button[type="submit"]').click();
  const createResponse = await created;
  expect(createResponse.status()).toBe(201);
  return (await createResponse.json()).adminId;
}

// #1266 + #1313: an IMMEDIATE administrator lands on their record. The record's bind card loads
// nothing secret until "Show QR" (each reveal is audited), shows the QR while CREATED and only the
// guarded code while BOUND, and disappears once VERIFIED. A Global Admin goes from the record to
// the enrollment and back; the enrollment record shows no bind material and no contact email.
test('@elective @device-backed admin onboarding: creation lands on the record with the bind card', async ({
  page,
  context,
}) => {
  await loginViaDemoDevice(page, context, 'approve');
  const authorization = await captureBearerToken(page);
  const headers = { Authorization: authorization };

  const suffix = Date.now();
  const username = `e2e-1313-${suffix}`;
  const tenantId = await createTenant(page, headers, suffix);

  const onboardingCalls: string[] = [];
  page.on('request', (request) => {
    if (request.url().includes('/onboarding')) onboardingCalls.push(request.url());
  });

  await page.goto('/admins');
  await page.getByTestId('admins-new-admin').click();
  await fillTenantAdminForm(page, tenantId, username);
  const adminId = await submitCreate(page);

  // P1: no intermediate screen; the record opens with the toast and the bind card.
  await expect(page).toHaveURL(new RegExp(`/admins\\?adminId=${adminId}$`));
  await expect(page.getByRole('alert').filter({ hasText: username })).toBeVisible();
  const dialog = page.getByRole('dialog');
  await expect(dialog).toContainText(username);
  const bindCard = page.getByTestId('admin-bind-card');
  const reveal = page.getByTestId('admin-bind-reveal');
  const token = page.getByTestId('admin-bind-token');
  const code = page.getByTestId('admin-bind-challenge');
  const qr = page.getByTestId('admin-bind-qr');
  const boundWarning = page.getByTestId('admin-bind-bound-warning');
  await expect(bindCard).toBeVisible();
  await expect(page.getByTestId('admin-bind-in-person')).toBeVisible();

  // P7: opening the record calls neither onboarding endpoint; "Show QR" does.
  expect(onboardingCalls).toEqual([]);
  await reveal.click();
  await expect(qr).toBeVisible();
  await expect(token).toBeVisible();
  await expect(boundWarning).toBeHidden();
  expect(onboardingCalls.some((url) => url.endsWith('/onboarding'))).toBe(true);
  expect(onboardingCalls.some((url) => url.endsWith('/onboarding/qrcode'))).toBe(true);

  const material = await page.request.get(`/api/v1/admins/${adminId}/onboarding`, { headers });
  const { enrollmentId, enrollmentProofToken, enrollmentChallenge } = await material.json();
  await expect(token).toContainText(enrollmentProofToken);
  await expect(code).toHaveText(String(enrollmentChallenge));

  // P3: Global Admin goes to the enrollment record and back.
  await page.getByTestId('admin-detail-enrollment-link').getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`/enrollments/${enrollmentId}$`));
  const owner = page.getByTestId('enrollment-admin-owner');
  await expect(owner).toContainText('Alice Chen');
  await expect(page.getByTestId('enrollment-qr-card')).toBeHidden();
  await expect(page.getByTestId('enrollment-token-card')).toBeHidden();
  await expect(page.getByTestId('enrollment-challenge-card')).toBeHidden();
  await expect(page.getByTestId('enrollment-edit-metadata')).toBeHidden();
  await expect(page.getByText(/contact email|courriel de contact/i)).toBeHidden();
  await owner.getByRole('link').click();
  await expect(page).toHaveURL(new RegExp(`/admins\\?adminId=${adminId}$`));
  await expect(page.getByRole('dialog')).toContainText(username);

  const form = { enrollmentId: String(enrollmentId), enrollmentProofToken };
  const bind = await context.request.post(`${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/bind`, {
    form,
  });
  expect(await bind.text()).toContain('demo-device-bind-success');

  const detailUrl = `/admins?adminId=${adminId}`;
  await page.goto(detailUrl);
  await reveal.click();
  await expect(code).toHaveText(String(enrollmentChallenge));
  await expect(boundWarning).toBeVisible();
  await expect(token).toBeHidden();
  await expect(qr).toBeHidden();

  const verify = await context.request.post(
    `${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/verify`,
    { form: { ...form, challengeResponse: String(enrollmentChallenge) } },
  );
  expect(await verify.text()).toContain('demo-device-verify-success');

  await page.goto(detailUrl);
  await expect(page.getByRole('dialog')).toContainText(username);
  await expect(bindCard).toBeHidden();
  const qrAfterVerify = await page.request.get(`/api/v1/admins/${adminId}/onboarding/qrcode`, { headers });
  expect(qrAfterVerify.status()).toBe(409);

  await assertAdminCanLogIn(page, context, username, enrollmentId);
});

// #1313 P2 + P6: the activation code is shown once behind the saved-confirmation gate, then the
// operator lands on a pending record with the code's expiry, no enrollment link and no bind card.
test('@elective @device-backed admin onboarding: activation code lands on a pending record', async ({
  page,
  context,
}) => {
  await loginViaDemoDevice(page, context, 'approve');
  const authorization = await captureBearerToken(page);
  const headers = { Authorization: authorization };

  const suffix = Date.now();
  const username = `e2e-1313-code-${suffix}`;
  const tenantId = await createTenant(page, headers, suffix);

  await page.goto('/admins');
  await page.getByTestId('admins-new-admin').click();
  await page.locator('#adm-onboarding-mode').selectOption('ACTIVATION_CODE');
  await fillTenantAdminForm(page, tenantId, username);
  const adminId = await submitCreate(page);

  const done = page.getByRole('button', { name: /^(done|terminé)$/i });
  await expect(done).toBeDisabled();
  await page.locator('#activation-code-saved').check();
  await done.click();

  await expect(page).toHaveURL(new RegExp(`/admins\\?adminId=${adminId}$`));
  await expect(page.getByRole('dialog')).toContainText(username);
  await expect(page.getByTestId('admin-detail-activation-code-expiry')).not.toHaveText('');
  await expect(page.getByTestId('admin-detail-enrollment-link')).toBeHidden();
  await expect(page.getByTestId('admin-bind-card')).toBeHidden();
});
