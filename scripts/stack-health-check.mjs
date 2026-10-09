#!/usr/bin/env node
/**
 * stack-health-check — score JavaMelody + container-log signals against
 * config/quality-gate/thresholds.json. Invoked by scripts/stack-health-check.sh.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(SCRIPT_DIR, '..');
const KEYWORD = 'stack-health-check';
const APPS = ['admin-api', 'auth-api', 'integration-api'];

const args = parseArgs(process.argv.slice(2));
const OUTPUT_DIR = args.outputDir || join(REPO_ROOT, 'logs', 'quality-gate', 'health');
const JM_JSON =
  args.jmJson || join(REPO_ROOT, 'logs', 'javamelody', 'javamelody.curated.json');
const LOG_SCAN = args.logScan || join(OUTPUT_DIR, 'container-logs.json');
const THRESHOLDS_PATH =
  args.thresholds || join(REPO_ROOT, 'config', 'quality-gate', 'thresholds.json');
const ALLOWLIST_PATH =
  args.allowlist || join(REPO_ROOT, 'config', 'quality-gate', 'log-allowlist.txt');

main();

function main() {
  mkdirSync(OUTPUT_DIR, { recursive: true });

  const thresholds = loadJson(THRESHOLDS_PATH) || {};
  const allowlist = loadAllowlist(ALLOWLIST_PATH);
  const jm = loadJson(JM_JSON) || {};
  const logScan = loadJson(LOG_SCAN) || { services: [], totals: {} };

  const checks = [];
  const health = {
    keyword: KEYWORD,
    generatedAt: new Date().toISOString(),
    thresholdsPath: THRESHOLDS_PATH,
    baselineLocked: Boolean(thresholds.baselineLocked),
    baselineNote: thresholds.baselineNote || '',
    javamelody: scoreJavaMelody(jm, thresholds.javamelody || {}, checks),
    containers: scoreContainers(logScan, thresholds.containers || {}, allowlist, checks),
  };

  health.verdict = aggregateVerdict(checks);
  health.checks = checks;

  const jsonPath = join(OUTPUT_DIR, 'health.json');
  const mdPath = join(OUTPUT_DIR, 'HEALTH.md');
  writeFileSync(jsonPath, `${JSON.stringify(health, null, 2)}\n`, 'utf8');
  writeFileSync(mdPath, renderMarkdown(health), 'utf8');

  console.log(`${KEYWORD}: ${health.verdict}`);
  console.log(`JSON: ${jsonPath}`);
  console.log(`Markdown: ${mdPath}`);
  if (health.verdict === 'RED') {
    process.exitCode = 1;
  }
}

function scoreJavaMelody(jm, thr, checks) {
  const perApp = {};
  const ranked = jm.ranked || {};

  for (const app of APPS) {
    const http = (ranked[app]?.http || []).slice(0, 5);
    const sql = (ranked[app]?.sql || []).slice(0, 5);
    const appMeta = jm.perApp?.[app] || {};
    const lastValue = appMeta.lastValue || {};
    const errorHits = appMeta.errorHits || 0;

    // Exclude JavaMelody synthetic Error* buckets from httpErrorPct (see
    // thresholds.javamelody.httpErrorPctExclusions — Error404 always 100%).
    const excl = thr.httpErrorPctExclusions || {};
    const exclPattern = excl.pattern ? new RegExp(excl.pattern, 'i') : /^Error\d+/i;
    const httpForErr = http.filter((row) => !exclPattern.test(row.name || ''));
    const topHttpMean = maxOf(http, 'mean');
    const topHttpMax = maxOf(http, 'maximum');
    const topHttpErr = maxOf(httpForErr, 'errorRatePct');
    const topSqlMean = maxOf(sql, 'mean');
    const topSqlMax = maxOf(sql, 'maximum');

    pushCheck(
      checks,
      `jm.${app}.httpMean`,
      topHttpMean,
      thr.topHttpMeanMs,
      `Top HTTP mean ${topHttpMean} ms on ${app}`,
    );
    pushCheck(
      checks,
      `jm.${app}.httpMax`,
      topHttpMax,
      thr.topHttpMaxMs,
      `Top HTTP max ${topHttpMax} ms on ${app}`,
    );
    pushCheck(
      checks,
      `jm.${app}.httpErrorPct`,
      topHttpErr,
      thr.httpErrorPct,
      `Top HTTP error% ${topHttpErr} on ${app}`,
    );
    pushCheck(
      checks,
      `jm.${app}.sqlMean`,
      topSqlMean,
      thr.topSqlMeanMs,
      `Top SQL mean ${topSqlMean} ms on ${app}`,
    );
    pushCheck(
      checks,
      `jm.${app}.sqlMax`,
      topSqlMax,
      thr.topSqlMaxMs,
      `Top SQL max ${topSqlMax} ms on ${app}`,
    );
    pushCheck(
      checks,
      `jm.${app}.systemErrors`,
      errorHits,
      thr.systemErrorHits,
      `System error hits ${errorHits} on ${app}`,
    );
    const waiting = lastValue.waitingConnections;
    if (waiting != null) {
      pushCheck(
        checks,
        `jm.${app}.waitingConnections`,
        waiting,
        thr.waitingConnections,
        `Waiting connections ${waiting} on ${app}`,
      );
    }

    perApp[app] = {
      topHttp: http.map(compactRow),
      topSql: sql.map(compactRow),
      errorHits,
      lastValue: {
        usedMemory: lastValue.usedMemory ?? null,
        gc: lastValue.gc ?? null,
        activeThreads: lastValue.activeThreads ?? null,
        httpMeanTimes: lastValue.httpMeanTimes ?? null,
        sqlMeanTimes: lastValue.sqlMeanTimes ?? null,
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

  for (const svc of services) {
    if (svc.oneshot) {
      continue;
    }
    maxRestarts = Math.max(maxRestarts, svc.restarts || 0);
    if (svc.oomKilled || svc.exitCode === 137) {
      oom += 1;
    }
    if (svc.health === 'unhealthy' || svc.status === 'exited') {
      unhealthy += 1;
    }
    totalErrors += svc.errorCount || 0;
    for (const sig of svc.errorSignatures || []) {
      const allowed = isAllowlisted(sig.signature, allowlist);
      errorSigs.push({ service: svc.name, ...sig, allowlisted: allowed });
    }
    for (const sig of svc.warnSignatures || []) {
      const allowed = isAllowlisted(sig.signature, allowlist);
      warnSigs.push({ service: svc.name, ...sig, allowlisted: allowed });
    }
  }

  const actionableErrors = errorSigs.filter((s) => !s.allowlisted);
  const actionableWarns = warnSigs.filter((s) => !s.allowlisted);

  pushCheck(
    checks,
    'containers.restarts',
    maxRestarts,
    thr.restartsPerService,
    `Max container restarts ${maxRestarts}`,
  );
  pushCheck(checks, 'containers.oom', oom, thr.oomKills, `OOM-killed containers ${oom}`);
  pushCheck(
    checks,
    'containers.unhealthy',
    unhealthy,
    thr.unhealthyServices,
    `Unhealthy services ${unhealthy}`,
  );
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
  pushCheck(
    checks,
    'containers.totalErrors',
    totalErrors,
    thr.totalErrorLines,
    `Total ERROR/exception lines ${totalErrors}`,
  );

  return {
    serviceCount: services.length,
    maxRestarts,
    oomKills: oom,
    unhealthy,
    totalErrorLines: totalErrors,
    topErrorSignatures: actionableErrors
      .sort((a, b) => b.count - a.count)
      .slice(0, 15),
    topWarnSignatures: actionableWarns
      .sort((a, b) => b.count - a.count)
      .slice(0, 10),
    allowlistedErrorCount: errorSigs.filter((s) => s.allowlisted).length,
  };
}

function pushCheck(checks, id, value, band, message) {
  const verdict = bandVerdict(value, band);
  checks.push({
    id,
    value: value == null ? null : Number(value),
    amber: band?.amber ?? null,
    red: band?.red ?? null,
    verdict,
    message,
  });
}

function bandVerdict(value, band) {
  if (value == null || !band) {
    return 'GREEN';
  }
  const n = Number(value);
  if (!Number.isFinite(n)) {
    return 'GREEN';
  }
  // Inclusive upper bands. A zero amber would make every zero value AMBER, so
  // treat amber=0 as "disabled" (only red applies).
  if (band.red != null && n >= band.red) {
    return 'RED';
  }
  if (band.amber != null && band.amber > 0 && n >= band.amber) {
    return 'AMBER';
  }
  return 'GREEN';
}

function aggregateVerdict(checks) {
  if (checks.some((c) => c.verdict === 'RED')) {
    return 'RED';
  }
  if (checks.some((c) => c.verdict === 'AMBER')) {
    return 'AMBER';
  }
  return 'GREEN';
}

function renderMarkdown(health) {
  const lines = [];
  lines.push(`# Stack health check`);
  lines.push('');
  lines.push(`- **Verdict:** ${health.verdict}`);
  lines.push(`- **Generated:** ${health.generatedAt}`);
  lines.push(`- **Baseline locked:** ${health.baselineLocked}`);
  if (health.baselineNote) {
    lines.push(`- **Baseline note:** ${health.baselineNote}`);
  }
  lines.push('');
  lines.push('## Checks');
  lines.push('');
  lines.push('| Id | Verdict | Value | Amber | Red | Message |');
  lines.push('| --- | --- | ---: | ---: | ---: | --- |');
  for (const c of health.checks) {
    lines.push(
      `| ${c.id} | ${c.verdict} | ${fmt(c.value)} | ${fmt(c.amber)} | ${fmt(c.red)} | ${c.message} |`,
    );
  }
  lines.push('');
  lines.push('## JavaMelody top HTTP / SQL (per app)');
  lines.push('');
  for (const app of APPS) {
    const block = health.javamelody.perApp[app];
    if (!block) {
      continue;
    }
    lines.push(`### ${app}`);
    lines.push('');
    lines.push('HTTP:');
    for (const row of block.topHttp) {
      lines.push(
        `- ${row.name} — hits ${row.hits}, mean ${row.mean} ms, max ${row.maximum} ms, err% ${row.errorRatePct}`,
      );
    }
    lines.push('SQL:');
    for (const row of block.topSql) {
      lines.push(
        `- ${sqlPreview(row.name)} — hits ${row.hits}, mean ${row.mean} ms, max ${row.maximum} ms`,
      );
    }
    lines.push(
      `Signals: usedMemory=${fmt(block.lastValue.usedMemory)}, gc=${fmt(block.lastValue.gc)}, threads=${fmt(block.lastValue.activeThreads)}, waitingConnections=${fmt(block.lastValue.waitingConnections)}`,
    );
    lines.push('');
  }
  lines.push('## Container log signatures (actionable)');
  lines.push('');
  const tops = health.containers.topErrorSignatures || [];
  if (tops.length === 0) {
    lines.push('_No actionable ERROR signatures._');
  } else {
    for (const sig of tops) {
      lines.push(`- [${sig.service}] ×${sig.count}: ${sig.signature}`);
    }
  }
  lines.push('');
  lines.push(
    `Allowlisted ERROR signatures (noise): ${health.containers.allowlistedErrorCount}`,
  );
  lines.push('');
  return `${lines.join('\n')}\n`;
}

function compactRow(row) {
  return {
    name: row.name,
    hits: row.hits,
    mean: row.mean,
    maximum: row.maximum,
    errorRatePct: row.errorRatePct ?? 0,
    systemErrors: row.systemErrors ?? 0,
  };
}

function maxOf(rows, field) {
  let m = 0;
  for (const row of rows) {
    const v = Number(row[field] || 0);
    if (v > m) {
      m = v;
    }
  }
  return m;
}

function sqlPreview(name) {
  const s = String(name || '').replace(/\s+/g, ' ').trim();
  return s.length > 120 ? `${s.slice(0, 117)}...` : s;
}

function isAllowlisted(signature, allowlist) {
  for (const entry of allowlist) {
    try {
      if (entry.re.test(signature)) {
        return true;
      }
    } catch {
      // ignore bad regex
    }
  }
  return false;
}

function loadAllowlist(path) {
  if (!existsSync(path)) {
    return [];
  }
  return readFileSync(path, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'))
    .map((line) => {
      const pipe = line.indexOf('|');
      const pattern = (pipe >= 0 ? line.slice(0, pipe) : line).trim();
      const justification = (pipe >= 0 ? line.slice(pipe + 1) : '').trim();
      return { pattern, justification, re: new RegExp(pattern, 'i') };
    });
}

function loadJson(path) {
  if (!existsSync(path)) {
    return null;
  }
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
    } else if (key === '--jm-json') {
      out.jmJson = val;
      i += 1;
    } else if (key === '--log-scan') {
      out.logScan = val;
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
  if (v == null) {
    return '—';
  }
  return String(v);
}
