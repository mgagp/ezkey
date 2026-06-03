import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SITE_DIR = __dirname;
const PROJECT_ROOT = path.resolve(SITE_DIR, '..', '..');
const SKILLS_SOURCE_DIR = path.join(PROJECT_ROOT, '.cursor', 'skills');

export const GENERATED_SKILLS_DIR = path.join(SITE_DIR, '.generated', 'skills');

const FALLBACK_WORKFLOW_ORDER = [
  'vision-intake',
  'backlog-triage',
  'grill-me',
  'plan-incubation',
  'tracer-bullet-promote',
  'component-design-pack',
  'test-strategy-planner',
  'quality-gatekeeper',
  'traceability-sync',
  'closeout',
];

const FALLBACK_RETROFIT_ORDER = [
  'legacy-plan-miner',
  'retrofit-curator',
];

export const PUBLIC_SKILLS_SEQUENCE = [
  'README',
  'vision-intake',
  'backlog-triage',
  'grill-me',
  'tracer-bullet-promote',
  'component-design-pack',
  'test-strategy-planner',
  'quality-gatekeeper',
  'traceability-sync',
  'closeout',
  'plan-incubation',
  'legacy-plan-miner',
  'retrofit-curator',
  'methodology-release',
];

const PUBLIC_SKILLS_RANK = new Map(PUBLIC_SKILLS_SEQUENCE.map((name, index) => [name, index]));

export function prepareSkillsPublicCorpus() {
  rmrf(GENERATED_SKILLS_DIR);
  mkdirp(GENERATED_SKILLS_DIR);

  const orders = readSkillOrders();
  const orderedNames = dedupe([
    ...orders.workflow,
    ...orders.retrofit,
    ...listUnorderedSkillDirs(),
  ]);

  const skills = orderedNames
    .map((skillName, index) => parseSkill(skillName, index + 1))
    .filter(Boolean);

  skills.sort(compareSkillsForPublicOrder);
  skills.forEach((skill, index) => {
    skill.order = index + 1;
  });
  
  const knownSkillNames = new Set(skills.map((skill) => skill.name));
  for (const skill of skills) {
    skill.category = orders.retrofit.includes(skill.name)
        ? 'retrofit'
      : orders.workflow.includes(skill.name)
        ? 'workflow'
        : 'additional';
    skill.callNextSkills = skill.callNextSkills.filter((name) => knownSkillNames.has(name));
  }

  writeText(
    path.join(GENERATED_SKILLS_DIR, 'README.md'),
    buildOverviewDoc({
      skills,
      workflowOrder: orders.workflow,
      retrofitOrder: orders.retrofit,
    }),
  );

  for (const skill of skills) {
    writeText(path.join(GENERATED_SKILLS_DIR, `${skill.name}.md`), buildSkillDoc(skill));
  }

  return {
    generatedDir: GENERATED_SKILLS_DIR,
    count: skills.length,
    skills,
  };
}

function readSkillOrders() {
  const readmePath = path.join(SKILLS_SOURCE_DIR, 'README.md');
  if (!fs.existsSync(readmePath)) {
    return {
      workflow: FALLBACK_WORKFLOW_ORDER,
      retrofit: FALLBACK_RETROFIT_ORDER,
    };
  }

  const readme = fs.readFileSync(readmePath, 'utf8');
  return {
    workflow: parseBulletListSection(readme, 'Workflow skills', FALLBACK_WORKFLOW_ORDER),
    retrofit: parseNumberedListSection(readme, 'Legacy retrofit skills', FALLBACK_RETROFIT_ORDER),
  };
}

function parseBulletListSection(markdown, title, fallback) {
  const section = sectionBody(markdown, title);
  if (!section) return fallback;
  const values = section
    .split(/\r?\n/)
    .map((line) => /^- `?([^`]+?)`?$/.exec(line.trim()))
    .filter(Boolean)
    .map((match) => match[1]);
  return values.length > 0 ? values : fallback;
}

function parseNumberedListSection(markdown, title, fallback) {
  const section = sectionBody(markdown, title);
  if (!section) return fallback;
  const values = section
    .split(/\r?\n/)
    .map((line) => /^\d+\.\s+(.+)$/.exec(line.trim()))
    .filter(Boolean)
    .map((match) => stripInlineCode(match[1]).trim().toLowerCase().replace(/\s+/g, '-'));
  return values.length > 0 ? values : fallback;
}

function sectionBody(markdown, title) {
  const escaped = title.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const re = new RegExp(`^## ${escaped}\\r?\\n([\\s\\S]*?)(?=^## |\\Z)`, 'm');
  const match = re.exec(markdown);
  return match ? match[1].trim() : '';
}

function stripInlineCode(value) {
  return String(value || '').replace(/`([^`]+)`/g, '$1');
}

function listUnorderedSkillDirs() {
  if (!fs.existsSync(SKILLS_SOURCE_DIR)) return [];
  return fs.readdirSync(SKILLS_SOURCE_DIR, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .filter((name) => fs.existsSync(path.join(SKILLS_SOURCE_DIR, name, 'SKILL.md')))
    .sort();
}

function parseSkill(skillName, order) {
  const abs = path.join(SKILLS_SOURCE_DIR, skillName, 'SKILL.md');
  if (!fs.existsSync(abs)) return null;

  const raw = fs.readFileSync(abs, 'utf8');
  const parsed = matter(raw);
  const sections = splitSections(parsed.content);
  const boundary = parseBoundary(sections.get('Boundary contract') || '');
  const outputs = sections.get('Outputs') || sections.get('Output target') || '';

  return {
    name: skillName,
    title: findTitle(parsed.content) || skillName,
    description: String(parsed.data?.description || '').trim(),
    purpose: sections.get('Purpose') || '',
    boundary,
    inputs: sections.get('Inputs') || '',
    outputs,
    steps: sections.get('Steps') || '',
    rule: sections.get('Rule') || '',
    multiBranchNote: sections.get('Multi-branch note') || '',
    callNextSkills: extractSkillRefs(boundary.callNext),
    order,
    sourcePath: `.cursor/skills/${skillName}/SKILL.md`,
  };
}

function splitSections(content) {
  const map = new Map();
  const lines = String(content || '').split(/\r?\n/);
  let currentTitle = null;
  let currentLines = [];

  const flush = () => {
    if (!currentTitle) return;
    map.set(currentTitle, currentLines.join('\n').trim());
  };

  for (const line of lines) {
    const heading = /^##\s+(.+)$/.exec(line);
    if (heading) {
      flush();
      currentTitle = heading[1].trim();
      currentLines = [];
      continue;
    }

    if (currentTitle) currentLines.push(line);
  }

  flush();
  return map;
}

function findTitle(content) {
  const match = /^#\s+(.+)$/m.exec(String(content || ''));
  return match ? match[1].trim() : '';
}

function parseBoundary(section) {
  const out = {
    enterWhen: '',
    exitWhen: '',
    callNext: '',
    notNeededWhen: '',
  };

  for (const line of String(section || '').split(/\r?\n/)) {
    const match = /^- \*\*(Enter when|Exit when|Call next|Not needed when):\*\*\s+(.+)$/.exec(
      line.trim(),
    );
    if (!match) continue;

    if (match[1] === 'Enter when') out.enterWhen = match[2].trim();
    if (match[1] === 'Exit when') out.exitWhen = match[2].trim();
    if (match[1] === 'Call next') out.callNext = match[2].trim();
    if (match[1] === 'Not needed when') out.notNeededWhen = match[2].trim();
  }

  return out;
}

function extractSkillRefs(value) {
  return Array.from(String(value || '').matchAll(/`([^`]+)`/g), (match) => match[1]);
}

function buildOverviewDoc({ skills, workflowOrder, retrofitOrder }) {
  const workflowSkills = skills.filter((skill) => skill.category === 'workflow');
  const retrofitSkills = skills.filter((skill) => skill.category === 'retrofit');
  const remainingSkills = skills.filter((skill) => skill.category === 'additional');

  const cards = (list) => list.map((skill) => buildSkillCard(skill)).join('\n');

  return `# Skills Explorer

<section class="skills-hero">
  <p class="skills-eyebrow">Derived public layer</p>
  <p class="skills-lede">
    This section exposes the methodology skills as a public navigation layer inside the methodology
    site.
  </p>
  <p class="skills-sublede">
    The source of truth remains <code>.cursor/skills/</code>. The pages here are derived views
    packaged for discoverability, traceability, and public explanation.
  </p>
</section>

## Why skills belong here

- the methodology already depends on named skill boundaries,
- the public site should show how human and AI collaboration becomes operational,
- public discoverability should not require browsing editor-local folders,
- the published view should stay derived so the source of truth remains singular.

## Common boundary contract

Every published skill answers the same four questions:

| Field | Meaning |
| --- | --- |
| Enter when | What condition makes the skill useful now. |
| Exit when | What evidence means the skill has completed its job. |
| Call next | What skill or workflow step usually follows. |
| Not needed when | What condition preserves the fast path. |

See [AI Collaboration Model](#/methodology/ai-collaboration-model.md) for the canonical protocol.

## Skill map

\`\`\`mermaid
flowchart LR
  VI[vision-intake] --> BT[backlog-triage]
  BT --> GM[grill-me]
  GM --> TB[tracer-bullet-promote]
  TB --> CD[component-design-pack]
  CD --> TS[test-strategy-planner]
  TS --> QG[quality-gatekeeper]
  QG --> TR[traceability-sync]
  TR --> CO[closeout]
  PI[plan-incubation] --> VI
  PI --> BT
  PI --> TB
  LM[legacy-plan-miner] --> RC[retrofit-curator]
  RC --> TR
  RC --> CO
\`\`\`

## Workflow skills

<div class="skill-card-grid">
${cards(workflowSkills)}
</div>

## Legacy retrofit skills

<div class="skill-card-grid">
${cards(retrofitSkills)}
</div>

${remainingSkills.length > 0 ? `## Additional skills\n\n<div class="skill-card-grid">\n${cards(remainingSkills)}\n</div>` : ''}

## Method links

- [Methodology Pack](#/methodology/README.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Rich methodology view](#/methodology/view/index.html)

`;
}

function buildSkillCard(skill) {
  const summary = skill.description || summarizeMarkdown(skill.purpose);
  return `  <article class="skill-card skill-card--${skill.category}">
    <div class="skill-card-meta">
      <span class="skill-card-order">${String(skill.order).padStart(2, '0')}</span>
      <span class="skill-card-kind">${escapeHtml(skillKindLabel(skill.category))}</span>
    </div>
    <h3><a href="${skillDocHref(skill.name)}"><code>${escapeHtml(skill.name)}</code></a></h3>
    <p class="skill-card-summary">${escapeHtml(summary || 'See the detailed skill page.')}</p>
    <div class="skill-card-facts">
      <p><strong>Enter when</strong><br />${escapeHtml(stripInlineCode(skill.boundary.enterWhen) || 'See detail.')}</p>
      <p><strong>Call next</strong><br />${formatCallNextHtml(skill.callNextSkills) || escapeHtml(stripInlineCode(skill.boundary.callNext) || 'Context-dependent.')}</p>
      <p><strong>Not needed when</strong><br />${escapeHtml(stripInlineCode(skill.boundary.notNeededWhen) || 'The lightweight path is still valid.')}</p>
    </div>
    <p class="skill-card-link"><a href="${skillDocHref(skill.name)}">Open detail</a></p>
  </article>`;
}

function buildSkillDoc(skill) {
  return `# Skill: \`${skill.name}\`

<section class="skill-detail-hero skill-detail-hero--${skill.category}">
  <p class="skills-eyebrow">${escapeHtml(skillKindLabel(skill.category))}</p>
  <p class="skill-detail-summary">${escapeHtml(skill.description || summarizeMarkdown(skill.purpose) || 'Operational methodology skill.')}</p>
  <div class="skill-chip-row">
    <span class="skill-chip">Order ${String(skill.order).padStart(2, '0')}</span>
    <span class="skill-chip">Source of truth: <code>${escapeHtml(skill.sourcePath)}</code></span>
    ${skill.callNextSkills.length > 0 ? `<span class="skill-chip">Call next: ${skill.callNextSkills.map((name) => `<a href="${skillDocHref(name)}">${escapeHtml(name)}</a>`).join(', ')}</span>` : ''}
  </div>
</section>

## Purpose

${skill.purpose || 'See the source skill for the full operational framing.'}

## Boundary contract

<div class="skill-contract-grid">
  <article class="skill-contract-card">
    <h3>Enter when</h3>
    <p>${escapeHtml(stripInlineCode(skill.boundary.enterWhen) || '—')}</p>
  </article>
  <article class="skill-contract-card">
    <h3>Exit when</h3>
    <p>${escapeHtml(stripInlineCode(skill.boundary.exitWhen) || '—')}</p>
  </article>
  <article class="skill-contract-card">
    <h3>Call next</h3>
    <p>${formatCallNextHtml(skill.callNextSkills) || escapeHtml(stripInlineCode(skill.boundary.callNext) || '—')}</p>
  </article>
  <article class="skill-contract-card">
    <h3>Not needed when</h3>
    <p>${escapeHtml(stripInlineCode(skill.boundary.notNeededWhen) || '—')}</p>
  </article>
</div>

## Inputs

${skill.inputs || 'No additional input guidance recorded.'}

## Outputs

${skill.outputs || 'No additional output guidance recorded.'}

## Steps

${skill.steps || 'Use the boundary contract and method links to position this skill.'}

${skill.rule ? `## Rule\n\n${skill.rule}\n\n` : ''}${skill.multiBranchNote ? `## Multi-branch note\n\n${skill.multiBranchNote}\n\n` : ''}## Method links

- [Skills overview](README.md)
- [AI Collaboration Model](#/methodology/ai-collaboration-model.md)
- [Session Start Guide](#/methodology/session-start-guide.md)
- [Methodology Pack](#/methodology/README.md)
${skill.callNextSkills.length > 0 ? `- Next skills: ${skill.callNextSkills.map((name) => `[${name}](${name}.md)`).join(', ')}` : ''}
`;
}

function summarizeMarkdown(markdown) {
  return String(markdown || '')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/\s+/g, ' ')
    .trim();
}

function formatCallNext(skillNames) {
  if (!Array.isArray(skillNames) || skillNames.length === 0) return '';
  return skillNames.map((name) => `[${name}](${name}.md)`).join(', ');
}

function formatCallNextHtml(skillNames) {
  if (!Array.isArray(skillNames) || skillNames.length === 0) return '';
  return skillNames
    .map((name) => `<a href="${skillDocHref(name)}">${escapeHtml(name)}</a>`)
    .join(', ');
}

function skillDocHref(skillName) {
  return `#/skills/${encodeURIComponent(skillName)}.md`;
}

function tableEscape(value) {
  return String(value || '').replace(/\|/g, '\\|');
}

function skillKindLabel(category) {
  if (category === 'workflow') return 'Workflow skill';
  if (category === 'retrofit') return 'Legacy retrofit skill';
  return 'Method skill';
}

function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function publicSkillRank(name) {
  return PUBLIC_SKILLS_RANK.has(name) ? PUBLIC_SKILLS_RANK.get(name) : Number.MAX_SAFE_INTEGER;
}

function compareSkillsForPublicOrder(left, right) {
  const rankDiff = publicSkillRank(left.name) - publicSkillRank(right.name);
  if (rankDiff !== 0) return rankDiff;
  return left.name.localeCompare(right.name);
}

function dedupe(values) {
  return [...new Set(values.filter(Boolean))];
}

function rmrf(targetPath) {
  if (fs.existsSync(targetPath)) {
    fs.rmSync(targetPath, { recursive: true, force: true });
  }
}

function mkdirp(targetPath) {
  fs.mkdirSync(targetPath, { recursive: true });
}

function writeText(targetPath, content) {
  mkdirp(path.dirname(targetPath));
  fs.writeFileSync(targetPath, content.trimEnd() + '\n', 'utf8');
}