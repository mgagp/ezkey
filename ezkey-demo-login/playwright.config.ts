import { defineConfig } from '@playwright/test';

// End-to-end matrix against a running clean-start stack (see docs/e2e/README.md).
export default defineConfig({
  testDir: './e2e',
  globalSetup: './e2e/global-setup.ts',
  fullyParallel: false,
  workers: 1,
  timeout: 120_000,
  expect: { timeout: 15_000 },
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: process.env.E2E_APP_URL ?? 'http://localhost:18082',
    viewport: { width: 900, height: 760 },
    trace: 'retain-on-failure',
  },
});
