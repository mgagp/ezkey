#!/usr/bin/env node
/*
 * Code quality curator (iteration 0 foundation)
 *
 * This script normalizes findings from Biome, Semgrep, and Detekt/SARIF,
 * deduplicates noisy overlaps, computes a simple priority score, and writes
 * readable Markdown/HTML reports from a single normalized JSON model.
 */

import {mkdirSync, readFileSync, writeFileSync} from 'node:fs';
import path from 'node:path';

const rawArgs = process.argv.slice(2);

const parseArg = (name, fallback = null) => {
  const match = rawArgs.find(arg => arg.startsWith(`${name}=`));
  if (!match) {
    return fallback;
  }
  return match.slice(name.length + 1);
};

const hasFlag = name => rawArgs.includes(name);

const rootDir = path.resolve(process.cwd());
const outputDir = path.resolve(rootDir, parseArg('--output-dir', '.monitor/code-quality'));
const inputArg = parseArg('--inputs', '.monitor/biome-report.json,.monitor/semgrep-report.json,.monitor/detekt.sarif,.monitor/detekt-report.json');
const excludePathArg = parseArg('--exclude-path-fragments', '');
const reportBaseName = parseArg('--report-name', 'curated-report');
const formatArg = parseArg('--format', 'all');
const topNArg = Number(parseArg('--top', '30'));
const topN = Number.isFinite(topNArg) && topNArg > 0 ? Math.floor(topNArg) : 30;
const failOnCritical = hasFlag('--fail-on-critical');

const excludedPathFragments = excludePathArg
  .split(',')
  .map(v => v.trim())
  .filter(Boolean)
  .map(v => v.replace(/\\/g, '/').toLowerCase());

const ensureDir = dirPath => {
  mkdirSync(dirPath, {recursive: true});
};

const tryReadJson = filePath => {
  try {
    const raw = readFileSync(filePath, 'utf8');
    return JSON.parse(raw);
  } catch (error) {
    return null;
  }
};

const tryReadBiomeJson = filePath => {
  try {
    const raw = readFileSync(filePath, 'utf8');
    return JSON.parse(raw);
  } catch (error) {
    try {
      const raw = readFileSync(filePath, 'utf8');
      const fixed = raw.replace(/"path":"([^"]*)"/g, (_, value) => {
        return `"path":"${String(value).replace(/\\/g, '\\\\')}"`;
      });
      return JSON.parse(fixed);
    } catch (secondError) {
      return null;
    }
  }
};

const normalizeSeverity = value => {
  const input = String(value || '').toLowerCase();
  if (['critical', 'error', 'high', 'blocker', 'fatal'].includes(input)) {
    return 'high';
  }
  if (['warning', 'warn', 'medium', 'major'].includes(input)) {
    return 'medium';
  }
  if (['info', 'low', 'minor', 'note', 'style'].includes(input)) {
    return 'low';
  }
  return 'medium';
};

const classifyCategory = (ruleId, message) => {
  const text = `${ruleId || ''} ${message || ''}`.toLowerCase();
  if (/secret|token|credential|crypto|secure|tls|xss|sqli|signature|keystore|random/.test(text)) {
    return 'security';
  }
  if (/null|undefined|bug|correct|exception|timeout|abort|race|leak|overflow/.test(text)) {
    return 'correctness';
  }
  if (/complex|readab|maintain|refactor|nested|duplicate|smell/.test(text)) {
    return 'maintainability';
  }
  return 'style';
};

const severityWeight = {
  high: 3,
  medium: 2,
  low: 1,
};

const categoryWeight = {
  security: 3,
  correctness: 2,
  maintainability: 1,
  style: 0.5,
};

const computeScore = finding => {
  const s = severityWeight[finding.severity] ?? 1;
  const c = categoryWeight[finding.category] ?? 1;
  return Number((s * c).toFixed(2));
};

const buildFingerprint = finding => {
  const msg = (finding.message || '').toLowerCase().replace(/\s+/g, ' ').trim();
  return [
    finding.ruleId || 'unknown',
    finding.path || 'unknown',
    String(finding.line || 0),
    msg,
  ].join('|');
};

const normalizeBiome = (json, sourceFile) => {
  const diagnostics = Array.isArray(json?.diagnostics) ? json.diagnostics : [];
  return diagnostics.map(item => {
    const location = item.location || {};
    const start = location.start || {};
    const ruleId = item.category || item.rule || 'biome/unknown';
    const message = item.message || item.description || 'Biome finding';
    const severity = normalizeSeverity(item.severity);
    const filePath = location.path?.file || location.path || sourceFile;
    const line = Number(start.line || 1);
    const column = Number(start.column || 1);
    const category = classifyCategory(ruleId, message);

    return {
      tool: 'biome',
      category,
      severity,
      ruleId,
      message,
      path: String(filePath).replace(/\\/g, '/'),
      line,
      column,
      confidence: 'medium',
      evidence: {
        sourceFile,
      },
    };
  });
};

const normalizeSemgrep = (json, sourceFile) => {
  const results = Array.isArray(json?.results) ? json.results : [];
  return results.map(item => {
    const severity = normalizeSeverity(item?.extra?.severity);
    const ruleId = item.check_id || 'semgrep/unknown';
    const message = item?.extra?.message || 'Semgrep finding';
    const pathValue = item.path || sourceFile;
    const line = Number(item?.start?.line || 1);
    const column = Number(item?.start?.col || 1);
    const category = classifyCategory(ruleId, message);
    const confidence = item?.extra?.metadata?.confidence || 'medium';

    return {
      tool: 'semgrep',
      category,
      severity,
      ruleId,
      message,
      path: String(pathValue).replace(/\\/g, '/'),
      line,
      column,
      confidence: String(confidence).toLowerCase(),
      evidence: {
        sourceFile,
      },
    };
  });
};

const normalizeSarifRuns = (runs, toolName, sourceFile) => {
  const findings = [];
  runs.forEach(run => {
    const rules = run?.tool?.driver?.rules || [];
    const ruleNameById = new Map(rules.map(rule => [rule.id, rule.name || rule.shortDescription?.text || rule.id]));
    const results = Array.isArray(run?.results) ? run.results : [];

    results.forEach(result => {
      const location = result.locations?.[0]?.physicalLocation;
      const region = location?.region || {};
      const ruleIdRaw = result.ruleId || 'sarif/unknown';
      const ruleId = ruleNameById.get(ruleIdRaw) || ruleIdRaw;
      const message = result.message?.text || 'SARIF finding';
      const severity = normalizeSeverity(result.level || result.properties?.severity);
      const filePath = location?.artifactLocation?.uri || sourceFile;
      const line = Number(region.startLine || 1);
      const column = Number(region.startColumn || 1);
      const category = classifyCategory(ruleId, message);

      findings.push({
        tool: toolName,
        category,
        severity,
        ruleId,
        message,
        path: String(filePath).replace(/\\/g, '/'),
        line,
        column,
        confidence: 'medium',
        evidence: {
          sourceFile,
        },
      });
    });
  });
  return findings;
};

const normalizeDetektJson = (json, sourceFile) => {
  const findings = [];
  const records = Array.isArray(json) ? json : [];
  records.forEach(item => {
    const ruleId = item.rule || item.id || 'detekt/unknown';
    const message = item.message || item.description || 'Detekt finding';
    const severity = normalizeSeverity(item.severity || item.level);
    const pathValue = item.file || sourceFile;
    const line = Number(item.line || item.location?.line || 1);
    const column = Number(item.column || item.location?.column || 1);
    const category = classifyCategory(ruleId, message);

    findings.push({
      tool: 'detekt',
      category,
      severity,
      ruleId,
      message,
      path: String(pathValue).replace(/\\/g, '/'),
      line,
      column,
      confidence: 'medium',
      evidence: {
        sourceFile,
      },
    });
  });
  return findings;
};

const toRelative = absoluteOrRelativePath => {
  const candidate = path.resolve(rootDir, absoluteOrRelativePath);
  if (candidate.startsWith(rootDir)) {
    return path.relative(rootDir, candidate).replace(/\\/g, '/');
  }
  return String(absoluteOrRelativePath).replace(/\\/g, '/');
};

const normalizeFile = inputPath => {
  const absPath = path.resolve(rootDir, inputPath);
  const basename = path.basename(inputPath).toLowerCase();
  const json = basename.includes('biome') ? tryReadBiomeJson(absPath) : tryReadJson(absPath);
  if (!json) {
    return [];
  }

  if (basename.includes('semgrep')) {
    return normalizeSemgrep(json, toRelative(inputPath));
  }
  if (basename.includes('biome')) {
    return normalizeBiome(json, toRelative(inputPath));
  }
  if (basename.includes('detekt') && basename.endsWith('.json')) {
    return normalizeDetektJson(json, toRelative(inputPath));
  }
  if (Array.isArray(json?.runs)) {
    const toolName = basename.includes('detekt') ? 'detekt' : 'sarif';
    return normalizeSarifRuns(json.runs, toolName, toRelative(inputPath));
  }

  return [];
};

const countBy = (items, keySelector) => {
  const map = new Map();
  items.forEach(item => {
    const key = keySelector(item);
    map.set(key, (map.get(key) || 0) + 1);
  });
  return Object.fromEntries([...map.entries()].sort((a, b) => String(a[0]).localeCompare(String(b[0]))));
};

const renderMarkdown = report => {
  const lines = [];
  lines.push('# Mobile Code Quality Curated Report');
  lines.push('');
  lines.push(`- Generated at: ${report.generatedAt}`);
  lines.push(`- Inputs: ${report.inputs.join(', ') || '(none)'}`);
  lines.push(`- Findings (raw): ${report.summary.totalRaw}`);
  lines.push(`- Findings (deduplicated): ${report.summary.total}`);
  lines.push(`- Critical set (high severity): ${report.summary.highSeverityCount}`);
  lines.push('');
  lines.push('## Breakdown');
  lines.push('');
  lines.push('### By Tool');
  lines.push('');
  Object.entries(report.summary.byTool).forEach(([tool, count]) => {
    lines.push(`- ${tool}: ${count}`);
  });
  lines.push('');
  lines.push('### By Category');
  lines.push('');
  Object.entries(report.summary.byCategory).forEach(([category, count]) => {
    lines.push(`- ${category}: ${count}`);
  });
  lines.push('');
  lines.push('### By Severity');
  lines.push('');
  Object.entries(report.summary.bySeverity).forEach(([severity, count]) => {
    lines.push(`- ${severity}: ${count}`);
  });
  lines.push('');
  lines.push(`## Top ${report.topFindings.length} Actionable Findings`);
  lines.push('');
  lines.push('| Priority | Tool | Severity | Category | Rule | Location | Message |');
  lines.push('| --- | --- | --- | --- | --- | --- | --- |');

  report.topFindings.forEach(f => {
    const location = `${f.path}:${f.line}`;
    const msg = String(f.message || '').replace(/\|/g, '\\|').replace(/\n/g, ' ').trim();
    lines.push(`| ${f.priorityScore} | ${f.tool} | ${f.severity} | ${f.category} | ${f.ruleId} | ${location} | ${msg} |`);
  });

  return `${lines.join('\n')}\n`;
};

const htmlEscape = value =>
  String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');

const renderHtml = report => {
  const rows = report.topFindings
    .map(f => {
      return `<tr>
        <td>${htmlEscape(f.priorityScore)}</td>
        <td>${htmlEscape(f.tool)}</td>
        <td>${htmlEscape(f.severity)}</td>
        <td>${htmlEscape(f.category)}</td>
        <td>${htmlEscape(f.ruleId)}</td>
        <td>${htmlEscape(`${f.path}:${f.line}`)}</td>
        <td>${htmlEscape(f.message)}</td>
      </tr>`;
    })
    .join('\n');

  const summaryItems = [
    `Generated at: ${report.generatedAt}`,
    `Inputs: ${report.inputs.join(', ') || '(none)'}`,
    `Findings (raw): ${report.summary.totalRaw}`,
    `Findings (deduplicated): ${report.summary.total}`,
    `Critical set (high severity): ${report.summary.highSeverityCount}`,
  ]
    .map(item => `<li>${htmlEscape(item)}</li>`)
    .join('\n');

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width,initial-scale=1" />
  <title>Mobile Code Quality Curated Report</title>
  <style>
    body { font-family: -apple-system, Segoe UI, Roboto, sans-serif; margin: 24px; color: #1f2937; }
    h1, h2, h3 { margin: 0 0 12px; }
    .meta { margin: 0 0 16px; }
    table { border-collapse: collapse; width: 100%; margin-top: 12px; }
    th, td { border: 1px solid #d1d5db; padding: 8px; vertical-align: top; text-align: left; }
    th { background: #f3f4f6; }
    tr:nth-child(even) { background: #fafafa; }
  </style>
</head>
<body>
  <h1>Mobile Code Quality Curated Report</h1>
  <ul class="meta">
    ${summaryItems}
  </ul>

  <h2>Top ${report.topFindings.length} Actionable Findings</h2>
  <table>
    <thead>
      <tr>
        <th>Priority</th>
        <th>Tool</th>
        <th>Severity</th>
        <th>Category</th>
        <th>Rule</th>
        <th>Location</th>
        <th>Message</th>
      </tr>
    </thead>
    <tbody>
      ${rows}
    </tbody>
  </table>
</body>
</html>
`;
};

const inputFiles = inputArg
  .split(',')
  .map(v => v.trim())
  .filter(Boolean);

let allFindings = [];
inputFiles.forEach(file => {
  allFindings = allFindings.concat(normalizeFile(file));
});

if (excludedPathFragments.length > 0) {
  allFindings = allFindings.filter(item => {
    const normalizedPath = String(item.path || '').replace(/\\/g, '/').toLowerCase();
    return !excludedPathFragments.some(fragment => normalizedPath.includes(fragment));
  });
}

const totalRaw = allFindings.length;

const dedupMap = new Map();
allFindings.forEach(item => {
  const withDefaults = {
    ...item,
    priorityScore: computeScore(item),
  };
  const fp = buildFingerprint(withDefaults);
  const existing = dedupMap.get(fp);
  if (!existing || withDefaults.priorityScore > existing.priorityScore) {
    dedupMap.set(fp, withDefaults);
  }
});

const deduped = [...dedupMap.values()].sort((a, b) => b.priorityScore - a.priorityScore);
const topFindings = deduped.slice(0, topN);

const report = {
  version: 1,
  generatedAt: new Date().toISOString(),
  inputs: inputFiles,
  summary: {
    totalRaw,
    total: deduped.length,
    highSeverityCount: deduped.filter(f => f.severity === 'high').length,
    byTool: countBy(deduped, f => f.tool),
    byCategory: countBy(deduped, f => f.category),
    bySeverity: countBy(deduped, f => f.severity),
  },
  topFindings,
  findings: deduped,
};

ensureDir(outputDir);

const normalizedPath = path.join(outputDir, `${reportBaseName}.normalized.json`);
writeFileSync(normalizedPath, JSON.stringify(report, null, 2), 'utf8');

if (formatArg === 'all' || formatArg === 'md') {
  const mdPath = path.join(outputDir, `${reportBaseName}.md`);
  writeFileSync(mdPath, renderMarkdown(report), 'utf8');
}

if (formatArg === 'all' || formatArg === 'html') {
  const htmlPath = path.join(outputDir, `${reportBaseName}.html`);
  writeFileSync(htmlPath, renderHtml(report), 'utf8');
}

console.log('Code quality curator report generated.');
console.log(`- normalized: ${path.relative(rootDir, normalizedPath).replace(/\\/g, '/')}`);
if (formatArg === 'all' || formatArg === 'md') {
  console.log(`- markdown: ${path.relative(rootDir, path.join(outputDir, `${reportBaseName}.md`)).replace(/\\/g, '/')}`);
}
if (formatArg === 'all' || formatArg === 'html') {
  console.log(`- html: ${path.relative(rootDir, path.join(outputDir, `${reportBaseName}.html`)).replace(/\\/g, '/')}`);
}

if (failOnCritical && report.summary.highSeverityCount > 0) {
  process.exit(1);
}