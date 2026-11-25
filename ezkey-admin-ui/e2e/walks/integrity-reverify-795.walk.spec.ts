import { expect, test, type Route } from '@playwright/test';

import { playwrightArtifactPath } from '../support/artifact-path';
import { injectSession, mockAdminApi, type WalkLocale } from '../support/mock-admin-api';

/**
 * Interface gate for #795 n2 (mini-gate in the PR description, no separate WALK record):
 * "Re-verify" on the Integrity strip re-runs the window of the verdict it shows, never the
 * Verify tab's filter. A rolling 24 h verdict re-runs the 24 h ending now; a fixed period
 * re-runs exactly. Step names carry the gate row number; captures land next to the test output.
 */

const HOUR = 3_600_000;
const JOBS = '/audit-logs/integrity/jobs';

interface StartBody {
  type: string;
  from: string;
  to: string;
  trigger?: string;
}

const copy = {
  en: { reverify: 'Re-verify' },
  fr: { reverify: 'Revérifier' },
} satisfies Record<WalkLocale, unknown>;

test.use({ timezoneId: 'America/Toronto' });

for (const locale of ['en', 'fr'] as const) {
  test(`walk #795 re-verify runs the verdict window (${locale.toUpperCase()})`, async ({ context, page }, testInfo) => {
    const T = copy[locale];
    const shot = (name: string) =>
      page.screenshot({ path: playwrightArtifactPath(testInfo, `walk-795-${locale}-${name}.png`), fullPage: true });
    const check = (row: number, label: string, body: () => Promise<void>) => test.step(`#${row} ${label}`, body);

    // Glance job served by /current and /last-verdict. Starts as a fresh rolling 24 h auto-run
    // that ended 5 h ago, so the page does not auto-run on open.
    const ended = Date.now() - 5 * HOUR;
    let glance: Record<string, unknown> = {
      jobId: 'job-auto',
      type: 'VERIFY_CHAIN_RANGE',
      status: 'SUCCEEDED',
      trigger: 'AUTO',
      chainStatus: 'OK',
      intact: true,
      startedAt: new Date(ended - 1000).toISOString(),
      finishedAt: new Date(ended).toISOString(),
      scopeFrom: new Date(ended - 24 * HOUR).toISOString(),
      scopeTo: new Date(ended).toISOString(),
      totalCheckpoints: 4,
      validCheckpoints: 4,
      invalidCheckpoints: 0,
      archivedCheckpoints: 0,
      undeclaredGaps: 0,
    };
    const starts: StartBody[] = [];

    await page.setViewportSize({ width: 1280, height: 900 });
    await injectSession(context, locale);
    const api = await mockAdminApi(page, [
      ({ path }) => (path === `${JOBS}/current` || path === `${JOBS}/last-verdict` ? glance : undefined),
    ]);
    // POST …/jobs: record the requested window, then finish the job at once with that scope.
    await page.route(`**/api/v1${JOBS}`, async (route: Route) => {
      if (route.request().method() !== 'POST') return route.fallback();
      const body = route.request().postDataJSON() as StartBody;
      starts.push(body);
      const now = new Date().toISOString();
      glance = {
        ...glance,
        jobId: `job-${starts.length}`,
        trigger: body.trigger ?? 'MANUAL',
        startedAt: now,
        finishedAt: now,
        scopeFrom: body.from,
        scopeTo: body.to,
      };
      return route.fulfill({ status: 202, contentType: 'application/json', body: JSON.stringify({ jobId: glance.jobId }) });
    });

    await page.goto('/integrity');
    const strip = page.getByTestId('integrity-micro-dashboard');
    const reverify = page.getByTestId('integrity-micro-dashboard-reverify');
    await expect(strip).toHaveAttribute('data-tone', 'intact');
    await expect(reverify).toHaveText(T.reverify);
    expect(starts, 'no auto-run on a fresh verdict').toHaveLength(0);

    // Verify tab filter on "Yesterday": the n2 trap.
    await page.getByTestId('integrity-mode-verify').click();
    const preset = page.getByTestId('integrity-mode-panel-verify').locator('select').first();
    await preset.selectOption('yesterday');

    await check(1, 'rolling 24 h verdict + filter on Yesterday: Re-verify runs the 24 h ending now', async () => {
      const before = Date.now();
      await reverify.click();
      await expect.poll(() => starts.length).toBe(1);
      const after = Date.now();
      const [start] = starts;
      const to = Date.parse(start.to);
      const from = Date.parse(start.from);
      expect.soft(start.type).toBe('VERIFY_CHAIN_RANGE');
      expect.soft(start.trigger).toBe('MANUAL');
      expect.soft(to - from, 'window length').toBe(24 * HOUR);
      expect.soft(to, 'window ends now').toBeGreaterThanOrEqual(before - 1000);
      expect.soft(to, 'window ends now').toBeLessThanOrEqual(after + 1000);
    });
    await expect(strip).toHaveAttribute('data-tone', 'intact');
    await shot('01-rolling');

    await check(2, 'Re-verify again after a manual re-verify: still rolls to now', async () => {
      const before = Date.now();
      await expect(reverify).toBeEnabled();
      await reverify.click();
      await expect.poll(() => starts.length).toBe(2);
      const to = Date.parse(starts[1].to);
      expect.soft(to - Date.parse(starts[1].from), 'window length').toBe(24 * HOUR);
      expect.soft(to, 'window ends now').toBeGreaterThanOrEqual(before - 1000);
    });

    await check(3, '"Verify chain" on Yesterday, then Re-verify: same calendar day, exactly', async () => {
      await preset.selectOption('yesterday');
      await page.getByTestId('integrity-verify-chain').click();
      await expect.poll(() => starts.length).toBe(3);
      const day = starts[2];
      // Toronto calendar day: midnight to midnight (23, 24 or 25 h).
      expect.soft(Math.abs(Date.parse(day.to) - Date.parse(day.from) - 24 * HOUR)).toBeLessThanOrEqual(HOUR);
      await expect(reverify).toBeEnabled();
      await reverify.click();
      await expect.poll(() => starts.length).toBe(4);
      expect.soft({ from: starts[3].from, to: starts[3].to }).toEqual({ from: day.from, to: day.to });
      expect.soft(starts[3].trigger).toBe('MANUAL');
    });
    await expect(page.getByTestId('integrity-micro-dashboard-scope-mismatch')).toHaveCount(0);
    await shot('02-calendar-day');

    await testInfo.attach('unmocked-requests', { body: [...api.unmocked].join('\n') || '(none)', contentType: 'text/plain' });
  });
}
