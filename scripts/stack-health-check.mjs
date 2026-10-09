#!/usr/bin/env node
/**
 * stack-health-check — scan container logs, redact, score vs thresholds.
 * Invoked by scripts/stack-health-check.sh (Git Bash / Linux / macOS).
 */

import { execFileSync } from 'node:child_process';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(SCRIPT_DIR, '..');
const KEYWORD = 'stack-health-check';
const APPS = ['admin-api', 'auth-api', 'integration-api'];
const HA_API_REPLICAS = [
  'ezkey-admin-api-1',
  'ezkey-admin-api-2',
  'ezkey-auth-api-1',
  'ezkey-auth-api-2',
  'ezkey-integration-api-1',
  'ezkey-integration-api-2',
  'ezkey-crypto-api-ha',
];

const args = parseArgs(process.argv.slice(2));
const OUTPUT_DIR = args.outputDir || join(REPO_ROOT, 'logs', 'quality-gate', 'health');
const JM_DIR = args.jmDir || join(OUTPUT_DIR, 'javamelody');
const JM_OK = args.jmOk === '1' || args.jmOk === 'true';
const THRESHOLDS_PATH =
  args.thresholds || join(REPO_ROOT, 'config', 'quality-gate', 'thresholds.json');
const ALLOWLIST_PATH =
  args.allowlist || join(REPO_ROOT, 'config', 'quality-gate', 'log-allowlist.txt');
const COMPOSE_RETRY_FIRED =
  process.env.COMPOSE_RETRY_FIRED === '1' || process.env.COMPOSE_RETRY_FIRED === 'true';

main();

function main() {
  mkdirSync(OUTPUT_DIR, { recursive: true });
  const thresholds = loadJson(THRESHOLDS_PATH) || {};
  const allowlist = loadAllowlist(ALLOWLIST_PATH);
  const checks = [];

  const logScan = scanContainers();
  writeFileSync(
    join(OUTPUT_DIR, 'container-logs.json'),
    `${JSON.stringify(logScan, null, 2)}\n`,
    'utf8',
  );
  writeFileSync(join(OUTPUT_DIR, 'container-logs.md'), renderLogMd(logScan), 'utf8');

  // RED: secrets in raw logs (before redaction for scoring)
  const secretHits = logScan.secretHits || [];
  pushCheck(
    checks,
    'containers.secretsInRawLogs',
    secretHits.length,
    { amber: 1, red: 1 },
    secretHits.length
      ? `Secret patterns in raw logs: ${secretHits.length} (e.g. ${secretHits[0]?.service}: ${sanitize(secretHits[0]?.preview || '')})`
      : 'No secret patterns in raw container logs',
  );

  const jmPath = join(JM_DIR, 'javamelody.curated.json');
  const jmMetaPath = join(JM_DIR, 'raw', 'snapshot-meta.json');
  const jm = loadJson(jmPath);
  const jmMeta = loadJson(jmMetaPath);
  scoreJavaMelodyFreshness(jm, jmMeta, JM_OK, thresholds.javamelody || {}, checks);
  const jmScore = scoreJavaMelody(jm, thresholds.javamelody || {}, checks);

  const containers = scoreContainers(
    logScan,
    thresholds.containers || {},
    allowlist,
    checks,
  );

  const health = {
    keyword: KEYWORD,
    generatedAt: new Date().toISOString(),
    thresholdsPath: THRESHOLDS_PATH,
    note: thresholds.note || '',
    composeRetryFired: COMPOSE_RETRY_FIRED,
    javamelody: jmScore,
    containers,
    secretHits: secretHits.slice(0, 20),
    checks,
  };
  health.verdict = aggregateVerdict(checks);

  writeFileSync(join(OUTPUT_DIR, 'health.json'), `${JSON.stringify(health, null, 2)}\n`, 'utf8');
  writeFileSync(join(OUTPUT_DIR, 'HEALTH.md'), renderMarkdown(health), 'utf8');

  console.log(`${KEYWORD}: ${health.verdict}`);
  console.log(`JSON: ${join(OUTPUT_DIR, 'health.json')}`);
  console.log(`Markdown: ${join(OUTPUT_DIR, 'HEALTH.md')}`);
  if (health.verdict === 'RED') {
    process.exitCode = 1;
  }
}

/* ---------- redaction / signatures ---------- */

function sanitize(s) {
  let out = String(s ?? '');
  out = out.replace(/[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g, '<EMAIL>');
  out = out.replace(
    /\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\b/g,
    '<IPV4>',
  );
  out = out.replace(/\b(?:[0-9a-fA-F]{0,4}:){2,7}[0-9a-fA-F]{0,4}\b/g, '<IPV6>');
  out = out.replace(/\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/g, '<JWT>');
  out = out.replace(
    /"(password|token|secret|challenge|code|key|accessCode|username|user)"\s*:\s*"[^"]*"/gi,
    '"$1":"***REDACTED***"',
  );
  out = out.replace(
    /\b(password|passwd|pwd|token|bearer|api[_-]?key|secret|authorization|challenge|code|accessCode|username|user)\s*[=:]\s*\S+/gi,
    '$1=***REDACTED***',
  );
  out = out.replace(/Bearer\s+[A-Za-z0-9._~+/=-]+/gi, 'Bearer ***REDACTED***');
  out = out.replace(/\b[A-Za-z0-9_-]{20,}\b/g, '<TOKEN>');
  return out;
}

function signature(line) {
  let s = sanitize(line.trim());
  s = s.replace(/\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}:\d{2}(?:\.\d+)?Z?/g, '<TS>');
  s = s.replace(/\b\d{2}:\d{2}:\d{2}(?:\.\d+)?\b/g, '<TIME>');
  s = s.replace(
    /\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\b/gi,
    '<UUID>',
  );
  s = s.replace(/\b\d{5,}\b/g, '<N>');
  s = s.replace(/\s+/g, ' ');
  return s.slice(0, 240);
}

const SECRET_RAW_PATTERNS = [
  { name: 'email', re: /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/ },
  {
    name: 'ipv4',
    re: /\b(?:(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\.){3}(?:25[0-5]|2[0-4]\d|[01]?\d?\d)\b/,
  },
  { name: 'jwt', re: /\beyJ[\w-]+\.[\w-]+\.[\w-]+\b/ },
  {
    name: 'json-secret',
    re: /"(password|token|secret|challenge|code|key|accessCode)"\s*:\s*"[^"]+"/i,
  },
  {
    name: 'kv-secret',
    re: /\b(password|token|secret|challenge|accessCode)\s*[=:]\s*\S+/i,
  },
];

function detectSecrets(rawLine) {
  const hits = [];
  for (const p of SECRET_RAW_PATTERNS) {
    if (p.re.test(rawLine)) {
      hits.push(p.name);
    }
  }
  return hits;
}

/* ---------- container scan ---------- */

function scanContainers() {
  let names = [];
  try {
    const out = execFileSync(
      'docker',
      ['ps', '-a', '--filter', 'name=ezkey', '--format', '{{.Names}}'],
      { encoding: 'utf8' },
    );
    names = out.split(/\r?\n/).map((s) => s.trim()).filter(Boolean);
  } catch {
    names = [];
  }

  const services = [];
  const secretHits = [];
  const ERROR_HINT = /\b(ERROR|Exception|OutOfMemoryError|FATAL)\b/i;
  const WARN_HINT = /\bWARN(ING)?\b/i;

  for (const name of names) {
    let inspect = {};
    try {
      inspect = JSON.parse(execFileSync('docker', ['inspect', name], { encoding: 'utf8' }))[0];
    } catch {
      /* ignore */
    }
    const state = inspect.State || {};
    const health = (state.Health || {}).Status;
    const restarts = Number(inspect.RestartCount || 0);
    let oom = Boolean(state.OOMKilled);
    const exitCode = state.ExitCode;
    const status = state.Status;
    if (exitCode === 137) {
      oom = true;
    }

    let logs = '';
    try {
      logs = execFileSync('docker', ['logs', '--tail', '4000', name], {
        encoding: 'utf8',
        maxBuffer: 20 * 1024 * 1024,
      });
    } catch (exc) {
      logs = `<log-fetch-failed: ${exc.message || exc}>`;
    }

    const errC = new Map();
    const warnC = new Map();
    let errTotal = 0;
    let warnTotal = 0;
    for (const line of logs.split(/\r?\n/)) {
      if (!line) continue;
      const secrets = detectSecrets(line);
      if (secrets.length) {
        secretHits.push({
          service: name,
          kinds: secrets,
          preview: sanitize(line).slice(0, 160),
        });
      }
      if (ERROR_HINT.test(line)) {
        errTotal += 1;
        const sig = signature(line);
        errC.set(sig, (errC.get(sig) || 0) + 1);
      } else if (WARN_HINT.test(line)) {
        warnTotal += 1;
        const sig = signature(line);
        warnC.set(sig, (warnC.get(sig) || 0) + 1);
      }
    }

    const oneshot = /migration|db-grants|bootstrap-init/i.test(name);
    services.push({
      name,
      status,
      health,
      restarts,
      oomKilled: oom,
      exitCode,
      oneshot,
      errorCount: errTotal,
      warnCount: warnTotal,
      errorSignatures: [...errC.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 30)
        .map(([sig, count]) => ({ signature: sig, count })),
      warnSignatures: [...warnC.entries()]
        .sort((a, b) => b[1] - a[1])
        .slice(0, 20)
        .map(([sig, count]) => ({ signature: sig, count })),
    });
  }

  return {
    generatedAt: new Date().toISOString(),
    serviceCount: services.length,
    services,
    secretHits,
    totals: {
      errorLines: services.reduce((a, s) => a + s.errorCount, 0),
      warnLines: services.reduce((a, s) => a + s.warnCount, 0),
      oomKills: services.filter((s) => s.oomKilled).length,
      unhealthy: services.filter((s) => !s.oneshot && s.health === 'unhealthy').length,
      maxRestarts: Math.max(0, ...services.filter((s) => !s.oneshot).map((s) => s.restarts)),
    },
  };
}

function renderLogMd(scan) {
  const lines = ['# Container log scan', '', `Services: ${scan.serviceCount}`, ''];
  for (const s of scan.services) {
    lines.push(
      `## ${s.name} — status=${s.status} health=${s.health} restarts=${s.restarts} oom=${s.oomKilled} errors=${s.errorCount} warns=${s.warnCount}`,
    );
    for (const sig of (s.errorSignatures || []).slice(0, 8)) {
      lines.push(`- ERROR ×${sig.count}: ${sig.signature}`);
    }
    lines.push('');
  }
  return `${lines.join('\n')}\n`;
}

/* ---------- scoring ---------- */

function scoreJavaMelodyFreshness(jm, jmMeta, jmOk, thr, checks) {
  const maxAge = thr.maxAgeSeconds ?? 600;
  if (!jmOk || !jm || !jm.ranked) {
    pushCheck(
      checks,
      'jm.data',
      1,
      { amber: 1, red: 1 },
      'JavaMelody data missing or dump failed — scored as no data (never GREEN)',
    );
    return;
  }
  const captured = jmMeta?.capturedAt ? Date.parse(jmMeta.capturedAt) : NaN;
  if (!Number.isFinite(captured)) {
    pushCheck(
      checks,
      'jm.data',
      1,
      { amber: 1, red: 2 },
      'JavaMelody snapshot-meta missing/unparseable — treat as stale (AMBER)',
    );
    return;
  }
  const ageSec = Math.max(0, (Date.now() - captured) / 1000);
  pushCheck(
    checks,
    'jm.freshness',
    ageSec,
    { amber: maxAge, red: maxAge * 3 },
    `JavaMelody snapshot age ${Math.round(ageSec)}s (max ${maxAge}s)`,
  );
}

function scoreJavaMelody(jm, thr, checks) {
  const perApp = {};
  if (!jm || !jm.ranked) {
    return { perApp, note: 'no JavaMelody data' };
  }
  const ranked = jm.ranked || {};
  let exclPattern;
  try {
    const excl = thr.httpErrorPctExclusions || {};
    exclPattern = excl.pattern ? new RegExp(excl.pattern, 'i') : /^Error\d+/i;
  } catch (e) {
    throw new Error(`Invalid httpErrorPctExclusions.pattern in thresholds: ${e.message}`);
  }
  const minHits = thr.httpErrorPct?.minHits ?? 20;

  for (const app of APPS) {
    const http = (ranked[app]?.http || []).slice(0, 5);
    const sql = (ranked[app]?.sql || []).slice(0, 5);
    const appMeta = jm.perApp?.[app] || {};
    const lastValue = appMeta.lastValue || {};
    const errorHits = appMeta.errorHits || 0;

    const httpForErr = http.filter((row) => !exclPattern.test(row.name || ''));
    const topHttpMean = maxOf(http, 'mean');
    const topHttpMax = maxOf(http, 'maximum');
    const topHttpErrRow = httpForErr.reduce(
      (best, row) =>
        Number(row.errorRatePct || 0) > Number(best.errorRatePct || 0) ? row : best,
      { errorRatePct: 0, hits: 0 },
    );
    const topHttpErr =
      Number(topHttpErrRow.hits || 0) >= minHits ? Number(topHttpErrRow.errorRatePct || 0) : 0;
    const topSqlMean = maxOf(sql, 'mean');
    const topSqlMax = maxOf(sql, 'maximum');

    pushCheck(checks, `jm.${app}.httpMean`, topHttpMean, thr.topHttpMeanMs, `Top HTTP mean ${topHttpMean} ms on ${app}`);
    pushCheck(checks, `jm.${app}.httpMax`, topHttpMax, thr.topHttpMaxMs, `Top HTTP max ${topHttpMax} ms on ${app}`);
    pushCheck(
      checks,
      `jm.${app}.httpErrorPct`,
      topHttpErr,
      thr.httpErrorPct,
      `Top HTTP error% ${topHttpErr} on ${app} (minHits=${minHits})`,
    );
    pushCheck(checks, `jm.${app}.sqlMean`, topSqlMean, thr.topSqlMeanMs, `Top SQL mean ${topSqlMean} ms on ${app}`);
    pushCheck(checks, `jm.${app}.sqlMax`, topSqlMax, thr.topSqlMaxMs, `Top SQL max ${topSqlMax} ms on ${app}`);
    pushCheck(checks, `jm.${app}.systemErrors`, errorHits, thr.systemErrorHits, `System error hits ${errorHits} on ${app}`);
    if (lastValue.waitingConnections != null) {
      pushCheck(
        checks,
        `jm.${app}.waitingConnections`,
        lastValue.waitingConnections,
        thr.waitingConnections,
        `Waiting connections ${lastValue.waitingConnections} on ${app}`,
      );
    }

    perApp[app] = {
      topHttp: http.map((r) => ({
        name: sanitize(r.name || ''),
        hits: r.hits,
        mean: r.mean,
        maximum: r.maximum,
        errorRatePct: r.errorRatePct ?? 0,
      })),
      topSql: sql.map((r) => ({
        name: sanitize(r.name || ''),
        hits: r.hits,
        mean: r.mean,
        maximum: r.maximum,
      })),
      errorHits,
      lastValue: {
        usedMemory: lastValue.usedMemory ?? null,
        gc: lastValue.gc ?? null,
        activeThreads: lastValue.activeThreads ?? null,
        waitingConnections: lastValue.waitingConnections ?? null,
      },
    };
  }
  return { perApp, note: 'crypto-api is not in the JavaMelody collector application list' };
}

function scoreContainers(logScan, thr, allowlist, checks) {
  const services = logScan.services || [];
  let oom = 0;
  let unhealthy = 0;
  let maxRestarts = 0;
  const errorSigs = [];
  const warnSigs = [];
  let totalErrors = 0;
  const transientCounts = {};

  for (const svc of services) {
    if (svc.oneshot) continue;
    maxRestarts = Math.max(maxRestarts, svc.restarts || 0);
    if (svc.oomKilled || svc.exitCode === 137) oom += 1;
    if (svc.health === 'unhealthy' || svc.status === 'exited') unhealthy += 1;
    totalErrors += svc.errorCount || 0;
    for (const sig of svc.errorSignatures || []) {
      const allowed = isAllowlisted(sig.signature, svc.name, allowlist);
      errorSigs.push({ service: svc.name, ...sig, allowlisted: allowed });
      for (const [label, band] of Object.entries(thr.transientIo || {})) {
        let matched = sig.signature.includes(label);
        if (!matched) {
          try {
            matched = new RegExp(label.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i').test(
              sig.signature,
            );
          } catch (e) {
            throw new Error(
              `Invalid transientIo pattern (blocking): ${label} — ${e.message}`,
            );
          }
        }
        if (matched) {
          transientCounts[label] = (transientCounts[label] || 0) + sig.count;
          void band;
        }
      }
    }
    for (const sig of svc.warnSignatures || []) {
      const allowed = isAllowlisted(sig.signature, svc.name, allowlist);
      warnSigs.push({ service: svc.name, ...sig, allowlisted: allowed });
    }
  }

  for (const [label, band] of Object.entries(thr.transientIo || {})) {
    pushCheck(
      checks,
      `containers.transientIo.${label.replace(/\W+/g, '_')}`,
      transientCounts[label] || 0,
      band,
      `Transient I/O "${label}" count ${transientCounts[label] || 0}`,
    );
  }

  // Allowlist hygiene: expired / no-issue entries are blocking
  const badEntries = allowlist.filter((e) => e.hygieneBad);
  pushCheck(
    checks,
    'allowlist.hygiene',
    badEntries.length,
    { amber: 1, red: 1 },
    badEntries.length
      ? `Allowlist hygiene failures: ${badEntries.map((e) => e.raw).join('; ')}`
      : 'Allowlist hygiene OK',
  );

  const actionableErrors = errorSigs.filter((s) => !s.allowlisted);
  const actionableWarns = warnSigs.filter((s) => !s.allowlisted);

  pushCheck(checks, 'containers.restarts', maxRestarts, thr.restartsPerService, `Max container restarts ${maxRestarts}`);
  pushCheck(checks, 'containers.oom', oom, thr.oomKills, `OOM-killed containers ${oom}`);
  pushCheck(checks, 'containers.unhealthy', unhealthy, thr.unhealthyServices, `Unhealthy services ${unhealthy}`);
  pushCheck(
    checks,
    'containers.errorSignatures',
    actionableErrors.length,
    thr.distinctErrorSignatures,
    `Distinct actionable ERROR signatures ${actionableErrors.length}`,
  );
  pushCheck(
    checks,
    'containers.warnSignatures',
    actionableWarns.length,
    thr.distinctWarnSignatures,
    `Distinct actionable WARN signatures ${actionableWarns.length}`,
  );
  pushCheck(checks, 'containers.totalErrors', totalErrors, thr.totalErrorLines, `Total ERROR/exception lines ${totalErrors}`);

  return {
    serviceCount: services.length,
    maxRestarts,
    oomKills: oom,
    unhealthy,
    totalErrorLines: totalErrors,
    topErrorSignatures: actionableErrors.sort((a, b) => b.count - a.count).slice(0, 15),
    topWarnSignatures: actionableWarns.sort((a, b) => b.count - a.count).slice(0, 10),
    allowlistedErrorCount: errorSigs.filter((s) => s.allowlisted).length,
    haReplicas: HA_API_REPLICAS.map((n) => {
      const svc = services.find((s) => s.name === n);
      return {
        name: n,
        status: svc?.status || 'missing',
        health: svc?.health || null,
        oom: svc?.oomKilled || false,
      };
    }),
  };
}

function pushCheck(checks, id, value, band, message) {
  checks.push({
    id,
    value: value == null ? null : Number(value),
    amber: band?.amber ?? null,
    red: band?.red ?? null,
    verdict: bandVerdict(value, band),
    message,
  });
}

function bandVerdict(value, band) {
  if (value == null || !band) return 'GREEN';
  const n = Number(value);
  if (!Number.isFinite(n)) return 'GREEN';
  if (band.red != null && n >= band.red) return 'RED';
  if (band.amber != null && band.amber > 0 && n >= band.amber) return 'AMBER';
  return 'GREEN';
}

function aggregateVerdict(checks) {
  if (checks.some((c) => c.verdict === 'RED')) return 'RED';
  if (checks.some((c) => c.verdict === 'AMBER')) return 'AMBER';
  return 'GREEN';
}

function maxOf(rows, field) {
  let m = 0;
  for (const row of rows) {
    const v = Number(row[field] || 0);
    if (v > m) m = v;
  }
  return m;
}

/* ---------- allowlist ---------- */

function loadAllowlist(path) {
  if (!existsSync(path)) return [];
  const today = new Date().toISOString().slice(0, 10);
  const entries = [];
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const parts = trimmed.split('|').map((p) => p.trim());
    if (parts.length < 5) {
      entries.push({
        raw: trimmed,
        hygieneBad: true,
        reason: 'need service|regex|issue|expires=|justification',
      });
      continue;
    }
    const [service, pattern, issue, expiresField, ...justParts] = parts;
    const justification = justParts.join('|');
    const expM = /^expires=(\d{4}-\d{2}-\d{2})$/.exec(expiresField);
    const expires = expM ? expM[1] : null;
    const hasIssue = /^#\d+$/.test(issue);
    const expired = !expires || expires < today;
    let re = null;
    try {
      re = new RegExp(pattern);
    } catch (e) {
      throw new Error(
        `Allowlist invalid regex (blocking): ${pattern} — ${e.message}. Fix ${path}`,
      );
    }
    const hygieneBad = !hasIssue || expired;
    entries.push({
      raw: trimmed,
      service,
      pattern,
      issue,
      expires,
      justification,
      re,
      hygieneBad,
      reason: hygieneBad
        ? !hasIssue
          ? 'missing issue'
          : `expired ${expires}`
        : null,
    });
  }
  return entries;
}

function isAllowlisted(signature, serviceName, allowlist) {
  for (const entry of allowlist) {
    if (entry.hygieneBad || !entry.re) continue;
    if (entry.service !== '*') {
      const svcPat = entry.service.endsWith('*')
        ? entry.service.slice(0, -1)
        : entry.service;
      if (!serviceName.includes(svcPat)) continue;
    }
    if (entry.issue === '#747') {
      if (!COMPOSE_RETRY_FIRED) continue;
      if (!serviceName.includes('ezkey-admin-api')) continue;
    }
    if (entry.re.test(signature)) return true;
  }
  return false;
}

/* ---------- report ---------- */

function renderMarkdown(health) {
  const lines = [
    '# Stack health check',
    '',
    `- **Verdict:** ${health.verdict}`,
    `- **Generated:** ${health.generatedAt}`,
    `- **Compose retry (#747) fired:** ${health.composeRetryFired}`,
  ];
  if (health.note) lines.push(`- **Note:** ${health.note}`);
  lines.push('', '## Checks', '', '| Id | Verdict | Value | Amber | Red | Message |', '| --- | --- | ---: | ---: | ---: | --- |');
  for (const c of health.checks) {
    lines.push(
      `| ${c.id} | ${c.verdict} | ${fmt(c.value)} | ${fmt(c.amber)} | ${fmt(c.red)} | ${sanitize(c.message)} |`,
    );
  }
  lines.push('', '## JavaMelody top HTTP / SQL', '');
  for (const app of APPS) {
    const block = health.javamelody?.perApp?.[app];
    if (!block) continue;
    lines.push(`### ${app}`, '', 'HTTP:');
    for (const row of block.topHttp || []) {
      lines.push(
        `- ${row.name} — hits ${row.hits}, mean ${row.mean} ms, max ${row.maximum} ms, err% ${row.errorRatePct}`,
      );
    }
    lines.push('SQL:');
    for (const row of block.topSql || []) {
      const name = String(row.name || '').replace(/\s+/g, ' ').slice(0, 120);
      lines.push(`- ${name} — hits ${row.hits}, mean ${row.mean} ms, max ${row.maximum} ms`);
    }
    lines.push('');
  }
  lines.push('## Actionable ERROR signatures', '');
  const tops = health.containers?.topErrorSignatures || [];
  if (!tops.length) lines.push('_None._');
  else for (const sig of tops) lines.push(`- [${sig.service}] ×${sig.count}: ${sig.signature}`);
  lines.push('', `Allowlisted ERROR signatures: ${health.containers?.allowlistedErrorCount ?? 0}`, '');
  lines.push('## Secret scan (raw logs)', '');
  if (!(health.secretHits || []).length) lines.push('_No secret patterns detected._');
  else
    for (const h of health.secretHits.slice(0, 10))
      lines.push(`- [${h.service}] ${h.kinds.join(',')}: ${h.preview}`);
  lines.push('');
  return `${lines.join('\n')}\n`;
}

function loadJson(path) {
  if (!existsSync(path)) return null;
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch {
    return null;
  }
}

function parseArgs(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    const val = argv[i + 1];
    if (key === '--output-dir') {
      out.outputDir = val;
      i += 1;
    } else if (key === '--jm-dir') {
      out.jmDir = val;
      i += 1;
    } else if (key === '--jm-ok') {
      out.jmOk = val;
      i += 1;
    } else if (key === '--thresholds') {
      out.thresholds = val;
      i += 1;
    } else if (key === '--allowlist') {
      out.allowlist = val;
      i += 1;
    }
  }
  return out;
}

function fmt(v) {
  return v == null ? '—' : String(v);
}
