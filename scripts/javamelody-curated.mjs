#!/usr/bin/env node
/**
 * javamelody-curated — parse frozen JavaMelody XStream XML + lastValue dumps,
 * filter collector/actuator noise, rank HTTP/SQL/Spring by total time, emit
 * curated MD+JSON. Invoked by scripts/javamelody-curated.sh.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(SCRIPT_DIR, '..');
const KEYWORD = 'javamelody-curated';
const APPS = ['admin-api', 'auth-api', 'integration-api'];
const COUNTERS = ['http', 'sql', 'spring', 'error'];
const LASTVALUE_GRAPHS = [
  'usedMemory',
  'cpu',
  'gc',
  'httpHitsRate',
  'sqlHitsRate',
  'activeThreads',
  'activeConnections',
  'usedConnections',
  'waitingConnections',
  'httpMeanTimes',
  'sqlMeanTimes',
];

const args = parseArgs(process.argv.slice(2));
const RAW_DIR = args.rawDir || join(REPO_ROOT, 'logs', 'javamelody', 'raw');
const OUTPUT_DIR = args.outputDir || join(REPO_ROOT, 'logs', 'javamelody');
const MIN_HITS = args.minHits;
const TOP = args.top;
const CONFIG_DIR = join(REPO_ROOT, 'config', 'javamelody');

main();

function main() {
  mkdirSync(OUTPUT_DIR, { recursive: true });

  const noisePaths = loadLines(join(CONFIG_DIR, 'noise-paths.txt'));
  const expectedHot = loadLines(join(CONFIG_DIR, 'expected-hot.txt'));
  const snapshotMeta = loadJson(join(RAW_DIR, 'snapshot-meta.json')) || {};

  const apps = {};
  for (const app of APPS) {
    const xmlPath = join(RAW_DIR, `collector-${app}.xml`);
    if (!existsSync(xmlPath)) {
      console.error(`WARNING: missing ${xmlPath}`);
      continue;
    }
    const xml = readFileSync(xmlPath, 'utf8');
    const counters = parseCounters(xml);
    const httpAll = (counters.http || []).map((row) => annotateHttp(row, noisePaths, expectedHot));
    apps[app] = {
      counters,
      http: httpAll.filter((row) => !row.noise),
      httpNoise: httpAll.filter((row) => row.noise),
      sql: counters.sql || [],
      spring: counters.spring || [],
      error: counters.error || [],
      lastValue: loadLastValues(app),
    };
  }

  const ranked = rankApps(apps);
  const lot = proposeLot(ranked, apps);

  const summary = {
    keyword: KEYWORD,
    generatedAt: new Date().toISOString(),
    snapshot: snapshotMeta,
    minHits: MIN_HITS,
    top: TOP,
    workloadBias: {
      hotByDesign: expectedHot,
      coldByDesign: ['integration-api — operational churn does not loop Integration API'],
      noiseDropped: noisePaths,
    },
    perApp: Object.fromEntries(
      Object.entries(apps).map(([app, data]) => [
        app,
        {
          httpHits: sumHits(data.http),
          httpNames: data.http.length,
          sqlHits: sumHits(data.sql),
          springHits: sumHits(data.spring),
          errorHits: sumHits(data.error),
          lastValue: data.lastValue,
        },
      ]),
    ),
    ranked,
    lot,
  };

  const jsonPath = join(OUTPUT_DIR, 'javamelody.curated.json');
  const mdPath = join(OUTPUT_DIR, 'javamelody.curated.md');
  writeFileSync(jsonPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
  writeFileSync(mdPath, renderMarkdown(summary, apps), 'utf8');

  console.log(`${KEYWORD} curated.`);
  console.log(`Markdown: ${mdPath}`);
  console.log(`JSON: ${jsonPath}`);
  console.log(`HITL lot: ${lot.length} item(s)`);
  for (const item of lot) {
    console.log(`- ${item.priority} [${item.app} ${item.family}] ${item.title}`);
  }
}

function parseArgs(argv) {
  const out = { minHits: 50, top: 15 };
  for (let i = 0; i < argv.length; i += 1) {
    const key = argv[i];
    const val = argv[i + 1];
    if (key === '--raw-dir') {
      out.rawDir = val;
      i += 1;
    } else if (key === '--output-dir') {
      out.outputDir = val;
      i += 1;
    } else if (key === '--min-hits') {
      out.minHits = Number(val);
      i += 1;
    } else if (key === '--top') {
      out.top = Number(val);
      i += 1;
    }
  }
  return out;
}

function loadLines(path) {
  if (!existsSync(path)) {
    return [];
  }
  return readFileSync(path, 'utf8')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith('#'));
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

function parseCounters(xml) {
  const blocks = xml.split('</counter>');
  const out = {};
  for (const block of blocks) {
    const nameMatch = block.match(/<name>([^<]+)<\/name>/);
    if (!nameMatch) {
      continue;
    }
    const name = nameMatch[1];
    if (!COUNTERS.includes(name)) {
      continue;
    }
    out[name] = parseRequests(block);
  }
  return out;
}

function parseRequests(counterXml) {
  const rows = [];
  const re = /<request>([\s\S]*?)<\/request>/g;
  let match = re.exec(counterXml);
  while (match) {
    const body = match[1];
    const name = innerText(body, 'name');
    const hits = intTag(body, 'hits');
    const durationsSum = intTag(body, 'durationsSum');
    const maximum = intTag(body, 'maximum');
    const cpuTimeSum = intTag(body, 'cpuTimeSum');
    const systemErrors = intTag(body, 'systemErrors');
    const childHits = intTag(body, 'childHits');
    const childDurationsSum = intTag(body, 'childDurationsSum');
    const mean = hits > 0 ? durationsSum / hits : 0;
    const cpuMean = hits > 0 && cpuTimeSum >= 0 ? cpuTimeSum / hits : null;
    rows.push({
      name: collapseWs(name),
      hits,
      durationsSum,
      mean: round(mean, 2),
      maximum,
      cpuTimeSum: cpuTimeSum < 0 ? null : cpuTimeSum,
      cpuMean: cpuMean === null ? null : round(cpuMean, 2),
      systemErrors,
      errorRatePct: hits > 0 ? round((systemErrors / hits) * 100, 4) : 0,
      childHits,
      childDurationsSum,
      sqlPerRequest: hits > 0 && childHits > 0 ? round(childHits / hits, 2) : 0,
      childMeanPerRequest: hits > 0 ? round(childDurationsSum / hits, 2) : 0,
    });
    match = re.exec(counterXml);
  }
  rows.sort((a, b) => b.durationsSum - a.durationsSum);
  return rows;
}

function annotateHttp(row, noisePaths, expectedHot) {
  const noise = noisePaths.some((prefix) => row.name.includes(prefix));
  const hot = expectedHot.includes(row.name);
  return { ...row, noise, expectedHot: hot };
}

function loadLastValues(app) {
  const out = {};
  for (const graph of LASTVALUE_GRAPHS) {
    const path = join(RAW_DIR, `lastvalue-${app}-${graph}.txt`);
    if (!existsSync(path)) {
      continue;
    }
    const raw = readFileSync(path, 'utf8').trim();
    const num = Number(raw);
    out[graph] = Number.isFinite(num) && num >= 0 ? num : null;
  }
  return out;
}

function rankApps(apps) {
  const ranked = {};
  for (const [app, data] of Object.entries(apps)) {
    ranked[app] = {
      http: data.http.filter((row) => row.hits >= MIN_HITS).slice(0, TOP),
      sql: data.sql.filter((row) => row.hits >= MIN_HITS).slice(0, TOP),
      spring: data.spring.filter((row) => row.hits >= MIN_HITS).slice(0, TOP),
    };
  }
  return ranked;
}

function proposeLot(ranked, apps) {
  const lot = [];
  const seen = new Set();

  const push = (item) => {
    const key = `${item.app}|${item.family}|${item.title}`;
    if (seen.has(key) || lot.length >= 6) {
      return;
    }
    seen.add(key);
    lot.push(item);
  };

  for (const [app, data] of Object.entries(apps)) {
    const httpPeers = data.http.filter((row) => row.hits >= MIN_HITS);
    const httpMedian = median(httpPeers.map((row) => row.mean));
    for (const row of httpPeers.slice(0, 8)) {
      const errorHot = row.errorRatePct >= 0.1 && row.systemErrors >= 5;
      const meanOutlier = httpMedian > 0 && row.mean >= httpMedian * 3 && row.mean >= 15;
      const sqlHeavy = row.sqlPerRequest >= 8 && row.hits >= MIN_HITS;
      if (row.expectedHot && !errorHot && !meanOutlier && !sqlHeavy) {
        continue;
      }
      if (errorHot) {
        push({
          id: `JM-${lot.length + 1}`,
          priority: 'P1',
          app,
          family: 'http',
          title: `${row.name} error rate ${row.errorRatePct}%`,
          why: 'Non-trivial error rate on a protocol path.',
          hint: `Inspect ${row.name} failures and RFC 9457 types.`,
          metrics: row,
        });
      } else if (sqlHeavy && row.expectedHot) {
        push({
          id: `JM-${lot.length + 1}`,
          priority: 'P2',
          app,
          family: 'http',
          title: `${row.name} runs ${row.sqlPerRequest} SQL statements per request`,
          why: 'Volume is expected; SQL-per-request is the improvement angle.',
          hint: 'Map child SQL to token validation, access checks, and writes.',
          metrics: row,
        });
      } else if (meanOutlier && !row.expectedHot) {
        push({
          id: `JM-${lot.length + 1}`,
          priority: 'P2',
          app,
          family: 'http',
          title: `${row.name} mean ${row.mean} ms (peer median ${round(httpMedian, 2)})`,
          why: 'Mean is high relative to other HTTP names on this API.',
          hint: `Open the controller for ${row.name}.`,
          metrics: row,
        });
      }
    }

    for (const row of (ranked[app]?.spring || []).slice(0, 8)) {
      if (
        row.name.includes('updateTokenLastUsed') ||
        row.name.includes('validateTokenWithRelations')
      ) {
        push({
          id: `JM-${lot.length + 1}`,
          priority: 'P2',
          app,
          family: 'spring',
          title: `${row.name} ${row.hits} hits, mean ${row.mean} ms`,
          why: 'Per-request session sliding TTL / token reload on the Admin hot path.',
          hint: 'AdminTokenAuthenticationFilter + AdminTokenValidationService.updateTokenLastUsed.',
          metrics: row,
        });
      }
      if (row.name.includes('canAccessAuthAttempt') || row.name.includes('canAccessEnrollment')) {
        push({
          id: `JM-${lot.length + 1}`,
          priority: 'P3',
          app,
          family: 'spring',
          title: `${row.name} stacked object-check on the hot GET`,
          why: 'Access checks are correct; stacking two loads on every GET may be accidental cost.',
          hint: 'AccessControlService.canAccessAuthAttempt / canAccessEnrollment.',
          metrics: row,
        });
      }
    }

    const topSql = (ranked[app]?.sql || [])[0];
    if (topSql && topSql.mean >= 2) {
      push({
        id: `JM-${lot.length + 1}`,
        priority: 'P2',
        app,
        family: 'sql',
        title: `Slow SQL mean ${topSql.mean} ms: ${sqlPreview(topSql.name)}`,
        why: 'Top SQL by total time also has a non-trivial mean.',
        hint: 'Match the table to Flyway indexes / partition keys.',
        metrics: topSql,
      });
    }

    const waiting = data.lastValue.waitingConnections;
    const used = data.lastValue.usedConnections;
    const active = data.lastValue.activeConnections;
    if (waiting && waiting > 0) {
      push({
        id: `JM-${lot.length + 1}`,
        priority: 'P1',
        app,
        family: 'pool',
        title: `JDBC waiters=${waiting} used=${used} active=${active}`,
        why: 'Connection pool wait under this workload.',
        hint: 'Check Hikari pool size vs SQL-per-request on the hot HTTP names.',
        metrics: { waiting, used, active },
      });
    }
  }

  if (apps['integration-api'] && sumHits(apps['integration-api'].http) < MIN_HITS) {
    // Coverage caveat, not a HITL performance item.
  }

  return lot.map((item, index) => ({ ...item, id: `JM-${String(index + 1).padStart(3, '0')}` }));
}

function renderMarkdown(summary, apps) {
  const lines = [];
  lines.push('# JavaMelody curated report');
  lines.push('');
  lines.push(`Keyword: \`${KEYWORD}\``);
  lines.push('');
  lines.push('## Run context');
  lines.push('');
  lines.push(
    `- **Captured at:** ${summary.snapshot.capturedAt || '(unknown)'}`,
  );
  lines.push(`- **Collector:** ${summary.snapshot.collectorUrl || 'http://localhost:8088'}`);
  lines.push(`- **Period:** ${summary.snapshot.period || 'tout'}`);
  lines.push(
    '- **Workload:** operational churn (three instances, about two hours). Not a balanced three-API load test.',
  );
  lines.push(
    '- **Hot by design:** Admin `POST /auth-attempts` + `GET /auth-attempts/{id}`; Auth `pending` + `respond`.',
  );
  lines.push(
    '- **Cold by design:** Integration API is almost unused in the churn loop. Silence is coverage, not health.',
  );
  lines.push(
    '- **Crypto API** is excluded from JavaMelody. Signing happens in the test JVM, not these three APIs.',
  );
  lines.push(
    '- **Direct management-port XML** may 503 on the host; collector XML is the source of truth.',
  );
  lines.push(`- **Filters:** min-hits=${summary.minHits}, top=${summary.top}, actuator/monitoring dropped.`);
  lines.push('');
  lines.push('A finding that only restates "pending/respond/create dominate hits" is **not** a signal.');
  lines.push('');
  lines.push('## Per-API volume');
  lines.push('');
  lines.push('| API | HTTP hits | HTTP names | SQL hits | Spring hits | HTTP errors |');
  lines.push('| --- | ---: | ---: | ---: | ---: | ---: |');
  for (const app of APPS) {
    const p = summary.perApp[app];
    if (!p) {
      continue;
    }
    lines.push(
      `| ${app} | ${p.httpHits} | ${p.httpNames} | ${p.sqlHits} | ${p.springHits} | ${p.errorHits} |`,
    );
  }
  lines.push('');
  lines.push('## LastValue (current, after the churn window)');
  lines.push('');
  lines.push(
    '| API | usedMemory (bytes) | cpu | gc | httpMeanTimes | usedConnections | waitingConnections |',
  );
  lines.push('| --- | ---: | ---: | ---: | ---: | ---: | ---: |');
  for (const app of APPS) {
    const lv = summary.perApp[app]?.lastValue || {};
    lines.push(
      `| ${app} | ${fmtNum(lv.usedMemory)} | ${fmtNum(lv.cpu)} | ${fmtNum(lv.gc)} | ${fmtNum(lv.httpMeanTimes)} | ${fmtNum(lv.usedConnections)} | ${fmtNum(lv.waitingConnections)} |`,
    );
  }
  lines.push('');
  lines.push(
    'Rates near zero after the run are expected. Memory in the ~200 MiB range with idle CPU is not a pool/GC alarm.',
  );
  lines.push('');

  for (const app of APPS) {
    const data = apps[app];
    if (!data) {
      continue;
    }
    lines.push(`## ${app}`);
    lines.push('');
    lines.push('### Top HTTP by total time');
    lines.push('');
    lines.push(httpTable(summary.ranked[app]?.http || []));
    lines.push('');
    lines.push('### Top SQL by total time');
    lines.push('');
    lines.push(sqlTable(summary.ranked[app]?.sql || []));
    lines.push('');
    lines.push('### Top Spring by total time');
    lines.push('');
    lines.push(springTable(summary.ranked[app]?.spring || []));
    lines.push('');
  }

  lines.push('## Proposed HITL lot');
  lines.push('');
  if (summary.lot.length === 0) {
    lines.push(
      'No P1/P2 performance campaign from this snapshot. Baseline captured; re-run after the next churn.',
    );
    lines.push('');
  } else {
    lines.push('| ID | P | API | Family | Title |');
    lines.push('| --- | --- | --- | --- | --- |');
    for (const item of summary.lot) {
      lines.push(`| ${item.id} | ${item.priority} | ${item.app} | ${item.family} | ${escapeMd(item.title)} |`);
    }
    lines.push('');
    for (const item of summary.lot) {
      lines.push(`### ${item.id} — ${item.priority}`);
      lines.push('');
      lines.push(`- **Why:** ${item.why}`);
      lines.push(`- **Complementary search:** ${item.hint}`);
      lines.push('');
    }
  }

  lines.push('## Non-signals');
  lines.push('');
  lines.push(
    '- Auth `pending` / `respond` and Admin create/GET dominating **hits** — that is the churn loop.',
  );
  lines.push('- Integration API near-zero HTTP — churn creates API keys then never uses them for auth-attempt create.');
  lines.push('- Collector `GET /actuator/monitoring` scrape (dropped).');
  lines.push('- SQL `cpuTimeSum` negative sentinels — JavaMelody does not measure CPU on JDBC.');
  lines.push('- Audit-log inserts and HMAC — expected tamper-evident monitoring cost unless mean grows vs siblings.');
  lines.push('- Auth `SELECT … FOR NO KEY UPDATE` on pending — expected lock; only a signal if mean is out of line.');
  lines.push('');
  lines.push('## Complementary search hints');
  lines.push('');
  lines.push('Do these only for HITL items, not for every top-N row:');
  lines.push('');
  lines.push('1. Map the HTTP name to the controller; use Spring rows to see nested cost.');
  lines.push(
    '2. For `ezkey_admin_tokens` updates: `AdminTokenAuthenticationFilter` + `AdminTokenValidationService.updateTokenLastUsed` (second find + save per request).',
  );
  lines.push(
    '3. For pending SQL: `AuthAttemptPendingService` native `FOR NO KEY UPDATE` — expected unless mean dwarfs respond.',
  );
  lines.push('4. Optional Postgres table sizes only if a SQL mean looks wrong, not because hit count is high.');
  lines.push('5. Do not take heap histograms or thread dumps unless lastValue shows a memory/thread problem.');
  lines.push('');
  return `${lines.join('\n')}\n`;
}

function httpTable(rows) {
  if (!rows.length) {
    return '_none after filters_';
  }
  const lines = [
    '| Request | Hits | Mean ms | Max | Errors | SQL/req | Expected-hot |',
    '| --- | ---: | ---: | ---: | ---: | ---: | --- |',
  ];
  for (const row of rows) {
    lines.push(
      `| \`${escapeMd(row.name)}\` | ${row.hits} | ${row.mean} | ${row.maximum} | ${row.systemErrors} | ${row.sqlPerRequest} | ${row.expectedHot ? 'yes' : ''} |`,
    );
  }
  return lines.join('\n');
}

function sqlTable(rows) {
  if (!rows.length) {
    return '_none after filters_';
  }
  const lines = [
    '| SQL (preview) | Hits | Mean ms | Max | Total ms |',
    '| --- | ---: | ---: | ---: | ---: |',
  ];
  for (const row of rows) {
    lines.push(
      `| \`${escapeMd(sqlPreview(row.name))}\` | ${row.hits} | ${row.mean} | ${row.maximum} | ${row.durationsSum} |`,
    );
  }
  return lines.join('\n');
}

function springTable(rows) {
  if (!rows.length) {
    return '_none after filters_';
  }
  const lines = [
    '| Bean.method | Hits | Mean ms | Max | CPU mean ms |',
    '| --- | ---: | ---: | ---: | ---: |',
  ];
  for (const row of rows) {
    lines.push(
      `| \`${escapeMd(row.name)}\` | ${row.hits} | ${row.mean} | ${row.maximum} | ${row.cpuMean ?? '—'} |`,
    );
  }
  return lines.join('\n');
}

function sqlPreview(name) {
  const collapsed = collapseWs(name);
  if (collapsed.length <= 140) {
    return collapsed;
  }
  return `${collapsed.slice(0, 137)}...`;
}

function innerText(xml, tag) {
  const match = xml.match(new RegExp(`<${tag}>([\\s\\S]*?)</${tag}>`));
  return match ? decodeXml(match[1]) : '';
}

function intTag(xml, tag) {
  const match = xml.match(new RegExp(`<${tag}>(-?\\d+)</${tag}>`));
  return match ? Number(match[1]) : 0;
}

function decodeXml(value) {
  return value
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&amp;/g, '&')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'");
}

function collapseWs(value) {
  return String(value || '')
    .replace(/\s+/g, ' ')
    .trim();
}

function sumHits(rows) {
  return (rows || []).reduce((acc, row) => acc + (row.hits || 0), 0);
}

function median(values) {
  if (!values.length) {
    return 0;
  }
  const sorted = [...values].sort((a, b) => a - b);
  const mid = Math.floor(sorted.length / 2);
  if (sorted.length % 2 === 0) {
    return (sorted[mid - 1] + sorted[mid]) / 2;
  }
  return sorted[mid];
}

function round(value, digits) {
  const f = 10 ** digits;
  return Math.round(value * f) / f;
}

function fmtNum(value) {
  if (value === null || value === undefined) {
    return '—';
  }
  if (Math.abs(value) >= 1000) {
    return String(Math.round(value));
  }
  return String(value);
}

function escapeMd(value) {
  return String(value).replace(/\|/g, '\\|').replace(/`/g, "'");
}
