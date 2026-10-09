#!/usr/bin/env node
/**
 * Slim REPORT.md + summary.json for quality-gate.
 * Cloud agent tooling; Git Bash best-effort, untested on macOS.
 */

import { existsSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

const args = parseArgs(process.argv.slice(2));
const runDir = args.runDir;
const tip = args.tip || '';
const short = args.short || '';
const totalMs = Number(args.totalMs || 0);
const churnMin = Number(args.churnMin || 5);
const red = args.red === '1';
const amber = args.amber === '1';
const haInvalid = args.haInvalid === '1';
const composeRetry = args.composeRetry === '1';
const shedlockRestored = args.shedlockRestored === '1';

const phases = [];
for (const line of readFileSync(join(runDir, 'phases.tsv'), 'utf8').split(/\r?\n/)) {
  if (!line.trim()) continue;
  const p = line.split('\t');
  while (p.length < 7) p.push('');
  phases.push({
    name: p[0],
    verdict: p[1],
    exitCode: p[2],
    durationMs: Number(p[3] || 0),
    logPath: p[4],
    counts: p[5],
    note: p[6],
  });
}

const health = loadJson(join(runDir, 'health', 'health.json')) || {};
let overall = 'GO';
if (red) overall = 'NO-GO';
else if (amber || haInvalid || health.verdict === 'AMBER') overall = 'GO with reservations';

const findings = [];
for (const p of phases) {
  if (p.verdict === 'AMBER' || p.verdict === 'RED') {
    findings.push({
      phase: p.name,
      verdict: p.verdict,
      note: p.note,
      classification: classify(p),
    });
  }
}
if (haInvalid) {
  findings.push({
    phase: 'replicas',
    verdict: 'AMBER',
    note: 'HA invalid — replica dead/unhealthy, restart delta, or elective restore failed',
    classification: 'environment',
  });
} else if (shedlockRestored) {
  findings.push({
    phase: 'elective-tests',
    verdict: 'GREEN',
    note: 'expected: ShedLockDistributedTest kill; gate docker-start restored',
    classification: 'expected (ShedLock)',
  });
}
const extraPath = join(runDir, 'findings-extra.txt');
if (existsSync(extraPath)) {
  for (const line of readFileSync(extraPath, 'utf8').split(/\r?\n/)) {
    if (!line.trim()) continue;
    findings.push({
      phase: 'extra',
      verdict: 'NOTE',
      note: line.trim(),
      classification: line.includes('OOM') ? 'environment' : 'test-hygiene',
    });
  }
}

const summary = {
  keyword: 'quality-gate',
  tipSha: tip,
  tipShortSha: short,
  generatedAtUtc: new Date().toISOString(),
  stackMode: 'HA + JavaMelody + gate memory overlay',
  churnMinutes: churnMin,
  totalDurationMs: totalMs,
  totalDurationHuman: human(totalMs),
  overallVerdict: overall,
  haInvalid,
  shedlockRestored,
  composeRetryFired: composeRetry,
  phases,
  findings,
  healthVerdict: health.verdict || null,
  secretCheck: (health.secretFindings || []).some((f) => f.severity === 'RED')
    ? 'RED'
    : 'GREEN',
  runDir,
};

writeFileSync(join(runDir, 'summary.json'), `${JSON.stringify(summary, null, 2)}\n`, 'utf8');

const lines = [
  '# Quality gate report',
  '',
  `- **Overall:** **${overall}**`,
  `- **HA invalid:** ${haInvalid}`,
  `- **Tip SHA:** \`${tip}\``,
  `- **Duration:** ${human(totalMs)}`,
  `- **ShedLock restore:** ${shedlockRestored}`,
  `- **#747 compose retry:** ${composeRetry}`,
  '',
  '## Phase table',
  '',
  '| Phase | Verdict | Duration | Counts | Notes |',
  '| --- | --- | --- | --- | --- |',
];
for (const p of phases) {
  lines.push(
    `| ${p.name} | ${p.verdict} | ${human(p.durationMs)} | ${p.counts} | ${p.note.replace(/\|/g, '/')} |`,
  );
}
if (findings.length) {
  lines.push('', '## Findings', '');
  lines.push('| Phase | Verdict | Classification | Note |', '| --- | --- | --- | --- |');
  for (const f of findings) {
    lines.push(
      `| ${f.phase} | ${f.verdict} | ${f.classification} | ${String(f.note).replace(/\|/g, '/')} |`,
    );
  }
}
lines.push('', '## Links', '');
lines.push(`- Health: \`health/HEALTH.md\` (verdict ${health.verdict || 'n/a'})`);
lines.push(`- Memory samples: \`mem-samples.tsv\``);
lines.push(`- Restarts: \`restarts.tsv\``);
lines.push(`- Summary: \`summary.json\``);
lines.push('');
writeFileSync(join(runDir, 'REPORT.md'), `${lines.join('\n')}\n`, 'utf8');
console.log(`summary: ${join(runDir, 'summary.json')}`);
console.log(`report: ${join(runDir, 'REPORT.md')}`);
console.log(`overall: ${overall}`);

function classify(p) {
  const n = p.note || '';
  if (n.includes('#747')) return 'product bug (#747)';
  if (n.includes('expected: ShedLock') || n.includes('ShedLockDistributedTest')) {
    return 'expected (ShedLock)';
  }
  if (n.includes('HA invalid') || n.includes('OOM') || n.includes('MemTotal')) {
    return 'environment';
  }
  if (p.verdict === 'RED') return 'unclassified';
  return 'reservation';
}

function human(ms) {
  const sec = Math.floor(Number(ms) / 1000);
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return m > 0 ? `${m}m${String(s).padStart(2, '0')}s` : `${s}s`;
}

function loadJson(p) {
  if (!existsSync(p)) return null;
  try {
    return JSON.parse(readFileSync(p, 'utf8'));
  } catch {
    return null;
  }
}

function parseArgs(argv) {
  const out = {};
  const map = {
    '--run-dir': 'runDir',
    '--tip': 'tip',
    '--short': 'short',
    '--total-ms': 'totalMs',
    '--churn-min': 'churnMin',
    '--red': 'red',
    '--amber': 'amber',
    '--ha-invalid': 'haInvalid',
    '--compose-retry': 'composeRetry',
    '--shedlock-restored': 'shedlockRestored',
  };
  for (let i = 0; i < argv.length; i += 1) {
    const k = argv[i];
    if (map[k]) {
      out[map[k]] = argv[i + 1];
      i += 1;
    }
  }
  return out;
}
