import { expect, test, type Page } from '@playwright/test';
import { loginViaDemoDevice } from './support/auth-flow';
import { testEnv } from './support/test-env';

/** Reads the bearer token the SPA sends, so the spec can set up data through the Admin API. */
async function captureBearerToken(page: Page): Promise<string> {
  const request = page.waitForRequest(
    (candidate) =>
      candidate.url().includes('/api/v1/') && candidate.headers().authorization != null,
  );
  await page.goto('/enrollments');
  return (await request).headers().authorization;
}

// #1180: proof token and QR while CREATED, code while CREATED or BOUND with a guard at BOUND,
// no public keys at any step.
test('@elective @device-backed enrollment detail shows bind material only while useful', async ({
  page,
  context,
}) => {
  await loginViaDemoDevice(page, context, 'approve');
  const authorization = await captureBearerToken(page);
  const headers = { Authorization: authorization };

  const suffix = Date.now();
  const integration = await page.request.post('/api/v1/integrations', {
    headers,
    data: { code: `e2e-1180-${suffix}`, name: 'E2E bind material', description: 'Playwright' },
  });
  expect(integration.ok()).toBe(true);
  const enrollment = await page.request.post('/api/v1/enrollments', {
    headers,
    data: {
      integrationId: (await integration.json()).id,
      name: 'Alice Chen',
      authAttemptChallengeRequired: false,
      userIdentifier: `alice-${suffix}`,
    },
  });
  expect(enrollment.ok()).toBe(true);
  const { enrollmentId, enrollmentChallenge } = await enrollment.json();
  const details = await page.request.get(`/api/v1/enrollments/${enrollmentId}`, { headers });
  const { enrollmentProofToken } = await details.json();

  const tokenCard = page.getByTestId('enrollment-token-card');
  const qrCard = page.getByTestId('enrollment-qr-card');
  const code = page.getByTestId('enrollment-challenge-code');
  const boundWarning = page.getByTestId('enrollment-bound-warning');

  await page.goto(`/enrollments/${enrollmentId}`);
  await expect(tokenCard).toBeVisible();
  await expect(qrCard).toBeVisible();
  await expect(code).toHaveText(String(enrollmentChallenge));
  await expect(boundWarning).toBeHidden();
  await expect(page.getByText(/public key/i)).toHaveCount(0);

  // #1206 step 5: before bind, the operator can give the invitation an address, and nothing else.
  const editEmail = page.getByRole('button', { name: /edit contact email|modifier l'e-mail de contact/i });
  await editEmail.click();
  await expect(page.getByTestId('enrollment-edit-email-only-hint')).toBeVisible();
  await expect(page.locator('#edit-name')).toHaveCount(0);
  await page.locator('#edit-email').fill(`alice-${suffix}@example.com`);
  await page.getByRole('dialog').getByRole('button', { name: /save|enregistrer/i }).click();
  await expect(page.getByRole('dialog')).toBeHidden();
  const updated = await page.request.get(`/api/v1/enrollments/${enrollmentId}`, { headers });
  expect((await updated.json()).contactEmail).toBe(`alice-${suffix}@example.com`);

  const form = { enrollmentId: String(enrollmentId), enrollmentProofToken };
  const bind = await context.request.post(`${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/bind`, {
    form,
  });
  expect(await bind.text()).toContain('demo-device-bind-success');

  await page.reload();
  await expect(code).toHaveText(String(enrollmentChallenge));
  await expect(boundWarning).toBeVisible();
  await expect(editEmail).toBeHidden();
  await expect(tokenCard).toBeHidden();
  await expect(qrCard).toBeHidden();

  const verify = await context.request.post(
    `${testEnv.demoDeviceUrl}/phone/ezkey/enrollment/verify`,
    { form: { ...form, challengeResponse: String(enrollmentChallenge) } },
  );
  expect(await verify.text()).toContain('demo-device-verify-success');

  await page.reload();
  await expect(page.getByTestId('enrollment-challenge-card')).toBeHidden();
  await expect(tokenCard).toBeHidden();
  await expect(qrCard).toBeHidden();
  await expect(page.getByText(/public key/i)).toHaveCount(0);
});
