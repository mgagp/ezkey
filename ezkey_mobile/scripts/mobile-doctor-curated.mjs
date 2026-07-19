#!/usr/bin/env node
/**
 * mobile-doctor-curated — punctual hygiene pass for ezkey_mobile.
 *
 * Runs react-doctor + Semgrep (Ezkey pack) + Detekt, then curates into
 * P1/P2/P3 under logs/mobile-doctor/. Not a CI / validate:ci gate.
 *
 * Keyword: mobile-doctor-curated
 */

import {copyFileSync, existsSync, mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import {dirname, join, relative, resolve} from 'node:path';
import {fileURLToPath} from 'node:url';
import {spawnSync} from 'node:child_process';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(SCRIPT_DIR, '..');
const OUTPUT_DIR = join(PROJECT_ROOT, 'logs', 'mobile-doctor');
const RAW_DIR = join(OUTPUT_DIR, 'raw');
const CONFIG_DIR = join(PROJECT_ROOT, 'config', 'mobile-doctor');
const SUPPRESSIONS_PATH = join(CONFIG_DIR, 'suppressions.json');
const KEYWORD = 'mobile-doctor-curated';

const PRIORITY_LABELS = {
  P1: 'Review now',
  P2: 'Good next pass',
  P3: 'Defer unless touching the area',
};

const args = process.argv.slice(2);
const hasFlag = name => args.includes(name);
const skipReactDoctor = hasFlag('--skip-react-doctor');
const skipSemgrep = hasFlag('--skip-semgrep');
const skipDetekt = hasFlag('--skip-detekt');
const curateOnly = hasFlag('--curate-only');

main();

function main() {
  mkdirSync(RAW_DIR, {recursive: true});

  if (!curateOnly) {
    if (!skipReactDoctor) {
      runReactDoctor();
    }
    if (!skipSemgrep) {
      runNodeScript('semgrep-scan.mjs', 'Semgrep');
      copyIfExists(
        join(PROJECT_ROOT, '.monitor', 'semgrep-report.json'),
        join(RAW_DIR, 'semgrep.json'),
      );
    }
    if (!skipDetekt) {
      runNodeScript('detekt-scan.mjs', 'Detekt');
      copyIfExists(join(PROJECT_ROOT, '.monitor', 'detekt.sarif'), join(RAW_DIR, 'detekt.sarif'));
    }
  }

  const diagnostics = [];
  diagnostics.push(...loadReactDoctor(join(RAW_DIR, 'react-doctor.json')));
  diagnostics.push(...loadSemgrep(join(RAW_DIR, 'semgrep.json')));
  if (!existsSync(join(RAW_DIR, 'semgrep.json'))) {
    diagnostics.push(...loadSemgrep(join(PROJECT_ROOT, '.monitor', 'semgrep-report.json')));
  }
  diagnostics.push(...loadDetektSarif(join(RAW_DIR, 'detekt.sarif')));
  if (!existsSync(join(RAW_DIR, 'detekt.sarif'))) {
    diagnostics.push(...loadDetektSarif(join(PROJECT_ROOT, '.monitor', 'detekt.sarif')));
  }

  if (diagnostics.length === 0) {
    console.error(
      `[${KEYWORD}] No analyzer inputs found under logs/mobile-doctor/raw or .monitor/.`,
    );
    process.exit(2);
  }

  const suppressions = loadSuppressions(SUPPRESSIONS_PATH);
  const suppressed = [];
  const curated = [];

  for (const diagnostic of diagnostics) {
    const match = matchSuppression(diagnostic, suppressions);
    if (match) {
      suppressed.push({...diagnostic, suppressionReason: match.reason});
    } else {
      curated.push({
        ...diagnostic,
        priority: priorityFor(diagnostic),
      });
    }
  }

  const ruleGroups = buildRuleGroups(curated);
  const suppressedRuleGroups = buildSuppressedGroups(suppressed);
  const fileGroups = buildCountGroups(curated, 'filePath');
  const toolGroups = buildCountGroups(curated, 'tool');
  const categoryGroups = buildCountGroups(curated, 'category');

  const summary = {
    keyword: KEYWORD,
    generatedAt: new Date().toISOString(),
    projectRoot: PROJECT_ROOT,
    raw: summarize(diagnostics),
    curated: summarize(curated),
    suppressedRules: suppressedRuleGroups,
    topRules: ruleGroups.slice(0, 20),
    topFiles: fileGroups.slice(0, 15),
    tools: toolGroups,
    categories: categoryGroups,
    findings: curated.slice(0, 200).map(toFindingRecord),
  };

  const curatedJsonPath = join(OUTPUT_DIR, 'mobile-doctor.curated.json');
  const curatedMdPath = join(OUTPUT_DIR, 'mobile-doctor.curated.md');

  writeFileSync(curatedJsonPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
  writeFileSync(
    curatedMdPath,
    renderMarkdown({
      rawSummary: summary.raw,
      curatedSummary: summary.curated,
      toolGroups,
      categoryGroups,
      fileGroups,
      ruleGroups,
      suppressedRuleGroups,
    }),
    'utf8',
  );

  console.log(`${KEYWORD} completed.`);
  console.log(`Raw diagnostics: ${diagnostics.length}`);
  console.log(`Curated kept: ${curated.length}`);
  console.log(`Suppressed: ${suppressed.length}`);
  console.log(`Markdown: ${curatedMdPath}`);
  console.log(`JSON: ${curatedJsonPath}`);

  for (const group of ruleGroups.slice(0, 5)) {
    console.log(
      `- ${group.priority} [${group.tool}] ${group.rule}: ${group.count} (${group.category})`,
    );
  }
}

function runReactDoctor() {
  console.log(`[${KEYWORD}] Running react-doctor...`);
  const result = spawnSync(
    'npx',
    [
      '--yes',
      'react-doctor@latest',
      '.',
      '--scope',
      'full',
      '--no-score',
      '--blocking',
      'none',
      '--json',
      '--json-compact',
    ],
    {
      cwd: PROJECT_ROOT,
      encoding: 'utf8',
      maxBuffer: 20 * 1024 * 1024,
      shell: process.platform === 'win32',
    },
  );

  writeFileSync(join(RAW_DIR, 'react-doctor.stderr.log'), result.stderr ?? '', 'utf8');

  if (result.error) {
    console.error(`[${KEYWORD}] Failed to start react-doctor:`, result.error.message);
    process.exit(1);
  }

  const report = parseJsonReport(result.stdout);
  if (!report) {
    console.error(`[${KEYWORD}] Could not parse react-doctor JSON output.`);
    if (result.stdout) {
      console.error(result.stdout.slice(0, 2000));
    }
    process.exit(result.status ?? 1);
  }

  writeFileSync(join(RAW_DIR, 'react-doctor.json'), `${JSON.stringify(report, null, 2)}\n`, 'utf8');
  console.log(
    `[${KEYWORD}] react-doctor raw written (${Array.isArray(report.diagnostics) ? report.diagnostics.length : 0} diagnostics).`,
  );
}

function runNodeScript(scriptName, label) {
  console.log(`[${KEYWORD}] Running ${label}...`);
  const result = spawnSync(process.execPath, [join(SCRIPT_DIR, scriptName)], {
    cwd: PROJECT_ROOT,
    stdio: 'inherit',
    shell: false,
  });
  const exitCode = result.status ?? 1;
  // Semgrep/Detekt often exit 1 when findings exist; treat report presence as success.
  if (exitCode === 0 || exitCode === 1) {
    return;
  }
  console.error(`[${KEYWORD}] ${label} failed with exit code ${exitCode}`);
  process.exit(exitCode);
}

function copyIfExists(fromPath, toPath) {
  if (existsSync(fromPath)) {
    copyFileSync(fromPath, toPath);
  }
}

function parseJsonReport(stdout) {
  if (!stdout || !stdout.trim()) {
    return null;
  }
  const trimmed = stdout.trim();
  const firstBrace = trimmed.indexOf('{');
  const lastBrace = trimmed.lastIndexOf('}');
  if (firstBrace === -1 || lastBrace === -1 || lastBrace <= firstBrace) {
    return null;
  }
  try {
    return JSON.parse(trimmed.slice(firstBrace, lastBrace + 1));
  } catch {
    return null;
  }
}

function loadReactDoctor(filePath) {
  if (!existsSync(filePath)) {
    return [];
  }
  const report = tryReadJson(filePath);
  const diagnostics = Array.isArray(report?.diagnostics) ? report.diagnostics : [];
  return diagnostics.map(item => ({
    tool: 'react-doctor',
    rule: String(item.rule || 'unknown'),
    category: String(item.category || 'Unknown'),
    severity: String(item.severity || 'warning').toLowerCase(),
    message: String(item.message || item.title || ''),
    filePath: toProjectRelative(item.filePath || item.normalizedFilePath || ''),
    line: Number(item.line || 0),
  }));
}

function loadSemgrep(filePath) {
  if (!existsSync(filePath)) {
    return [];
  }
  const report = tryReadJson(filePath);
  const results = Array.isArray(report?.results) ? report.results : [];
  return results.map(item => ({
    tool: 'semgrep',
    rule: String(item.check_id || 'semgrep/unknown'),
    category: 'Security',
    severity: String(item?.extra?.severity || 'WARNING').toLowerCase(),
    message: String(item?.extra?.message || 'Semgrep finding'),
    filePath: toProjectRelative(item.path || ''),
    line: Number(item?.start?.line || 0),
  }));
}

function loadDetektSarif(filePath) {
  if (!existsSync(filePath)) {
    return [];
  }
  const report = tryReadJson(filePath);
  const runs = Array.isArray(report?.runs) ? report.runs : [];
  const findings = [];
  for (const run of runs) {
    const rules = run?.tool?.driver?.rules || [];
    const ruleNameById = new Map(
      rules.map(rule => [rule.id, rule.name || rule.shortDescription?.text || rule.id]),
    );
    const results = Array.isArray(run?.results) ? run.results : [];
    for (const result of results) {
      const location = result.locations?.[0]?.physicalLocation;
      const region = location?.region || {};
      const ruleIdRaw = result.ruleId || 'detekt/unknown';
      findings.push({
        tool: 'detekt',
        rule: String(ruleNameById.get(ruleIdRaw) || ruleIdRaw),
        category: 'Maintainability',
        severity: String(result.level || 'warning').toLowerCase(),
        message: String(result.message?.text || 'Detekt finding'),
        filePath: toProjectRelative(location?.artifactLocation?.uri || ''),
        line: Number(region.startLine || 0),
      });
    }
  }
  return findings;
}

function tryReadJson(filePath) {
  try {
    return JSON.parse(readFileSync(filePath, 'utf8').replace(/^\uFEFF/, ''));
  } catch {
    return null;
  }
}

function toProjectRelative(value) {
  const raw = String(value || '').replace(/\\/g, '/');
  if (!raw) {
    return '(unknown)';
  }
  if (raw.startsWith('file:///')) {
    return toProjectRelative(decodeURIComponent(raw.slice('file:///'.length)));
  }
  // Windows file:///C:/...
  if (/^file:\/\/\/[A-Za-z]:\//.test(String(value || ''))) {
    return toProjectRelative(decodeURIComponent(String(value).slice('file:///'.length)));
  }
  try {
    const absolute = resolve(PROJECT_ROOT, raw);
    if (absolute.toLowerCase().startsWith(PROJECT_ROOT.toLowerCase())) {
      return relative(PROJECT_ROOT, absolute).replace(/\\/g, '/');
    }
  } catch {
    // fall through
  }
  const marker = '/ezkey_mobile/';
  const idx = raw.toLowerCase().indexOf(marker);
  if (idx >= 0) {
    return raw.slice(idx + marker.length);
  }
  return raw.replace(/^\/+/, '');
}

function loadSuppressions(path) {
  if (!existsSync(path)) {
    return [];
  }
  try {
    const json = JSON.parse(readFileSync(path, 'utf8'));
    const list = Array.isArray(json.suppressions) ? json.suppressions : [];
    return list
      .map(entry => ({
        tool: String(entry.tool || '').trim().toLowerCase(),
        rule: String(entry.rule || entry.ruleId || '').trim(),
        reason: String(entry.reason || '').trim() || 'No reason provided',
        pathFragment: String(entry.pathFragment || entry.path || '')
          .trim()
          .replace(/\\/g, '/')
          .toLowerCase(),
      }))
      .filter(entry => entry.rule);
  } catch {
    console.error(`WARNING: could not parse ${path}`);
    return [];
  }
}

function matchSuppression(diagnostic, suppressions) {
  const file = normalizePath(diagnostic.filePath);
  const rule = diagnostic.rule;
  const shortRule = rule.includes('.')
    ? rule.slice(rule.lastIndexOf('.') + 1)
    : rule.includes('/')
      ? rule.slice(rule.lastIndexOf('/') + 1)
      : rule;

  for (const suppression of suppressions) {
    if (suppression.tool && suppression.tool !== diagnostic.tool) {
      continue;
    }
    if (suppression.rule !== rule && suppression.rule !== shortRule) {
      continue;
    }
    if (suppression.pathFragment && !file.includes(suppression.pathFragment)) {
      continue;
    }
    return suppression;
  }
  return null;
}

function normalizePath(value) {
  return String(value || '')
    .replace(/\\/g, '/')
    .toLowerCase();
}

function priorityFor(diagnostic) {
  if (diagnostic.tool === 'semgrep') {
    if (diagnostic.severity === 'error' || diagnostic.severity === 'high') {
      return 'P1';
    }
    return 'P2';
  }

  if (diagnostic.tool === 'react-doctor') {
    if (diagnostic.severity === 'error') {
      return 'P1';
    }
    if (
      diagnostic.category === 'Correctness' ||
      diagnostic.category === 'Accessibility' ||
      diagnostic.rule === 'exhaustive-deps' ||
      diagnostic.rule === 'async-await-in-loop'
    ) {
      return 'P1';
    }
    if (
      diagnostic.category === 'Performance' ||
      diagnostic.category === 'Bugs' ||
      diagnostic.category === 'State & Effects'
    ) {
      // High-volume preference rules stay visible but lean P3
      if (
        diagnostic.rule === 'rn-prefer-pressable' ||
        diagnostic.rule === 'no-barrel-import' ||
        diagnostic.rule === 'rn-no-single-element-style-array'
      ) {
        return 'P3';
      }
      return 'P2';
    }
    return 'P3';
  }

  // Detekt — design/maintainability leans P2/P3
  if (diagnostic.severity === 'error') {
    return 'P2';
  }
  return 'P3';
}

function summarize(diagnostics) {
  const affectedFiles = new Set();
  let errorCount = 0;
  let warningCount = 0;
  for (const diagnostic of diagnostics) {
    if (diagnostic.filePath) {
      affectedFiles.add(diagnostic.filePath);
    }
    if (diagnostic.severity === 'error' || diagnostic.severity === 'high') {
      errorCount += 1;
    } else {
      warningCount += 1;
    }
  }
  return {
    totalDiagnosticCount: diagnostics.length,
    errorCount,
    warningCount,
    affectedFileCount: affectedFiles.size,
  };
}

function buildCountGroups(diagnostics, field) {
  const counts = new Map();
  for (const diagnostic of diagnostics) {
    const key = diagnostic[field] || '(unknown)';
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([name, count]) => ({name, count}))
    .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name));
}

function buildRuleGroups(diagnostics) {
  const groups = new Map();
  for (const diagnostic of diagnostics) {
    const key = `${diagnostic.tool}::${diagnostic.rule}`;
    if (!groups.has(key)) {
      groups.set(key, {
        tool: diagnostic.tool,
        rule: diagnostic.rule,
        count: 0,
        category: diagnostic.category || 'Unknown',
        maxSeverity: diagnostic.severity || 'warning',
        priority: diagnostic.priority || 'P3',
        examples: [],
      });
    }
    const group = groups.get(key);
    group.count += 1;
    if (severityRank(diagnostic.severity) > severityRank(group.maxSeverity)) {
      group.maxSeverity = diagnostic.severity;
    }
    if (priorityRank(diagnostic.priority) < priorityRank(group.priority)) {
      group.priority = diagnostic.priority;
    }
    if (group.examples.length < 3) {
      group.examples.push({
        filePath: diagnostic.filePath || '(unknown)',
        line: diagnostic.line || 0,
        severity: diagnostic.severity || 'warning',
        message: diagnostic.message || '',
      });
    }
  }
  return [...groups.values()].sort(
    (left, right) =>
      priorityRank(left.priority) - priorityRank(right.priority) ||
      severityRank(right.maxSeverity) - severityRank(left.maxSeverity) ||
      right.count - left.count ||
      left.rule.localeCompare(right.rule),
  );
}

function buildSuppressedGroups(suppressed) {
  const groups = new Map();
  for (const diagnostic of suppressed) {
    const key = `${diagnostic.tool}::${diagnostic.rule}`;
    if (!groups.has(key)) {
      groups.set(key, {
        tool: diagnostic.tool,
        rule: diagnostic.rule,
        count: 0,
        reason: diagnostic.suppressionReason || '',
      });
    }
    groups.get(key).count += 1;
  }
  return [...groups.values()].sort((a, b) => b.count - a.count || a.rule.localeCompare(b.rule));
}

function toFindingRecord(diagnostic) {
  return {
    tool: diagnostic.tool,
    rule: diagnostic.rule,
    priority: diagnostic.priority,
    category: diagnostic.category,
    severity: diagnostic.severity,
    filePath: diagnostic.filePath,
    line: diagnostic.line,
    message: diagnostic.message,
  };
}

function severityRank(severity) {
  const value = String(severity || '').toLowerCase();
  if (value === 'error' || value === 'high') {
    return 2;
  }
  return 1;
}

function priorityRank(priority) {
  if (priority === 'P1') {
    return 1;
  }
  if (priority === 'P2') {
    return 2;
  }
  return 3;
}

function renderMarkdown({
  rawSummary,
  curatedSummary,
  toolGroups,
  categoryGroups,
  fileGroups,
  ruleGroups,
  suppressedRuleGroups,
}) {
  const lines = [
    '# Mobile Doctor Curated Report',
    '',
    `Keyword: ${KEYWORD}`,
    `Generated: ${new Date().toISOString()}`,
    'Analyzers: react-doctor + Semgrep (Ezkey mobile pack) + Detekt',
    'Mode: punctual hygiene pass — not a CI gate, not a zero-warning campaign',
    '',
    '## How to use this report',
    '',
    '- Treat this report as triage, not as ground truth.',
    '- Revalidate each retained finding in the code before planning or editing.',
    '- Prefer small local fixes with strong signal-to-effort ratio over broad refactors.',
    '- Hygiene trace: dedicated branch + PR; do not invent I-*/TB-*/TSP-* for a routine polish pass.',
    '- Before implementing: HITL one finding at a time (see ezkey_mobile/AGENTS.md § Mobile doctor-curated).',
    '- Be especially careful in crypto / keystore / proof-token zones.',
    '- After high-signal picks, include a modest low-signal allotment (1–3 cheap P2/P3 items).',
    '',
    '## Raw summary',
    '',
    `- Diagnostics: ${rawSummary.totalDiagnosticCount}`,
    `- Errors/high: ${rawSummary.errorCount}`,
    `- Warnings/other: ${rawSummary.warningCount}`,
    `- Affected files: ${rawSummary.affectedFileCount}`,
    '',
    '## Curated summary',
    '',
    `- Diagnostics kept: ${curatedSummary.totalDiagnosticCount}`,
    `- Errors/high kept: ${curatedSummary.errorCount}`,
    `- Warnings/other kept: ${curatedSummary.warningCount}`,
    `- Affected files kept: ${curatedSummary.affectedFileCount}`,
    '',
    '## By tool (curated)',
    '',
  ];

  for (const group of toolGroups) {
    lines.push(`- ${group.name}: ${group.count}`);
  }
  if (toolGroups.length === 0) {
    lines.push('- None');
  }

  lines.push('', '## Suppressed low-signal rules', '');
  for (const group of suppressedRuleGroups) {
    lines.push(`- [${group.tool}] ${group.rule}: ${group.count} — ${group.reason}`);
  }
  if (suppressedRuleGroups.length === 0) {
    lines.push('- None');
  }

  lines.push('', '## Top categories', '');
  for (const group of categoryGroups.slice(0, 10)) {
    lines.push(`- ${group.name}: ${group.count}`);
  }

  lines.push('', '## Top files', '');
  for (const group of fileGroups.slice(0, 10)) {
    lines.push(`- ${group.name}: ${group.count}`);
  }

  for (const priority of ['P1', 'P2', 'P3']) {
    lines.push('', `## ${priority} — ${PRIORITY_LABELS[priority]}`, '');
    const matchingGroups = ruleGroups.filter(group => group.priority === priority).slice(0, 10);
    if (matchingGroups.length === 0) {
      lines.push('- None');
      continue;
    }
    for (const group of matchingGroups) {
      lines.push(
        `- [${group.tool}] ${group.rule}: ${group.count} (${group.category}, max ${group.maxSeverity})`,
      );
      for (const example of group.examples) {
        const location = example.line > 0 ? `${example.filePath}:${example.line}` : example.filePath;
        lines.push(`  Example: ${location} — ${example.message}`);
      }
    }
  }

  lines.push(
    '',
    '## Planning contract',
    '',
    '- Start with P1 only unless the task explicitly widens scope.',
    '- Keep the first lot small: usually 3 to 6 items maximum.',
    '- HITL: iterate one finding at a time; do not ask for a bulk options-matrix reply.',
    '- For each retained item, state why it matters in Ezkey Mobile, files involved, and narrow validation.',
    '- Record decisions in product-docs/global/hygiene/mobile-doctor/ (copy TEMPLATE.md).',
    '- Carry the briefing into the PR description.',
    '- Reserve a modest low-signal allotment after high-signal work.',
    '',
    '## Operating guidance',
    '',
    '- Use this report for a small polishing pass, not a zero-warning campaign.',
    '- Prefer fixing P1 and then a narrow subset of P2 in touched files.',
    '- Revisit suppressed rules only if they become locally relevant.',
    '- Campaign notes: product-docs/global/hygiene/mobile-doctor/',
  );

  return `${lines.join('\n')}\n`;
}
