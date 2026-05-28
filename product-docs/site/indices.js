/*
 * Ezkey Method · Local Explorer — corpus indices (pure, transport-agnostic)
 *
 * Extracted from server.js for Phase 4 (static build reuse). Contains:
 *   - buildCorpusIndices: walks the corpus once and returns
 *     { backlinks: Map<urlPath, Array>, searchIndex: Array<{path,title,headings,text}> }
 *   - buildGlossary: hand-curated glossary served to the client for tooltips.
 *   - plainifyMarkdown / pickTitle helpers (shared with renderDoc).
 *
 * No I/O assumptions beyond fs.readFileSync on absolute paths returned by the
 * caller's resolveCorpusPath. Safe to call from the Express server or a CLI
 * build script.
 */

import path from 'node:path';
import fs from 'node:fs';
import matter from 'gray-matter';

/** Extract the first H1 from markdown content, or fall back to the basename. */
export function pickTitle(markdown, absPath) {
  const m = /^#\s+(.+?)\s*$/m.exec(markdown);
  if (m) return m[1].trim();
  return path.basename(absPath).replace(/\.md$/i, '');
}

/** Strip markdown syntax to a plain, search-friendly excerpt. */
export function plainifyMarkdown(md) {
  return md
    .replace(/```[\s\S]*?```/g, ' ')
    .replace(/`[^`]*`/g, ' ')
    .replace(/!\[[^\]]*\]\([^)]*\)/g, ' ')
    .replace(/\[([^\]]+)\]\([^)]*\)/g, '$1')
    .replace(/^#{1,6}\s+/gm, '')
    .replace(/[*_>#~]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Walk every corpus file once to build backlinks + search payload.
 *
 * @param {object} deps
 * @param {() => Array} deps.buildTree         - returns the navigation tree.
 * @param {(urlPath: string) => string|null} deps.resolveCorpusPath
 * @param {(absPath: string) => string|null} deps.toCorpusUrlPath
 * @returns {{ backlinks: Map<string, Array<{source:string,sourceTitle:string,linkText:string}>>,
 *             searchIndex: Array<{path:string,title:string,headings:Array,text:string}> }}
 */
export function buildCorpusIndices({ buildTree, resolveCorpusPath, toCorpusUrlPath }) {
  const backlinks = new Map();
  const searchIndex = [];

  const allFiles = [];
  function collect(node) {
    if (node.kind === 'file') allFiles.push(node.path);
    if (node.children) for (const c of node.children) collect(c);
  }
  for (const root of buildTree()) collect(root);

  for (const urlPath of allFiles) {
    const abs = resolveCorpusPath(urlPath);
    if (!abs) continue;
    let raw;
    try { raw = fs.readFileSync(abs, 'utf8'); } catch { continue; }
    const parsed = matter(raw);
    const title = pickTitle(parsed.content, abs);
    const docDir = path.dirname(abs);

    // Search payload: title + headings + first ~3000 chars of plain text.
    const headings = [];
    const headingRe = /^(#{1,4})\s+(.+?)\s*$/gm;
    let hm;
    while ((hm = headingRe.exec(parsed.content))) {
      headings.push({ level: hm[1].length, text: hm[2].trim() });
    }
    const text = plainifyMarkdown(parsed.content).slice(0, 3000);
    searchIndex.push({ path: urlPath, title, headings, text });

    // Backlinks: find every relative md link and register the reverse edge.
    const linkRe = /\[([^\]]+)\]\(([^)]+)\)/g;
    let lm;
    while ((lm = linkRe.exec(parsed.content))) {
      const linkText = lm[1];
      const href = lm[2].trim();
      if (/^(https?:|mailto:|tel:|#|\/\/)/i.test(href)) continue;
      const hashIdx = href.indexOf('#');
      const pathPart = hashIdx >= 0 ? href.slice(0, hashIdx) : href;
      if (!pathPart) continue;
      let target;
      try { target = path.resolve(docDir, pathPart); } catch { continue; }
      if (!target.toLowerCase().endsWith('.md')) continue;
      const targetUrl = toCorpusUrlPath(target);
      if (!targetUrl || targetUrl === urlPath) continue;
      const list = backlinks.get(targetUrl) || [];
      if (!list.some((e) => e.source === urlPath)) {
        list.push({ source: urlPath, sourceTitle: title, linkText });
        backlinks.set(targetUrl, list);
      }
    }
  }

  return { backlinks, searchIndex };
}

/**
 * Curated glossary served to the client for hover tooltips.
 * Kept hand-curated (small, high-signal) rather than full-parsed for predictable UX.
 */
export function buildGlossary() {
  return {
    entries: [
      // Identifier patterns (regex matched client-side)
      { kind: 'pattern', pattern: '\\bV-\\d{4}-\\d{2}-\\d{2}-[a-z0-9-]+\\b', label: 'Vision note (V-*)',
        body: 'Date+slug identifier for a vision note. Lifecycle: draft → under-review → promoted | archived. The substance, once promoted, lives in a durable artifact (ADR, design principles, feature catalog).',
        href: '#/methodology/nomenclature.md?h=vision-status-vocabulary' },
      { kind: 'pattern', pattern: '\\bV-\\d{4}-\\d{4}\\b', label: 'Vision note (legacy V-*)',
        body: 'Legacy NNNN-style vision note identifier — stable, never renamed, never reused.',
        href: '#/methodology/nomenclature.md?h=identifier-conventions' },
      { kind: 'pattern', pattern: '\\bI-\\d{4}-\\d{2}-\\d{2}-[a-z0-9-]+\\b', label: 'Backlog idea (I-*)',
        body: 'Backlog idea identifier. Lifecycle: captured → triaged → incubating → ready → active → done (or parked/archived/dropped).',
        href: '#/methodology/nomenclature.md?h=backlog-status-vocabulary' },
      { kind: 'pattern', pattern: '\\bI-\\d{4}-\\d{4}\\b', label: 'Backlog idea (legacy I-*)',
        body: 'Legacy NNNN-style backlog idea identifier.',
        href: '#/methodology/nomenclature.md?h=identifier-conventions' },
      { kind: 'pattern', pattern: '\\bTB-\\d{4}-\\d{2}-\\d{2}-[a-z0-9-]+\\b', label: 'Tracer bullet (TB-*)',
        body: 'Bounded execution slice. Lifecycle: draft → under-review → promoted | archived. Learnings get canonized into design notes, ADRs, or the feature catalog at promotion.',
        href: '#/methodology/nomenclature.md?h=tracer-bullet-status-vocabulary' },
      { kind: 'pattern', pattern: '\\bTB-\\d{4}-\\d{4}\\b', label: 'Tracer bullet (legacy TB-*)',
        body: 'Legacy NNNN-style tracer-bullet identifier.',
        href: '#/methodology/nomenclature.md?h=identifier-conventions' },
      { kind: 'pattern', pattern: '\\bR-\\d{4}-\\d{2}-\\d{2}-[a-z0-9-]+\\b', label: 'Retrofit slice (R-*)',
        body: 'Legacy retrofit slice. Lifecycle: captured → mapped → integrated → archived. Used to mine historical plans or verbal history into canonical docs.',
        href: '#/methodology/nomenclature.md?h=retrofit-status-vocabulary' },
      { kind: 'pattern', pattern: '\\bR-\\d{4}-\\d{4}\\b', label: 'Retrofit slice (legacy R-*)',
        body: 'Legacy NNNN-style retrofit slice identifier.',
        href: '#/methodology/nomenclature.md?h=identifier-conventions' },
      { kind: 'pattern', pattern: '\\bADR-\\d{4}\\b', label: 'Architecture Decision Record (ADR)',
        body: 'A recorded decision with rationale, alternatives, and consequences. Global ADRs live in product-docs/global/architecture-decisions.md; component ADRs in components/<pack>/design-decisions.md; methodology decisions in methodology/decisions/.',
        href: '#/methodology/nomenclature.md?h=canonical-decision-recording-three-scopes' },

      // Terms (exact-token, case-sensitive whole word matches)
      { kind: 'term', term: 'phase',
        body: 'In this methodology, “phase” means a workflow stage (Capture, Triage, Challenge, Promote, Design, Test, Gate, Implement, Close-out). For product-level progression, use “milestone”.',
        href: '#/methodology/workflow-overview.md?h=terminology-note' },
      { kind: 'term', term: 'milestone',
        body: 'Product-level progression and roadmap context. Distinct from methodology workflow phases.',
        href: '#/methodology/workflow-overview.md?h=terminology-note' },
      { kind: 'term', term: 'working plan',
        body: 'A live, non-canonical planning artifact under .cursor/plans/ or plans/, used for current-session incubation. Not a retrofit slice by default — materialize into V-*/I-*/TB-* once the direction converges.',
        href: '#/methodology/nomenclature.md?h=working-artifact-terminology' },
      { kind: 'term', term: 'tracer bullet',
        body: 'A vertical, bounded, testable execution slice. The atomic unit of execution in this method.',
        href: '#/methodology/tracer-bullet-method.md' },
      { kind: 'term', term: 'Global Admin',
        body: 'Operator responsible for platform-wide, IT-level concerns (e.g. cryptographic keys, system configuration).',
        href: '#/glossary?h=role-vocabulary' },
      { kind: 'term', term: 'Tenant Admin',
        body: 'Operator responsible for day-to-day tenant administration (integrations, enrollments, API keys).',
        href: '#/glossary?h=role-vocabulary' },
    ],
  };
}
