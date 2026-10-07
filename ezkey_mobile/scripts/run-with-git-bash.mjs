#!/usr/bin/env node
/**
 * Run a Bash script with a reliable Bash on Windows (Git for Windows), never
 * the WSL shim at System32\\bash.exe that yarn/cmd often resolve as `bash`.
 *
 * Usage (from ezkey_mobile/):
 *   node scripts/run-with-git-bash.mjs scripts/check-third-party-licenses-ci.sh
 *   node scripts/run-with-git-bash.mjs scripts/build-install-debug-clean.sh --build-only
 *
 * Override: EZKEY_GIT_BASH=/path/to/bash.exe
 * macOS/Linux: uses `bash` from PATH.
 */
import {spawnSync} from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const mobileRoot = path.resolve(__dirname, '..');

function isWindows() {
  return process.platform === 'win32' || os.type().startsWith('Windows');
}

function existsExecutable(candidate) {
  if (!candidate) {
    return false;
  }
  try {
    fs.accessSync(candidate, fs.constants.F_OK);
    return true;
  } catch {
    return false;
  }
}

function isWslSystem32Bash(candidate) {
  if (!candidate) {
    return false;
  }
  const normalized = candidate.replace(/\//g, '\\').toLowerCase();
  return (
    normalized.endsWith('\\system32\\bash.exe') ||
    normalized.includes('\\windows\\system32\\bash.exe')
  );
}

function resolveBash() {
  const override = process.env.EZKEY_GIT_BASH;
  if (override && existsExecutable(override)) {
    if (isWslSystem32Bash(override)) {
      console.error(
        'ezkey_mobile: EZKEY_GIT_BASH points at WSL System32 bash.exe; use Git for Windows instead:',
      );
      console.error('  C:\\Program Files\\Git\\bin\\bash.exe');
      process.exit(127);
    }
    return override;
  }

  if (!isWindows()) {
    return 'bash';
  }

  const candidates = [
    process.env.PROGRAMFILES
      ? path.join(process.env.PROGRAMFILES, 'Git', 'bin', 'bash.exe')
      : null,
    'C:\\Program Files\\Git\\bin\\bash.exe',
    process.env['PROGRAMFILES(X86)']
      ? path.join(process.env['PROGRAMFILES(X86)'], 'Git', 'bin', 'bash.exe')
      : null,
    'C:\\Program Files (x86)\\Git\\bin\\bash.exe',
    process.env.LOCALAPPDATA
      ? path.join(process.env.LOCALAPPDATA, 'Programs', 'Git', 'bin', 'bash.exe')
      : null,
  ].filter(Boolean);

  for (const candidate of candidates) {
    if (existsExecutable(candidate)) {
      return candidate;
    }
  }

  console.error(
    'ezkey_mobile: Git Bash not found. Install Git for Windows, or set EZKEY_GIT_BASH.',
  );
  console.error('  Expected: C:\\Program Files\\Git\\bin\\bash.exe');
  console.error(
    '  Do not use C:\\Windows\\System32\\bash.exe (WSL) for these scripts.',
  );
  process.exit(127);
}

const scriptArg = process.argv[2];
if (!scriptArg) {
  console.error(
    'Usage: node scripts/run-with-git-bash.mjs <script.sh> [args...]',
  );
  process.exit(2);
}

const scriptPath = path.isAbsolute(scriptArg)
  ? scriptArg
  : path.resolve(process.cwd(), scriptArg);
const scriptArgs = process.argv.slice(3);
const bash = resolveBash();

const result = spawnSync(bash, [scriptPath, ...scriptArgs], {
  cwd: process.cwd() || mobileRoot,
  stdio: 'inherit',
  env: process.env,
  windowsHide: true,
});

if (result.error) {
  console.error(`ezkey_mobile: failed to spawn ${bash}: ${result.error.message}`);
  process.exit(127);
}

process.exit(result.status === null ? 1 : result.status);
