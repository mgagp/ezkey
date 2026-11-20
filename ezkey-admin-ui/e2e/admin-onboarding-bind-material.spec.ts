import { expect, test, type Page } from '@playwright/test';
import { loginAsEnrolledAdmin, loginViaDemoDevice } from './support/auth-flow';
import { testEnv } from './support/test-env';

/**
 * Opens `/admins` and reads the bearer token the SPA sends, so the spec can set up data and probe
 * the Admin API as the signed-in administrator.
 */
async function captureBearerToken(page: Page): Promise<string> {
  const request = page.waitForRequest(
    (candidate) =>
      candidate.url().includes('/api/v1/') && candidate.headers().authorization != null,
  );
  await page.goto('/admins');
  return (await request).headers().authorization;
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
// The new Tenant Admin then signs in through the UI and sees a colleague's record without the
// enrollment link (P4).
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
  await expect(page).toHaveURL(new RegExp(`/admins/${adminId}$`));
  await expect(page.getByRole('alert').filter({ hasText: username })).toBeVisible();
  const record = page.getByTestId('admin-record');
  await expect(record).toContainText(username);
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
  await expect(page).toHaveURL(new RegExp(`/admins/${adminId}$`));
  await expect(record).toContainText(username);

  const form = { enrollmentId: String(enrollmentId), enrollmentProofToken };
  const bind = await context.request.post(`${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/bind`, {
    form,
  });
  expect(await bind.text()).toContain('demo-device-bind-success');

  // Reload the record page after the bind.
  const detailUrl = `/admins/${adminId}`;
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
  await expect(record).toContainText(username);
  await expect(bindCard).toBeHidden();
  const qrAfterVerify = await page.request.get(`/api/v1/admins/${adminId}/onboarding/qrcode`, { headers });
  expect(qrAfterVerify.status()).toBe(409);

  // P4: the new Tenant Admin signs in, creates a colleague and lands on the colleague's record:
  // bind card, no enrollment link; the enrollment record itself answers 403 to a Tenant Admin.
  const tenantPage = await context.newPage();
  await loginAsEnrolledAdmin(tenantPage, context, username, enrollmentId);
  const tenantHeaders = { Authorization: await captureBearerToken(tenantPage) };

  await tenantPage.getByTestId('admins-new-admin').click();
  await expect(tenantPage.locator('#adm-tenant')).toHaveCount(0);
  const colleague = `${username}-b`;
  await tenantPage.locator('#adm-username').fill(colleague);
  await tenantPage.locator('#adm-fname').fill('Bob');
  await tenantPage.locator('#adm-lname').fill('Martin');
  await tenantPage.locator('#adm-email').fill(`${colleague}@example.com`);
  const colleagueId = await submitCreate(tenantPage);

  await expect(tenantPage).toHaveURL(new RegExp(`/admins/${colleagueId}$`));
  await expect(tenantPage.getByTestId('admin-bind-card')).toBeVisible();
  await expect(tenantPage.getByTestId('admin-detail-enrollment-link')).toHaveCount(0);
  await tenantPage.getByTestId('admin-bind-reveal').click();
  await expect(tenantPage.getByTestId('admin-bind-qr')).toBeVisible();

  const colleagueRecord = await tenantPage.request.get(`/api/v1/admins/${colleagueId}`, {
    headers: tenantHeaders,
  });
  const colleagueEnrollmentId = (await colleagueRecord.json()).enrollmentId;
  expect(colleagueEnrollmentId).toBeTruthy();
  const enrollmentAsTenant = await tenantPage.request.get(
    `/api/v1/enrollments/${colleagueEnrollmentId}`,
    { headers: tenantHeaders },
  );
  expect(enrollmentAsTenant.status()).toBe(403);

  // #1399: deactivate and reactivate from the record page (Global Admin).
  await page.goto(detailUrl);
  const record2 = page.getByTestId('admin-record');
  await record2.getByRole('button', { name: 'Deactivate' }).click();
  await page.locator('#deactivate-admin-reason').fill('E2E record page deactivation check');
  await page.getByRole('dialog').getByRole('button', { name: 'Deactivate' }).click();
  await expect(record2).toContainText('Inactive');
  await page.locator('#activate-admin-reason').fill('E2E record page reactivation check');
  await record2.getByRole('button', { name: 'Activate' }).click();
  await expect(record2.getByRole('button', { name: 'Deactivate' })).toBeVisible();
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

  await expect(page).toHaveURL(new RegExp(`/admins/${adminId}$`));
  await expect(page.getByTestId('admin-record')).toContainText(username);
  // The code step already confirmed the creation: no toast repeats it.
  await expect(page.getByRole('alert').filter({ hasText: username })).toHaveCount(0);
  await expect(page.getByTestId('admin-detail-activation-code-expiry')).not.toHaveText('');
  await expect(page.getByTestId('admin-detail-enrollment-link')).toBeHidden();
  await expect(page.getByTestId('admin-bind-card')).toBeHidden();

  // #1399: the record's dialogs open from the page. Edit the profile, then re-issue the code.
  await page.getByRole('button', { name: 'Edit profile' }).click();
  await page.locator('#adm-edit-first-name').fill('Alicia');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(page.getByTestId('admin-record')).toContainText('Alicia Chen');

  await page.getByRole('button', { name: 'Re-issue activation code' }).click();
  const reissue = page.getByRole('dialog');
  await reissue.getByRole('button', { name: 'Re-issue activation code' }).click();
  // The footer button (the header's X shares the "Close" name).
  const close = reissue.getByRole('button', { name: 'Close' }).last();
  await expect(close).toBeDisabled();
  await page.locator('#reissued-activation-code-saved').check();
  await close.click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page).toHaveURL(new RegExp(`/admins/${adminId}$`));
});

// #1368: the single onboarding path, end to end. A Global Admin provisions a Tenant Admin with an
// activation code; that person activates on /login, sees only the device-bind step (no other
// option), binds the Demo Device and signs in with passwordless MFA.
test('@elective @device-backed activation code: activate, enroll a device, sign in', async ({
  page,
  context,
}) => {
  await loginViaDemoDevice(page, context, 'approve');
  const headers = { Authorization: await captureBearerToken(page) };

  const suffix = Date.now();
  const username = `e2e-1368-${suffix}`;
  const tenantId = await createTenant(page, headers, suffix);
  const created = await page.request.post('/api/v1/admins/tenant', {
    headers,
    data: {
      username,
      firstName: 'Dana',
      lastName: 'Roy',
      email: `${username}@example.com`,
      tenantId,
      onboardingMode: 'ACTIVATION_CODE',
    },
  });
  expect(created.status()).toBe(201);
  const { activationCode } = await created.json();
  expect(activationCode).toMatch(/^ezkey_activation_/);

  // A fresh tab holds no session: this is the invited person on the login page.
  const invitee = await context.newPage();
  await invitee.goto('/login');
  await invitee.getByTestId('login-activation-link').click();
  await invitee.locator('#activation-code').fill(activationCode);
  const activated = invitee.waitForResponse(
    (response) =>
      response.url().includes('/api/v1/admin/auth/activate') &&
      response.request().method() === 'POST',
  );
  await invitee.getByTestId('login-activation-form').locator('button[type="submit"]').click();
  const { enrollmentId, enrollmentProofToken, enrollmentChallenge } = await (
    await activated
  ).json();

  // One path: the bind step shows at once, with nothing else to choose.
  await expect(invitee.getByTestId('login-activation-success')).toBeVisible();
  await expect(invitee.getByTestId('login-activation-fork')).toHaveCount(0);
  await expect(invitee.getByTestId('login-activation-explore-temp')).toHaveCount(0);
  await expect(invitee.getByAltText(/QR/i)).toBeVisible();
  await expect(invitee.getByText(String(enrollmentChallenge), { exact: true })).toBeVisible();

  const form = { enrollmentId: String(enrollmentId), enrollmentProofToken };
  const bind = await context.request.post(`${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/bind`, {
    form,
  });
  expect(await bind.text()).toContain('demo-device-bind-success');
  const verify = await context.request.post(
    `${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/verify`,
    { form: { ...form, challengeResponse: String(enrollmentChallenge) } },
  );
  expect(await verify.text()).toContain('demo-device-verify-success');

  await invitee.close();
  const signIn = await context.newPage();
  await loginAsEnrolledAdmin(signIn, context, username, enrollmentId);
  await expect(signIn.getByTestId('temporary-session-banner')).toHaveCount(0);
});
