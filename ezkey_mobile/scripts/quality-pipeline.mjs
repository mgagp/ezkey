#!/usr/bin/env node
/**
 * Legacy alias — prefer `yarn doctor:curated` / `./scripts/mobile-doctor-curated.sh`.
 * Keyword: mobile-doctor-curated
 */

import {spawnSync} from 'node:child_process';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const forwarded = process.argv.slice(2).filter(arg => !arg.startsWith('--report-name='));

console.log(
  '[quality-pipeline] Delegating to mobile-doctor-curated (keyword: mobile-doctor-curated).',
);

const result = spawnSync(process.execPath, [path.join(scriptDir, 'mobile-doctor-curated.mjs'), ...forwarded], {
  cwd: path.resolve(scriptDir, '..'),
  stdio: 'inherit',
  shell: false,
});

process.exit(result.status ?? 1);
