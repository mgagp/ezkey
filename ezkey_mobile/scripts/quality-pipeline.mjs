#!/usr/bin/env node

import {existsSync} from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const rootDir = path.resolve(process.cwd());
const scriptArgs = process.argv.slice(2);
const reportNameArg = scriptArgs.find(arg => arg.startsWith('--report-name='));
const reportName = reportNameArg ? reportNameArg.slice('--report-name='.length) : 'quality-unified';

const run = (command, args, label) => {
  const result = spawnSync(command, args, {
    cwd: rootDir,
    stdio: 'inherit',
    shell: false,
  });
  const exitCode = result.status ?? 1;
  if (exitCode !== 0) {
    console.error(`[quality-pipeline] ${label} failed with exit code ${exitCode}`);
    process.exit(exitCode);
  }
};

run('node', ['scripts/semgrep-scan.mjs'], 'Semgrep scan');
run('node', ['scripts/detekt-scan.mjs'], 'Detekt scan');

const candidateInputs = [
  '.monitor/biome-report.json',
  '.monitor/semgrep-report.json',
  '.monitor/detekt.sarif',
  '.monitor/detekt-report.json',
];
const inputs = candidateInputs
  .map(relPath => ({relPath, absPath: path.join(rootDir, relPath)}))
  .filter(item => existsSync(item.absPath))
  .map(item => item.relPath);

if (inputs.length === 0) {
  console.error('[quality-pipeline] No quality report inputs found for consolidation.');
  process.exit(2);
}

const curatorArgs = [
  'scripts/code-quality-curator.mjs',
  `--inputs=${inputs.join(',')}`,
  `--report-name=${reportName}`,
  '--top=40',
];

const defaultSuppressionFile = '.monitor/code-quality-suppressions.json';
if (existsSync(path.join(rootDir, defaultSuppressionFile))) {
  curatorArgs.push(`--suppression-file=${defaultSuppressionFile}`);
}

run('node', curatorArgs, 'Curated consolidated report');

console.log('[quality-pipeline] Unified quality report generated.');
