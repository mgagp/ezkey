/// <reference types="node" />
import { defineConfig, devices } from '@playwright/test';

const adminUiUrl = process.env.EZKEY_ADMIN_UI_URL ?? 'http://127.0.0.1:4173';
const skipWebServer = process.env.PLAYWRIGHT_SKIP_WEBSERVER === '1';

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  workers: 1,
  timeout: 90_000,
  expect: {
    timeout: 15_000,
  },
  outputDir: 'test-results/browser/artifacts',
  reporter: [
    ['list', { printSteps: true }],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
  ],
  use: {
    baseURL: adminUiUrl,
    headless: process.env.PLAYWRIGHT_HEADED !== '1',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: process.env.PLAYWRIGHT_CAPTURE_VIDEO === '1' ? 'retain-on-failure' : 'off',
  },
  projects: [
    {
      // Device-backed suite: clean-start stack + pre-seeded Demo Device.
      name: 'chromium',
      testIgnore: 'walks/**',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
    {
      // Walk Gate walks against a mocked Admin API (Vite only, no stack): `npm run test:walk`.
      name: 'walk-mocked',
      testMatch: 'walks/**/*.walk.spec.ts',
      use: {
        ...devices['Desktop Chrome'],
      },
    },
  ],
  webServer: skipWebServer
    ? undefined
    : {
        command: 'npm run dev -- --host 127.0.0.1 --port 4173',
        url: adminUiUrl,
        reuseExistingServer: true,
        timeout: 120_000,
      },
});
