#!/usr/bin/env node
/**
 * pam-doctor-curated — normalize clang-format, cppcheck, and clang-tidy raw
 * output, dedupe, apply suppressions, weight P1/P2/P3, emit curated MD+JSON.
 *
 * Invoked by scripts/pam-doctor-curated.sh. Report-only; not a build gate.
 */

import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(SCRIPT_DIR, '..');
const OUTPUT_DIR = join(REPO_ROOT, 'logs', 'pam-doctor');
const RAW_DIR = join(OUTPUT_DIR, 'raw');
const SUPPRESSIONS_PATH = join(REPO_ROOT, 'config', 'pam-doctor', 'suppressions.json');
const KEYWORD = 'pam-doctor-curated';

const PRIORITY_LABELS = {
  P1: 'Review now',
  P2: 'Good next pass',
  P3: 'Defer unless touching the area',
};

main();

function main() {
  mkdirSync(OUTPUT_DIR, { recursive: true });

  const loaded = [
    ...loadClangFormat(join(RAW_DIR, 'clang-format.tsv')),
    ...loadCppcheck(join(RAW_DIR, 'cppcheck.txt')),
    ...loadClangTidy(join(RAW_DIR, 'clang-tidy.txt')),
  ];
  const diagnostics = dedupe(loaded);
  const suppressions = loadSuppressions(SUPPRESSIONS_PATH);
  const suppressed = [];
  const curated = [];

  for (const diagnostic of diagnostics) {
    const match = matchSuppression(diagnostic, suppressions);
    if (match) {
      suppressed.push({ ...diagnostic, suppressionReason: match.reason });
    } else {
      curated.push({ ...diagnostic, priority: priorityFor(diagnostic) });
    }
  }

  const ruleGroups = buildRuleGroups(curated);
  const suppressedRuleGroups = buildSuppressedGroups(suppressed);
  const fileGroups = buildCountGroups(curated, 'filePath');
  const toolGroups = buildCountGroups(curated, 'tool');
  const summary = {
    keyword: KEYWORD,
    generatedAt: new Date().toISOString(),
    versions: readOptional(join(RAW_DIR, 'versions.txt')),
    projectRoot: REPO_ROOT,
    scope: 'ezkey-pam/src (Linux reference; macOS install track excluded)',
    rawBeforeDedupe: summarize(loaded),
    raw: summarize(diagnostics),
    duplicatesRemoved: loaded.length - diagnostics.length,
    curated: summarize(curated),
    suppressedRules: suppressedRuleGroups,
    topRules: ruleGroups.slice(0, 20),
    topFiles: fileGroups.slice(0, 15),
    tools: toolGroups,
    findings: curated.slice(0, 200).map(toFindingRecord),
  };

  const curatedJsonPath = join(OUTPUT_DIR, 'pam-doctor.curated.json');
  const curatedMdPath = join(OUTPUT_DIR, 'pam-doctor.curated.md');
  writeFileSync(curatedJsonPath, `${JSON.stringify(summary, null, 2)}\n`, 'utf8');
  writeFileSync(
    curatedMdPath,
    renderMarkdown({
      versions: summary.versions,
      rawBefore: summary.rawBeforeDedupe,
      rawSummary: summary.raw,
      duplicatesRemoved: summary.duplicatesRemoved,
      curatedSummary: summary.curated,
      toolGroups,
      fileGroups,
      ruleGroups,
      suppressedRuleGroups,
    }),
    'utf8',
  );

  console.log(`${KEYWORD} curated.`);
  console.log(`Raw diagnostics: ${loaded.length} (deduped ${diagnostics.length}, removed ${summary.duplicatesRemoved})`);
  console.log(`Curated kept: ${curated.length}`);
  console.log(`Suppressed: ${suppressed.length}`);
  console.log(`Markdown: ${curatedMdPath}`);
  console.log(`JSON: ${curatedJsonPath}`);

  for (const group of ruleGroups.slice(0, 8)) {
    console.log(`- ${group.priority} [${group.tool}] ${group.rule}: ${group.count}`);
  }
}

function readOptional(path) {
  if (!existsSync(path)) {
    return '';
  }
  return readFileSync(path, 'utf8').trim();
}

function repoPath(containerPath) {
  const normalized = containerPath.replace(/\\/g, '/');
  const marker = '/src/src/';
  const idx = normalized.indexOf(marker);
  if (idx >= 0) {
    return `ezkey-pam/src/${normalized.slice(idx + marker.length)}`;
  }
  if (normalized.startsWith('src/')) {
    return `ezkey-pam/${normalized}`;
  }
  return normalized.replace(/^\.\//, '');
}

function dedupe(diagnostics) {
  const seen = new Set();
  const out = [];
  for (const diagnostic of diagnostics) {
    const key = [
      diagnostic.tool,
      diagnostic.rule,
      diagnostic.filePath,
      String(diagnostic.line || 0),
      diagnostic.message,
    ].join('\u001f');
    if (seen.has(key)) {
      continue;
    }
    seen.add(key);
    out.push(diagnostic);
  }
  return out;
}

function loadClangFormat(path) {
  if (!existsSync(path)) {
    console.error('WARNING: clang-format.tsv missing');
    return [];
  }
  const out = [];
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    if (!line.trim()) {
      continue;
    }
    const [file, count] = line.split('\t');
    const replacements = Number(count || 0);
    out.push({
      tool: 'clang-format',
      rule: 'google-format-diff',
      severity: 'warning',
      category: 'layout',
      filePath: repoPath(file),
      line: 1,
      message: `${replacements} clang-format replacement(s) vs BasedOnStyle: Google`,
    });
  }
  return out;
}

function loadCppcheck(path) {
  if (!existsSync(path)) {
    console.error('WARNING: cppcheck.txt missing');
    return [];
  }
  const out = [];
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    if (!line.trim() || line.startsWith('Checking ') || line.startsWith('(information)')) {
      continue;
    }
    const parts = line.split('\t');
    if (parts.length < 5) {
      continue;
    }
    const [file, lineNo, severity, id, ...rest] = parts;
    if (!file || !id || Number.isNaN(Number(lineNo))) {
      continue;
    }
    out.push({
      tool: 'cppcheck',
      rule: id,
      severity: severity === 'error' ? 'error' : 'warning',
      category: severity || 'warning',
      filePath: repoPath(file),
      line: Number(lineNo),
      message: rest.join('\t'),
    });
  }
  return out;
}

function loadClangTidy(path) {
  if (!existsSync(path)) {
    console.error('WARNING: clang-tidy.txt missing');
    return [];
  }
  const out = [];
  const pattern = /^(.+):(\d+):\d+: (warning|error): (.*) \[([^\]]+)\]$/;
  for (const line of readFileSync(path, 'utf8').split(/\r?\n/)) {
    const match = pattern.exec(line);
    if (!match) {
      continue;
    }
    const rule = match[5];
    if (rule.startsWith('clang-diagnostic-')) {
      continue;
    }
    out.push({
      tool: 'clang-tidy',
      rule,
      severity: match[3] === 'error' ? 'error' : 'warning',
      category: tidyCategory(rule),
      filePath: repoPath(match[1]),
      line: Number(match[2]),
      message: match[4],
    });
  }
  return out;
}

function tidyCategory(rule) {
  if (rule.startsWith('clang-analyzer-')) {
    return 'analyzer';
  }
  if (rule.startsWith('cert-')) {
    return 'cert';
  }
  if (rule.startsWith('bugprone-')) {
    return 'bugprone';
  }
  return 'other';
}

function loadSuppressions(path) {
  if (!existsSync(path)) {
    return [];
  }
  const parsed = JSON.parse(readFileSync(path, 'utf8'));
  return Array.isArray(parsed.suppressions) ? parsed.suppressions : [];
}

function matchSuppression(diagnostic, suppressions) {
  for (const entry of suppressions) {
    if (entry.tool && entry.tool !== diagnostic.tool) {
      continue;
    }
    if (entry.rule && entry.rule !== diagnostic.rule) {
      continue;
    }
    if (entry.pathFragment && !String(diagnostic.filePath || '').includes(entry.pathFragment)) {
      continue;
    }
    if (entry.reason) {
      return entry;
    }
  }
  return null;
}

function priorityFor(diagnostic) {
  if (diagnostic.tool === 'clang-format') {
    return 'P2';
  }
  if (diagnostic.severity === 'error') {
    return 'P1';
  }
  if (diagnostic.tool === 'clang-tidy' && diagnostic.category === 'analyzer') {
    return 'P1';
  }
  if (diagnostic.tool === 'cppcheck' && diagnostic.category === 'warning') {
    return 'P2';
  }
  if (diagnostic.tool === 'clang-tidy' && (diagnostic.category === 'cert' || diagnostic.category === 'bugprone')) {
    return 'P2';
  }
  return 'P3';
}

function summarize(diagnostics) {
  const files = new Set();
  let errorCount = 0;
  let warningCount = 0;
  for (const diagnostic of diagnostics) {
    if (diagnostic.filePath) {
      files.add(diagnostic.filePath);
    }
    if (diagnostic.severity === 'error') {
      errorCount += 1;
    } else {
      warningCount += 1;
    }
  }
  return {
    totalDiagnosticCount: diagnostics.length,
    errorCount,
    warningCount,
    affectedFileCount: files.size,
  };
}

function buildCountGroups(diagnostics, field) {
  const counts = new Map();
  for (const diagnostic of diagnostics) {
    const key = diagnostic[field] || '(unknown)';
    counts.set(key, (counts.get(key) ?? 0) + 1);
  }
  return [...counts.entries()]
    .map(([name, count]) => ({ name, count }))
    .sort((a, b) => b.count - a.count || a.name.localeCompare(b.name));
}

function priorityRank(priority) {
  if (priority === 'P1') return 0;
  if (priority === 'P2') return 1;
  return 2;
}

function severityRank(severity) {
  return severity === 'error' ? 2 : 1;
}

function buildRuleGroups(diagnostics) {
  const groups = new Map();
  for (const diagnostic of diagnostics) {
    const key = `${diagnostic.tool}:${diagnostic.rule}`;
    if (!groups.has(key)) {
      groups.set(key, {
        tool: diagnostic.tool,
        rule: diagnostic.rule,
        count: 0,
        category: diagnostic.category,
        maxSeverity: diagnostic.severity,
        priority: diagnostic.priority,
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
        message: diagnostic.message || '',
      });
    }
  }
  return [...groups.values()].sort(
    (a, b) =>
      priorityRank(a.priority) - priorityRank(b.priority) ||
      severityRank(b.maxSeverity) - severityRank(a.maxSeverity) ||
      b.count - a.count ||
      a.rule.localeCompare(b.rule),
  );
}

function buildSuppressedGroups(suppressed) {
  const groups = new Map();
  for (const diagnostic of suppressed) {
    const key = `${diagnostic.tool}:${diagnostic.rule}`;
    if (!groups.has(key)) {
      groups.set(key, {
        tool: diagnostic.tool,
        rule: diagnostic.rule,
        count: 0,
        reason: diagnostic.suppressionReason,
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
    severity: diagnostic.severity,
    category: diagnostic.category,
    filePath: diagnostic.filePath,
    line: diagnostic.line,
    message: diagnostic.message,
  };
}

function renderMarkdown({
  versions,
  rawBefore,
  rawSummary,
  duplicatesRemoved,
  curatedSummary,
  toolGroups,
  fileGroups,
  ruleGroups,
  suppressedRuleGroups,
}) {
  const lines = [
    '# PAM Doctor Curated Report',
    '',
    `Keyword: ${KEYWORD}`,
    `Generated: ${new Date().toISOString()}`,
    'Analyzers: clang-format (Google preset, dry-run) + clang-tidy (analyzer / bugprone / CERT) + cppcheck (warning, performance, portability)',
    'Scope: ezkey-pam/src — Linux reference. macOS install track is out of this pass.',
    'Mode: report-only — not a CI gate',
    '',
    '## Tool versions',
    '',
    '```',
    versions || '(versions.txt missing)',
    '```',
    '',
    '## How to use this report',
    '',
    '- Treat this report as triage, not as ground truth.',
    '- Revalidate each retained finding in the code before planning or editing.',
    '- Deduped rows are grouped by tool + rule. Do not open one issue per hit.',
    '- clang-analyzer / cppcheck error → lean P1. CERT and bugprone → lean P2. Portability and performance → lean P3.',
    '- clang-format drift is one layout baseline (P2), not a defect list.',
    '- Not a CI gate. Do not invent I-* / TB-* per finding.',
    '- Before any source change: one finding at a time with the operator. Existing `make test` harnesses are the regression net; add a case only when an accepted fix changes a decision the harness does not already pin.',
    '',
    '## Raw summary',
    '',
    `- Diagnostics before dedupe: ${rawBefore.totalDiagnosticCount}`,
    `- Duplicates removed: ${duplicatesRemoved}`,
    `- Diagnostics after dedupe: ${rawSummary.totalDiagnosticCount}`,
    `- Errors: ${rawSummary.errorCount}`,
    `- Warnings: ${rawSummary.warningCount}`,
    `- Affected files: ${rawSummary.affectedFileCount}`,
    '',
    '## Curated summary',
    '',
    `- Kept: ${curatedSummary.totalDiagnosticCount}`,
    `- Errors: ${curatedSummary.errorCount}`,
    `- Warnings: ${curatedSummary.warningCount}`,
    `- Affected files: ${curatedSummary.affectedFileCount}`,
    '',
    '## By tool',
    '',
    ...bulletCounts(toolGroups),
    '',
    '## Top rules',
    '',
    ...ruleGroups.slice(0, 20).flatMap((group) => {
      const example = group.examples[0];
      const where = example ? `${example.filePath}:${example.line}` : '(no example)';
      return [
        `### ${group.priority} — ${group.rule} (${group.count})`,
        '',
        `- Tool: ${group.tool}`,
        `- Category: ${group.category}`,
        `- ${PRIORITY_LABELS[group.priority] || group.priority}`,
        `- Example: \`${where}\` — ${example ? example.message : ''}`,
        '',
      ];
    }),
    '## Files',
    '',
    ...bulletCounts(fileGroups.slice(0, 15)),
    '',
    '## Suppressed',
    '',
  ];

  if (suppressedRuleGroups.length === 0) {
    lines.push('- None.', '');
  } else {
    for (const group of suppressedRuleGroups) {
      lines.push(`- ${group.tool} \`${group.rule}\` × ${group.count} — ${group.reason}`);
    }
    lines.push('');
  }

  lines.push(
    '## Test posture',
    '',
    'Do not add tests for a finding that is left or suppressed.',
    'When a fix is accepted, name the existing harness (`test-http`, `test-conf`, `test-decision`, `test-sanitize`, `test-filelog`, `test-challenge`, `test-budget`, `test-session`, `test-diag`, plus the stub scripts) and extend it only if the change is not already pinned.',
    '',
  );

  return `${lines.join('\n')}\n`;
}

function bulletCounts(groups) {
  if (groups.length === 0) {
    return ['- None.'];
  }
  return groups.map((group) => `- ${group.name}: ${group.count}`);
}
