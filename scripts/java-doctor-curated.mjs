#!/usr/bin/env node
/**
 * java-doctor-curated — normalize SpotBugs XML + PMD XML + Semgrep JSON,
 * apply suppressions, weight P1/P2/P3, emit curated MD+JSON.
 *
 * Invoked by scripts/java-doctor-curated.sh (do not require as a build gate).
 */

import { existsSync, mkdirSync, readdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT = resolve(SCRIPT_DIR, '..');
const OUTPUT_DIR = join(REPO_ROOT, 'logs', 'java-doctor');
const RAW_DIR = join(OUTPUT_DIR, 'raw');
const CONFIG_DIR = join(REPO_ROOT, 'config', 'java-doctor');
const SUPPRESSIONS_PATH = join(CONFIG_DIR, 'suppressions.json');
const KEYWORD = 'java-doctor-curated';

const PRIORITY_LABELS = {
  P1: 'Review now',
  P2: 'Good next pass',
  P3: 'Defer unless touching the area',
};

main();

function main() {
  mkdirSync(OUTPUT_DIR, { recursive: true });

  const diagnostics = [];
  diagnostics.push(...loadSpotBugs(RAW_DIR));
  diagnostics.push(...loadPmd(RAW_DIR));
  diagnostics.push(...loadSemgrep(join(RAW_DIR, 'semgrep.json')));

  const suppressions = loadSuppressions(SUPPRESSIONS_PATH);
  const suppressed = [];
  const curated = [];

  for (const diagnostic of diagnostics) {
    const match = matchSuppression(diagnostic, suppressions);
    if (match) {
      suppressed.push({ ...diagnostic, suppressionReason: match.reason });
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
    projectRoot: REPO_ROOT,
    raw: summarize(diagnostics),
    curated: summarize(curated),
    suppressedRules: suppressedRuleGroups,
    topRules: ruleGroups.slice(0, 20),
    topFiles: fileGroups.slice(0, 15),
    tools: toolGroups,
    categories: categoryGroups,
    findings: curated.slice(0, 200).map(toFindingRecord),
  };

  const curatedJsonPath = join(OUTPUT_DIR, 'java-doctor.curated.json');
  const curatedMdPath = join(OUTPUT_DIR, 'java-doctor.curated.md');
  const rawSummaryPath = join(OUTPUT_DIR, 'java-doctor.raw-summary.json');

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
  writeFileSync(
    rawSummaryPath,
    `${JSON.stringify(
      {
        generatedAt: summary.generatedAt,
        total: diagnostics.length,
        byTool: buildCountGroups(diagnostics, 'tool'),
      },
      null,
      2,
    )}\n`,
    'utf8',
  );

  console.log(`${KEYWORD} curated.`);
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

function loadSuppressions(path) {
  if (!existsSync(path)) {
    return [];
  }
  try {
    const json = JSON.parse(readFileSync(path, 'utf8'));
    const list = Array.isArray(json.suppressions) ? json.suppressions : [];
    return list
      .map((entry) => ({
        tool: String(entry.tool || '').trim().toLowerCase(),
        rule: String(entry.rule || entry.ruleId || '').trim(),
        reason: String(entry.reason || '').trim() || 'No reason provided',
        pathFragment: String(entry.pathFragment || entry.path || '')
          .trim()
          .replace(/\\/g, '/')
          .toLowerCase(),
      }))
      .filter((entry) => entry.rule);
  } catch {
    console.error(`WARNING: could not parse ${path}`);
    return [];
  }
}

function matchSuppression(diagnostic, suppressions) {
  const file = normalizePath(diagnostic.filePath);
  for (const suppression of suppressions) {
    if (suppression.tool && suppression.tool !== diagnostic.tool) {
      continue;
    }
    if (suppression.rule !== diagnostic.rule) {
      continue;
    }
    if (suppression.pathFragment && !file.includes(suppression.pathFragment)) {
      continue;
    }
    return suppression;
  }
  return null;
}

function loadSpotBugs(rawDir) {
  if (!existsSync(rawDir)) {
    return [];
  }
  const files = readdirSync(rawDir).filter(
    (name) => name.startsWith('spotbugs-') && name.endsWith('.xml'),
  );
  const out = [];
  for (const file of files) {
    const xml = readFileSync(join(rawDir, file), 'utf8');
    const instances = extractBlocks(xml, 'BugInstance');
    for (const block of instances) {
      const type = attr(block, 'type') || '(unknown)';
      const priorityAttr = attr(block, 'priority');
      const category = attr(block, 'category') || 'CORRECTNESS';
      const message =
        innerText(block, 'ShortMessage') ||
        innerText(block, 'LongMessage') ||
        type;
      const sourceLine = extractBlocks(block, 'SourceLine')[0] || '';
      const sourcepath = attr(sourceLine, 'sourcepath') || attr(sourceLine, 'classname') || '';
      const line = Number(attr(sourceLine, 'start') || attr(sourceLine, 'primary') || 0);
      out.push({
        tool: 'spotbugs',
        rule: type,
        category: category.toLowerCase(),
        severity: spotbugsSeverity(priorityAttr),
        filePath: sourcepath,
        line: Number.isFinite(line) ? line : 0,
        message: collapseWs(message),
      });
    }
  }
  return out;
}

function loadPmd(rawDir) {
  if (!existsSync(rawDir)) {
    return [];
  }
  const files = readdirSync(rawDir).filter(
    (name) => name.startsWith('pmd-') && name.endsWith('.xml'),
  );
  const out = [];
  for (const file of files) {
    const xml = readFileSync(join(rawDir, file), 'utf8');
    const fileBlocks = extractBlocks(xml, 'file');
    for (const fileBlock of fileBlocks) {
      const filePath = attr(fileBlock, 'name') || '';
      const violations = extractBlocks(fileBlock, 'violation');
      for (const violation of violations) {
        const rule = attr(violation, 'rule') || '(unknown)';
        const ruleset = attr(violation, 'ruleset') || 'design';
        const priority = attr(violation, 'priority') || '3';
        const line = Number(attr(violation, 'beginline') || 0);
        const message = collapseWs(stripTags(violation));
        out.push({
          tool: 'pmd',
          rule,
          category: ruleset.toLowerCase(),
          severity: pmdSeverity(priority),
          filePath: toRepoRelative(filePath),
          line: Number.isFinite(line) ? line : 0,
          message,
        });
      }
    }
  }
  return out;
}

function loadSemgrep(jsonPath) {
  if (!existsSync(jsonPath)) {
    return [];
  }
  try {
    const report = JSON.parse(readFileSync(jsonPath, 'utf8'));
    const results = Array.isArray(report.results) ? report.results : [];
    return results.map((result) => {
      const extra = result.extra || {};
      const severity = String(extra.severity || 'WARNING').toUpperCase();
      const metadata = extra.metadata || {};
      return {
        tool: 'semgrep',
        rule: result.check_id || '(unknown)',
        category: String(metadata.category || 'security').toLowerCase(),
        severity: severity === 'ERROR' ? 'error' : 'warning',
        filePath: result.path || '',
        line: Number(result.start?.line || 0),
        message: collapseWs(extra.message || result.check_id || ''),
      };
    });
  } catch (error) {
    console.error(`WARNING: could not parse Semgrep JSON: ${error.message}`);
    return [];
  }
}

function priorityFor(diagnostic) {
  if (diagnostic.tool === 'spotbugs') {
    if (diagnostic.severity === 'error') {
      return 'P1';
    }
    if (
      diagnostic.category.includes('correctness') ||
      diagnostic.category.includes('security') ||
      diagnostic.category.includes('mt_correctness') ||
      diagnostic.category.includes('bad_practice')
    ) {
      return 'P1';
    }
    // STYLE / MALICIOUS_CODE (e.g. EI_EXPOSE_*) lean P2 unless already suppressed
    return 'P2';
  }

  if (diagnostic.tool === 'semgrep') {
    if (diagnostic.severity === 'error' || diagnostic.category.includes('security')) {
      return 'P1';
    }
    return 'P2';
  }

  // PMD — design / maintainability lean P2/P3
  if (diagnostic.severity === 'error') {
    return 'P2';
  }
  if (
    diagnostic.rule === 'EmptyCatchBlock' ||
    diagnostic.rule === 'UnusedPrivateMethod'
  ) {
    return 'P2';
  }
  return 'P3';
}

function spotbugsSeverity(priority) {
  const value = Number(priority);
  if (value === 1) {
    return 'error';
  }
  return 'warning';
}

function pmdSeverity(priority) {
  const value = Number(priority);
  if (value <= 2) {
    return 'error';
  }
  return 'warning';
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
        severity: diagnostic.severity,
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
  rawSummary,
  curatedSummary,
  toolGroups,
  categoryGroups,
  fileGroups,
  ruleGroups,
  suppressedRuleGroups,
}) {
  const lines = [
    '# Java Doctor Curated Report',
    '',
    `Keyword: ${KEYWORD}`,
    `Generated: ${new Date().toISOString()}`,
    'Analyzers: SpotBugs + Semgrep (pinned) + PMD (narrow design/maintainability)',
    'Mode: report-only — not a CI / build.sh gate',
    '',
    '## How to use this report',
    '',
    '- Treat this report as triage, not as ground truth.',
    '- Revalidate each retained finding in the code before planning or editing.',
    '- Prefer small local fixes with strong signal-to-effort ratio over broad refactors.',
    '- SpotBugs correctness / Semgrep security → lean P1; PMD design smells → lean P2/P3.',
    '- Hygiene trace: dedicated branch + PR; do not invent I-*/TB-*/TSP-* for a routine polish pass.',
    '- Before implementing: brief the maintainer (why it matters in Ezkey context). Carry that briefing into the PR body.',
    '- After high-signal picks, include a modest low-signal allotment (1–3 cheap continuous-improvement items).',
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
    '## By tool',
    '',
  ];

  for (const group of toolGroups) {
    lines.push(`- ${group.name}: ${group.count}`);
  }
  if (toolGroups.length === 0) {
    lines.push('- None');
  }

  lines.push('', '## Suppressed low-signal rules', '');
  if (suppressedRuleGroups.length === 0) {
    lines.push('- None');
  } else {
    for (const group of suppressedRuleGroups) {
      lines.push(`- [${group.tool}] ${group.rule}: ${group.count} — ${group.reason}`);
    }
  }

  lines.push('', '## Top categories', '');
  for (const group of categoryGroups.slice(0, 10)) {
    lines.push(`- ${group.name}: ${group.count}`);
  }
  if (categoryGroups.length === 0) {
    lines.push('- None');
  }

  lines.push('', '## Top files', '');
  for (const group of fileGroups.slice(0, 10)) {
    lines.push(`- ${group.name}: ${group.count}`);
  }
  if (fileGroups.length === 0) {
    lines.push('- None');
  }

  for (const priority of ['P1', 'P2', 'P3']) {
    lines.push('', `## ${priority} — ${PRIORITY_LABELS[priority]}`, '');
    const matching = ruleGroups.filter((group) => group.priority === priority).slice(0, 10);
    if (matching.length === 0) {
      lines.push('- None');
      continue;
    }
    for (const group of matching) {
      lines.push(
        `- [${group.tool}] ${group.rule}: ${group.count} (${group.category}, max ${group.maxSeverity})`,
      );
      for (const example of group.examples) {
        const location =
          example.line > 0 ? `${example.filePath}:${example.line}` : example.filePath;
        lines.push(`  Example: ${location} — ${example.message}`);
      }
    }
  }

  lines.push(
    '',
    '## Planning contract',
    '',
    '- Start with P1 only unless the task explicitly widens scope.',
    '- Keep the first plan small: usually 3 to 5 items maximum.',
    '- For each retained item, state why it matters, what files are involved, and the narrowest useful validation step.',
    '- Call out items that should be deferred to avoid diminishing returns.',
    '- Brief the maintainer first, then implement on a hygiene branch + PR.',
    '- Reserve a modest low-signal allotment after high-signal work.',
    '',
    '## Operating guidance',
    '',
    '- Use this report for a small polishing pass, not a zero-warning campaign.',
    '- Prefer fixing P1 and then a narrow subset of P2 in touched files.',
    '- Revisit suppressed rules only if they become locally relevant.',
    '- Pin packs stay small: edit config/java-doctor/ before expanding Semgrep or PMD catalogues.',
  );

  return `${lines.join('\n')}\n`;
}

function extractBlocks(xml, tag) {
  const blocks = [];
  const open = new RegExp(`<${tag}\\b([^>]*)>`, 'g');
  let match;
  while ((match = open.exec(xml)) !== null) {
    const startContent = open.lastIndex;
    const attrsPart = match[1] || '';
    if (attrsPart.trim().endsWith('/') || match[0].endsWith('/>')) {
      blocks.push(match[0]);
      continue;
    }
    const closeTag = `</${tag}>`;
    const closeIdx = xml.indexOf(closeTag, startContent);
    if (closeIdx === -1) {
      continue;
    }
    blocks.push(xml.slice(match.index, closeIdx + closeTag.length));
    open.lastIndex = closeIdx + closeTag.length;
  }
  return blocks;
}

function attr(fragment, name) {
  const doubleQuoted = fragment.match(new RegExp(`\\b${name}="([^"]*)"`, 'i'));
  if (doubleQuoted) {
    return decodeXml(doubleQuoted[1]);
  }
  const singleQuoted = fragment.match(new RegExp(`\\b${name}='([^']*)'`, 'i'));
  return singleQuoted ? decodeXml(singleQuoted[1]) : '';
}

function innerText(fragment, tag) {
  const match = fragment.match(new RegExp(`<${tag}[^>]*>([\\s\\S]*?)</${tag}>`, 'i'));
  return match ? decodeXml(stripTags(match[1])) : '';
}

function stripTags(value) {
  return String(value || '')
    .replace(/<[^>]+>/g, ' ')
    .trim();
}

function decodeXml(value) {
  return String(value || '')
    .replace(/&lt;/g, '<')
    .replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'")
    .replace(/&amp;/g, '&');
}

function collapseWs(value) {
  return String(value || '').replace(/\s+/g, ' ').trim();
}

function normalizePath(value) {
  return String(value || '').replace(/\\/g, '/').toLowerCase();
}

function toRepoRelative(absoluteOrRelative) {
  const normalized = String(absoluteOrRelative || '').replace(/\\/g, '/');
  const root = REPO_ROOT.replace(/\\/g, '/');
  if (normalized.toLowerCase().startsWith(root.toLowerCase())) {
    return normalized.slice(root.length).replace(/^\//, '');
  }
  return normalized;
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
