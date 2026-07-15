import { spawnSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = resolve(SCRIPT_DIR, '..');
const OUTPUT_DIR = join(PROJECT_ROOT, 'logs', 'react-doctor');
const RAW_REPORT_PATH = join(OUTPUT_DIR, 'react-doctor.raw.json');
const CURATED_REPORT_PATH = join(OUTPUT_DIR, 'react-doctor.curated.md');
const CURATED_JSON_PATH = join(OUTPUT_DIR, 'react-doctor.curated.json');
const STDERR_PATH = join(OUTPUT_DIR, 'react-doctor.stderr.log');
const KEYWORD = 'doctor-curated';

const SUPPRESSED_RULES = new Map([
  [
    'unused-file',
    'Current react-doctor reachability is noisy for this Vite route graph; treat as separate cleanup work.',
  ],
  [
    'design-no-em-dash-in-jsx-text',
    'Typography preference, not a product-risk signal for an Admin UI polishing pass.',
  ],
  [
    'only-export-components',
    'Fast Refresh ergonomics warning; useful later, but not a high-signal release-risk item for now.',
  ],
  [
    'no-impure-state-updater',
    'False positives on event handlers and mutation onSuccess that call setState (often with storage/toast beside setState). Not impure functional updaters; leave patterns as-is (pass-1 HITL).',
  ],
]);

const PRIORITY_LABELS = {
  P1: 'Review now',
  P2: 'Good next pass',
  P3: 'Defer unless touching the area',
};

main();

function main() {
  mkdirSync(OUTPUT_DIR, { recursive: true });

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

  writeFileSync(STDERR_PATH, result.stderr ?? '', 'utf8');

  if (result.error) {
    console.error('doctor-curated failed to start react-doctor.');
    console.error(result.error.message);
    process.exit(1);
  }

  const report = parseJsonReport(result.stdout);
  if (!report) {
    console.error('doctor-curated could not parse react-doctor JSON output.');
    if (result.stdout) {
      console.error(result.stdout.slice(0, 2000));
    }
    process.exit(result.status ?? 1);
  }

  writeFileSync(RAW_REPORT_PATH, `${JSON.stringify(report, null, 2)}\n`, 'utf8');

  const diagnostics = Array.isArray(report.diagnostics) ? report.diagnostics : [];
  const suppressedDiagnostics = diagnostics.filter((diagnostic) =>
    SUPPRESSED_RULES.has(diagnostic.rule),
  );
  const curatedDiagnostics = diagnostics.filter(
    (diagnostic) => !SUPPRESSED_RULES.has(diagnostic.rule),
  );

  const curatedRuleGroups = buildRuleGroups(curatedDiagnostics);
  const curatedCategoryGroups = buildCountGroups(curatedDiagnostics, 'category');
  const curatedFileGroups = buildCountGroups(curatedDiagnostics, 'filePath');
  const suppressedRuleGroups = buildRuleGroups(suppressedDiagnostics);

  const curatedSummary = {
    keyword: KEYWORD,
    generatedAt: new Date().toISOString(),
    projectRoot: PROJECT_ROOT,
    raw: summarizeDiagnostics(diagnostics),
    curated: summarizeDiagnostics(curatedDiagnostics),
    suppressedRules: suppressedRuleGroups.map((group) => ({
      rule: group.rule,
      count: group.count,
      reason: SUPPRESSED_RULES.get(group.rule),
    })),
    topRules: curatedRuleGroups.slice(0, 12),
    topFiles: curatedFileGroups.slice(0, 10),
    categories: curatedCategoryGroups,
  };

  writeFileSync(CURATED_JSON_PATH, `${JSON.stringify(curatedSummary, null, 2)}\n`, 'utf8');
  writeFileSync(
    CURATED_REPORT_PATH,
    renderMarkdownReport({
      rawSummary: curatedSummary.raw,
      curatedSummary: curatedSummary.curated,
      categoryGroups: curatedCategoryGroups,
      fileGroups: curatedFileGroups,
      ruleGroups: curatedRuleGroups,
      suppressedRuleGroups: curatedSummary.suppressedRules,
    }),
    'utf8',
  );

  console.log('doctor-curated completed.');
  console.log(`Raw report: ${RAW_REPORT_PATH}`);
  console.log(`Curated JSON: ${CURATED_JSON_PATH}`);
  console.log(`Curated Markdown: ${CURATED_REPORT_PATH}`);
  console.log(
    `Curated diagnostics kept: ${curatedSummary.curated.totalDiagnosticCount}/${curatedSummary.raw.totalDiagnosticCount}`,
  );

  for (const group of curatedRuleGroups.slice(0, 5)) {
    console.log(
      `- ${group.priority} ${group.rule}: ${group.count} (${group.category}, ${group.maxSeverity})`,
    );
  }

  process.exit(result.status ?? 0);
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

function summarizeDiagnostics(diagnostics) {
  const affectedFiles = new Set();
  let errorCount = 0;
  let warningCount = 0;

  for (const diagnostic of diagnostics) {
    if (diagnostic.filePath) {
      affectedFiles.add(diagnostic.filePath);
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
    .map(([name, count]) => ({ name, count }))
    .sort((left, right) => right.count - left.count || left.name.localeCompare(right.name));
}

function buildRuleGroups(diagnostics) {
  const groups = new Map();

  for (const diagnostic of diagnostics) {
    const key = diagnostic.rule || '(unknown-rule)';
    if (!groups.has(key)) {
      groups.set(key, {
        rule: key,
        count: 0,
        category: diagnostic.category || 'Unknown',
        maxSeverity: diagnostic.severity || 'warning',
        priority: priorityForDiagnostic(diagnostic),
        examples: [],
      });
    }

    const group = groups.get(key);
    group.count += 1;
    if (severityRank(diagnostic.severity) > severityRank(group.maxSeverity)) {
      group.maxSeverity = diagnostic.severity;
    }
    if (priorityRank(priorityForDiagnostic(diagnostic)) < priorityRank(group.priority)) {
      group.priority = priorityForDiagnostic(diagnostic);
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

function priorityForDiagnostic(diagnostic) {
  if (diagnostic.severity === 'error') {
    return 'P1';
  }

  if (
    diagnostic.category === 'Correctness' ||
    diagnostic.category === 'Accessibility' ||
    diagnostic.rule === 'no-adjust-state-on-prop-change' ||
    diagnostic.rule === 'no-react19-deprecated-apis' ||
    diagnostic.rule === 'button-has-type'
  ) {
    return 'P1';
  }

  if (
    diagnostic.category === 'State & Effects' ||
    diagnostic.category === 'Performance' ||
    diagnostic.rule === 'no-event-handler' ||
    diagnostic.rule === 'jsx-no-jsx-as-prop' ||
    diagnostic.rule === 'prefer-useReducer'
  ) {
    return 'P2';
  }

  return 'P3';
}

function severityRank(severity) {
  return severity === 'error' ? 2 : 1;
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

function renderMarkdownReport({
  rawSummary,
  curatedSummary,
  categoryGroups,
  fileGroups,
  ruleGroups,
  suppressedRuleGroups,
}) {
  const lines = [
    '# React Doctor Curated Report',
    '',
    `Keyword: ${KEYWORD}`,
    `Generated: ${new Date().toISOString()}`,
    'Mode: scope full, score disabled, blocking none',
    '',
    '## How to use this report',
    '',
    '- Treat this report as triage, not as ground truth.',
    '- Revalidate each retained finding in the code before planning or editing.',
    '- Prefer small local fixes with strong signal-to-effort ratio over broad refactors.',
    '- Separate immediate fixes from issues that need a design decision.',
    '- Produce a short prioritized plan with narrow validation steps, not an exhaustive warning chase.',
    '- Hygiene trace: dedicated branch + PR; do not invent I-*/TB-*/TSP-* for a routine polish pass.',
    '- Before implementing: give the maintainer a short continuous-learning briefing on each retained item (why it matters in Admin UI, not a React course). See ezkey-admin-ui/AGENTS.md § React Doctor curated pass.',
    '- When opening the PR: reuse that same briefing in the PR body (durable trace; do not write a second essay).',
    '- After high-signal picks, include a modest low-signal allotment (1–3 cheap continuous-improvement items) so light polish is not deferred forever.',
    '',
    '## Raw summary',
    '',
    `- Diagnostics: ${rawSummary.totalDiagnosticCount}`,
    `- Errors: ${rawSummary.errorCount}`,
    `- Warnings: ${rawSummary.warningCount}`,
    `- Affected files: ${rawSummary.affectedFileCount}`,
    '',
    '## Curated summary',
    '',
    `- Diagnostics kept: ${curatedSummary.totalDiagnosticCount}`,
    `- Errors kept: ${curatedSummary.errorCount}`,
    `- Warnings kept: ${curatedSummary.warningCount}`,
    `- Affected files kept: ${curatedSummary.affectedFileCount}`,
    '',
    '## Suppressed low-signal rules',
    '',
  ];

  for (const group of suppressedRuleGroups) {
    lines.push(`- ${group.rule}: ${group.count} - ${group.reason}`);
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
    lines.push('', `## ${priority} - ${PRIORITY_LABELS[priority]}`, '');
    const matchingGroups = ruleGroups.filter((group) => group.priority === priority).slice(0, 8);

    if (matchingGroups.length === 0) {
      lines.push('- None');
      continue;
    }

    for (const group of matchingGroups) {
      lines.push(
        `- ${group.rule}: ${group.count} (${group.category}, max severity ${group.maxSeverity})`,
      );
      for (const example of group.examples) {
        const location = example.line > 0 ? `${example.filePath}:${example.line}` : example.filePath;
        lines.push(`  Example: ${location} - ${example.message}`);
      }
    }
  }

  lines.push(
    '',
    '## Planning contract',
    '',
    '- Start with P1 only unless the task explicitly widens scope.',
    '- Keep the first plan small: usually 3 to 5 items maximum.',
    '- Propose a short lot overview (rule + location hint), then interactive HITL: one finding at a time — wait for Go / No-Go / suppress / skip before the next item. Do not use a dense options matrix as the primary vehicle.',
    '- Nest the short project-contextual continuous-learning briefing inside each HITL turn (why it matters in Admin UI, files involved, narrowest validation).',
    '- Call out items that should be deferred to avoid diminishing returns.',
    '- After HITL closes, record fix / suppress / skip in a dated campaign note under product-docs/global/hygiene/react-doctor/ (copy TEMPLATE.md).',
    '- Only then implement on a hygiene branch + PR; carry the briefing into the PR description and link the campaign note.',
    '- Reserve a modest low-signal allotment after high-signal work (cheap P2/P3 wins; not giant refactors).',
    '',
    '## Operating guidance',
    '',
    '- Use this report for a small polishing pass, not a zero-warning campaign.',
    '- Prefer fixing P1 and then a narrow subset of P2 in touched files.',
    '- Fuzzy or unclear findings: skip. Clear-but-harmless intentional patterns: leave and suppress with reason.',
    '- Revisit suppressed rules only if they become locally relevant.',
  );

  return `${lines.join('\n')}\n`;
}