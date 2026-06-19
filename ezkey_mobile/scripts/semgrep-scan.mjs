#!/usr/bin/env node

import {existsSync, mkdirSync, readFileSync} from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const rootDir = path.resolve(process.cwd());
const rulesFile = path.join(rootDir, 'semgrep', 'rules', 'mobile-security.yml');
const outputDir = path.join(rootDir, '.monitor');
const outputFile = path.join(outputDir, 'semgrep-report.json');

if (!existsSync(rulesFile)) {
  console.error('[semgrep-scan] Missing rules file:', rulesFile);
  process.exit(2);
}

mkdirSync(outputDir, {recursive: true});

const run = (command, args, options = {}) => {
  const result = spawnSync(command, args, {
    cwd: rootDir,
    stdio: 'inherit',
    shell: false,
    ...options,
  });
  return result.status ?? 1;
};

const commandExists = command => {
  const isWindows = process.platform === 'win32';
  const probe = isWindows
    ? spawnSync('where.exe', [command], {cwd: rootDir, stdio: 'ignore', shell: false})
    : spawnSync('which', [command], {cwd: rootDir, stdio: 'ignore', shell: false});
  return (probe.status ?? 1) === 0;
};

const semgrepArgs = [
  'scan',
  '--config',
  rulesFile,
  '--json',
  '--output',
  outputFile,
  '--exclude',
  '**/__tests__/**',
  '--exclude',
  'app/services/api/generated/**',
  'app',
  'android/app/src/main/java',
];

const semgrepDockerArgs = [
  'scan',
  '--config',
  '/src/semgrep/rules/mobile-security.yml',
  '--json',
  '--output',
  '/src/.monitor/semgrep-report.json',
  '--exclude',
  '**/__tests__/**',
  '--exclude',
  'app/services/api/generated/**',
  'app',
  'android/app/src/main/java',
];

let exitCode;

if (commandExists('semgrep')) {
  exitCode = run('semgrep', semgrepArgs);
} else if (commandExists('docker')) {
  const dockerArgs = [
    'run',
    '--rm',
    '-v',
    `${rootDir}:/src`,
    '-w',
    '/src',
    'semgrep/semgrep',
    'semgrep',
    ...semgrepDockerArgs,
  ];
  exitCode = run('docker', dockerArgs);
} else {
  console.error('[semgrep-scan] Neither semgrep CLI nor docker was found.');
  process.exit(2);
}

if (!existsSync(outputFile)) {
  console.error('[semgrep-scan] Scan finished without output file:', outputFile);
  process.exit(2);
}

try {
  const report = JSON.parse(readFileSync(outputFile, 'utf8'));
  const errors = Array.isArray(report?.errors) ? report.errors : [];
  const configError = errors.find(error => {
    const text = String(error?.message || '').toLowerCase();
    return text.includes('invalid yaml file') || text.includes('invalid configuration file');
  });
  if (configError) {
    console.error('[semgrep-scan] Invalid semgrep rules configuration:', configError.message);
    process.exit(2);
  }
} catch (error) {
  console.error('[semgrep-scan] Failed to parse report JSON:', outputFile);
  process.exit(2);
}

console.log(`[semgrep-scan] Report written to ${path.relative(rootDir, outputFile).replace(/\\/g, '/')}`);

if (exitCode !== 0 && exitCode !== 1) {
  process.exit(exitCode);
}
