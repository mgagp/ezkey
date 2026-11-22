// Screenshots for the ezkey.org community guided tour (960×540), taken from a real sign-in.
// Run on demand: npx playwright test e2e/tour-screens.spec.ts  (then convert to WebP, see docs/e2e/README.md).
import { test, expect } from '@playwright/test';
import { answerOnDevice, openOnDevice, provision, SHOTS } from './support';

test.use({ viewport: { width: 960, height: 540 } });

test('tour screens', async ({ page, browser }) => {
  const alice = provision().enrollments.alice;
  const device = await (await browser.newContext({ viewport: { width: 430, height: 860 } })).newPage();

  await page.goto('/');
  await page.getByTestId('login-username').fill('alice');
  await page.screenshot({ path: `${SHOTS}/tour-05-demo-login-form.png` });

  await page.getByTestId('login-submit').click();
  await expect(page.getByTestId('login-waiting')).toBeVisible();
  await page.waitForTimeout(2500);
  await page.screenshot({ path: `${SHOTS}/tour-06-demo-login-waiting.png` });

  const code = (await page.getByTestId('login-challenge').locator('p').nth(1).innerText()).trim();
  await openOnDevice(device, alice.enrollmentId);
  await answerOnDevice(device, true, code);
  await expect(page.getByTestId('home')).toBeVisible();
  await page.screenshot({ path: `${SHOTS}/tour-07-demo-login-signed-in.png` });
  await page.getByTestId('home-logout').click();
});
