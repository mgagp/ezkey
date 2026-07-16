#!/usr/bin/env node
/*
 * Lightweight dependency monitoring for ezkey_mobile.
 *
 * Goals:
 * - keep iteration cadence pragmatic
 * - separate actionable upgrades from ecosystem-gated deferred upgrades
 * - surface high-severity audit findings
 */

import {appendFileSync, existsSync, mkdirSync, readFileSync} from 'node:fs';
import path from 'node:path';
import {spawnSync} from 'node:child_process';

const rawArgs = process.argv.slice(2);
const args = new Set(rawArgs);
const strictMode = args.has('--strict');
const jsonMode = args.has('--json');
const historyMode = args.has('--history');

const historyFileArg = rawArgs.find(arg => arg.startsWith('--history-file='));
const historyFileRaw = historyFileArg ? historyFileArg.slice('--history-file='.length) : '.monitor/dependency-history.ndjson';

const rootDir = path.resolve(process.cwd());
const packageJsonPath = path.join(rootDir, 'package.json');

if (!existsSync(packageJsonPath)) {
  console.error('[deps-monitor] package.json not found. Run from ezkey_mobile/.');
  process.exit(2);
}

const packageJson = JSON.parse(readFileSync(packageJsonPath, 'utf8'));
const currentVersions = {
  ...(packageJson.dependencies ?? {}),
  ...(packageJson.devDependencies ?? {}),
};

const run = (command, commandArgs) => {
  const isWindows = process.platform === 'win32';
  const quote = value => {
    const str = String(value);
    if (!/[\s"^&|<>]/.test(str)) {
      return str;
    }
    return `"${str.replace(/"/g, '\\"')}"`;
  };

  const retriedResult = isWindows
    ? spawnSync(
        process.env.ComSpec || 'cmd.exe',
        ['/d', '/s', '/c', `${quote(command)} ${commandArgs.map(quote).join(' ')}`],
        {
          cwd: rootDir,
          encoding: 'utf8',
          shell: false,
        },
      )
    : spawnSync(command, commandArgs, {
        cwd: rootDir,
        encoding: 'utf8',
        shell: false,
      });

  const errorText = retriedResult.error
    ? String(retriedResult.error.message || retriedResult.error)
    : '';
  return {
    code: retriedResult.status ?? 1,
    stdout: retriedResult.stdout ?? '',
    stderr: retriedResult.stderr ?? '',
    error: errorText,
  };
};

const hasMajor = (range, major) => {
  if (!range) {
    return false;
  }
  const re = new RegExp(`(^|[^0-9])${major}([^0-9]|$)`);
  return re.test(String(range));
};

/** First numeric major from a semver or range string (e.g. "6.0.3" → 6, "^7.1.0" → 7). */
const parseMajor = version => {
  if (!version) {
    return null;
  }
  const match = String(version).match(/(\d+)/);
  return match ? Number(match[1]) : null;
};

const readJsonIfExists = relativePath => {
  const abs = path.join(rootDir, relativePath);
  if (!existsSync(abs)) {
    return null;
  }
  return JSON.parse(readFileSync(abs, 'utf8'));
};

const rnEslintConfig = readJsonIfExists('node_modules/@react-native/eslint-config/package.json');
const rnJestPreset = readJsonIfExists('node_modules/@react-native/jest-preset/package.json');

const eslint10GateOpen =
  !!rnEslintConfig && hasMajor(rnEslintConfig.peerDependencies?.eslint, 10);

const jest30GateOpen =
  !!rnJestPreset &&
  (hasMajor(rnJestPreset.dependencies?.['@jest/create-cache-key-function'], 30) ||
    hasMajor(rnJestPreset.dependencies?.['babel-jest'], 30) ||
    hasMajor(rnJestPreset.dependencies?.['jest-environment-node'], 30));

const ncu = run('npx', ['-y', 'npm-check-updates', '--jsonUpgraded']);

let upgrades = {};
let ncuError = null;
if (ncu.code === 0) {
  const raw = (ncu.stdout || '').trim();
  upgrades = raw ? JSON.parse(raw) : {};
} else {
  ncuError = (ncu.error || ncu.stderr || ncu.stdout || 'unknown npm-check-updates error').trim();
}

const audit = run('corepack', ['yarn', 'npm', 'audit', '--severity', 'high']);
const auditOutput = `${audit.stdout}\n${audit.stderr}`;
const auditNoSuggestions = /No audit suggestions/i.test(auditOutput);
const hasHighAuditFindings = audit.code !== 0 && !auditNoSuggestions;

const classifyUpgrade = (name, latest) => {
  const current = currentVersions[name] ?? '(not found)';

  if (name === 'eslint' && !eslint10GateOpen) {
    return {
      name,
      current,
      latest,
      status: 'deferred',
      reason:
        'React Native lint stack does not yet declare ESLint 10 compatibility.',
    };
  }

  if ((name === 'jest' || name === '@types/jest') && !jest30GateOpen) {
    return {
      name,
      current,
      latest,
      status: 'deferred',
      reason:
        'React Native jest preset/runtime remains on Jest 29 ecosystem in this baseline.',
    };
  }

  // TypeScript 7+ is an intentional monorepo deferral (Admin UI still on TS ~6).
  if (name === 'typescript') {
    const currentMajor = parseMajor(current);
    const latestMajor = parseMajor(latest);
    if (
      currentMajor != null &&
      latestMajor != null &&
      latestMajor > currentMajor &&
      latestMajor >= 7
    ) {
      return {
        name,
        current,
        latest,
        status: 'deferred',
        reason:
          'TypeScript 7+ is deferred until Admin UI and monorepo tooling align on the same major.',
      };
    }
  }

  return {
    name,
    current,
    latest,
    status: 'actionable',
    reason: 'No known ecosystem gate blocks this update.',
  };
};

const rows = Object.entries(upgrades)
  .map(([name, latest]) => classifyUpgrade(name, latest))
  .sort((a, b) => a.name.localeCompare(b.name));

const actionable = rows.filter(r => r.status === 'actionable');
const deferred = rows.filter(r => r.status === 'deferred');

const summary = {
  timestamp: new Date().toISOString(),
  rnVersion: currentVersions['react-native'] ?? 'unknown',
  eslint10GateOpen,
  jest30GateOpen,
  outdatedCount: rows.length,
  actionableCount: actionable.length,
  deferredCount: deferred.length,
  hasHighAuditFindings,
  ncuError,
  auditNoSuggestions,
};

if (historyMode) {
  const historyFilePath = path.isAbsolute(historyFileRaw)
    ? historyFileRaw
    : path.join(rootDir, historyFileRaw);
  const historyDir = path.dirname(historyFilePath);
  if (!existsSync(historyDir)) {
    mkdirSync(historyDir, {recursive: true});
  }

  const snapshot = {
    summary,
    actionable,
    deferred,
  };
  appendFileSync(historyFilePath, `${JSON.stringify(snapshot)}\n`, 'utf8');
}

if (jsonMode) {
  console.log(
    JSON.stringify(
      {
        summary,
        actionable,
        deferred,
      },
      null,
      2,
    ),
  );
} else {
  console.log('Dependency Monitoring Report (ezkey_mobile)');
  console.log('--------------------------------------------');
  console.log(`react-native baseline: ${summary.rnVersion}`);
  console.log(`eslint 10 gate open: ${summary.eslint10GateOpen ? 'yes' : 'no'}`);
  console.log(`jest 30 gate open: ${summary.jest30GateOpen ? 'yes' : 'no'}`);
  console.log(`high-severity audit findings: ${summary.hasHighAuditFindings ? 'yes' : 'no'}`);

  if (ncuError) {
    console.log('');
    console.log(`warning: npm-check-updates failed: ${ncuError}`);
  }

  console.log('');
  console.log(`outdated total: ${rows.length}`);
  console.log(`actionable now: ${actionable.length}`);
  console.log(`deferred by ecosystem gates: ${deferred.length}`);

  if (actionable.length > 0) {
    console.log('');
    console.log('Actionable updates:');
    actionable.forEach(item => {
      console.log(`- ${item.name}: ${item.current} -> ${item.latest}`);
    });
  }

  if (deferred.length > 0) {
    console.log('');
    console.log('Deferred updates:');
    deferred.forEach(item => {
      console.log(`- ${item.name}: ${item.current} -> ${item.latest}`);
      console.log(`  reason: ${item.reason}`);
    });
  }

  console.log('');
  console.log('Recommended cadence:');
  console.log('1. Run deps:monitor before starting an iteration.');
  console.log('2. Execute only actionable upgrades in isolated lots.');
  console.log('3. Keep deferred majors parked until ecosystem gates open.');
}

if (strictMode && (hasHighAuditFindings || actionable.length > 0 || ncuError)) {
  process.exit(1);
}
