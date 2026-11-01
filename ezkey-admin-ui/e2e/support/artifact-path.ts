import fs from 'node:fs';
import path from 'node:path';

import type { TestInfo } from '@playwright/test';

/**
 * Resolve a durable screenshot/artifact path for Playwright smokes.
 *
 * Prefer `EZKEY_PLAYWRIGHT_ARTIFACTS_DIR`, else the existing
 * `EZKEY_BROWSER_TEST_RESULTS_DIR` (quality-gate / run-ui-tests), else
 * Playwright's per-test `testInfo.outputPath`. Creates the directory when using
 * an env-configured root so hosts without `/opt/cursor/artifacts` (e.g. macOS
 * quality-gate) do not fail with ENOENT. Refs #915.
 */
export function playwrightArtifactPath(testInfo: TestInfo, filename: string): string {
  const configured =
    process.env.EZKEY_PLAYWRIGHT_ARTIFACTS_DIR?.trim() ||
    process.env.EZKEY_BROWSER_TEST_RESULTS_DIR?.trim();
  if (configured) {
    fs.mkdirSync(configured, { recursive: true });
    return path.join(configured, filename);
  }
  return testInfo.outputPath(filename);
}
