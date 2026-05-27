/*
 * Ezkey Method · Local Explorer — server
 *
 * Local-only Node/Express server for browsing the Ezkey methodology corpus.
 *
 * Architecture note (Phase 4 preparation):
 *   The rendering core is exposed as pure functions: `buildTree`, `renderDoc`,
 *   `extractToc`. Express is a thin transport layer on top. A future static
 *   pre-build (Phase 4) can reuse these functions without Express.
 */

import express from 'express';
import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import matter from 'gray-matter';
import { Marked } from 'marked';
import { gfmHeadingId } from 'marked-gfm-heading-id';
import { markedHighlight } from 'marked-highlight';
import hljs from 'highlight.js';

// ── Paths ─────────────────────────────────────────────────────────────────────

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..', '..');
const DOCS_ROOT = path.join(PROJECT_ROOT, 'product-docs');

// ── Phase 2 corpus metadata (phases / tracks / glossary) ─────────────────────

/** Load a JSON file relative to the site folder; returns null on failure. */
function loadJson(relPath) {
  try {
    const abs = path.join(__dirname, relPath);
    return JSON.parse(fs.readFileSync(abs, 'utf8'));
  } catch (err) {
    console.warn(`[site] failed to load ${relPath}:`, err.message);
    return null;
  }
}

const PHASES = loadJson('phases.json') || { phases: [], files: {} };
const TRACKS = loadJson('tracks.json') || { tracks: [] };

/** Look up the workflow phase id for a corpus url path. */
export function phaseForPath(urlPath) {
  return (PHASES.files && PHASES.files[urlPath]) || null;
}

/**
 * Corpus declaration. Each entry is either a directory root or a single file.
 * `key` becomes the first segment in client-side URLs (e.g. `#/methodology/...`).
 */
export const CORPUS = [
  { key: 'methodology', label: 'Methodology', kind: 'dir',  abs: path.join(DOCS_ROOT, 'methodology') },
  { key: 'templates',   label: 'Templates',   kind: 'dir',  abs: path.join(DOCS_ROOT, 'templates')   },
  { key: 'glossary',    label: 'Glossary',    kind: 'file', abs: path.join(DOCS_ROOT, 'glossary.md') },
];

// ── Pure: tree ────────────────────────────────────────────────────────────────

/**
 * Build a navigation tree across the corpus roots. Only `.md` files are kept.
 * Folders that contain no `.md` (directly or transitively) are dropped.
 *
 * Returned node shape:
 *   { key, label, kind: 'root'|'dir'|'file', path, children? }
 *   where `path` is the URL-style path used by the client (root key + relative).
 */
export function buildTree(corpus = CORPUS) {
  return corpus
    .map((root) => {
      if (root.kind === 'file') {
        if (!fs.existsSync(root.abs)) return null;
        return {
          key: root.key,
          label: root.label,
          kind: 'file',
          path: root.key,
        };
      }
      if (!fs.existsSync(root.abs)) return null;
      const children = walkDir(root.abs, root.key);
      return {
        key: root.key,
        label: root.label,
        kind: 'root',
        path: root.key,
        children,
      };
    })
    .filter(Boolean);
}

function walkDir(absDir, urlPrefix) {
  const entries = fs.readdirSync(absDir, { withFileTypes: true });
  const dirs = [];
  const files = [];
  for (const entry of entries) {
    if (entry.name.startsWith('.')) continue;
    const abs = path.join(absDir, entry.name);
    const urlPath = `${urlPrefix}/${entry.name}`;
    if (entry.isDirectory()) {
      const children = walkDir(abs, urlPath);
      if (children.length > 0) {
        dirs.push({ key: entry.name, label: entry.name, kind: 'dir', path: urlPath, children });
      }
    } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.md')) {
      let mtime = null;
      try { mtime = fs.statSync(abs).mtime.toISOString(); } catch { /* ignore */ }
      files.push({
        key: entry.name,
        label: entry.name.replace(/\.md$/i, ''),
        kind: 'file',
        path: urlPath,
        mtime,
      });
    }
  }
  dirs.sort((a, b) => a.label.localeCompare(b.label));
  files.sort((a, b) => a.label.localeCompare(b.label));
  return [...dirs, ...files];
}

// ── Pure: path resolution ─────────────────────────────────────────────────────

/**
 * Resolve a URL-style corpus path (e.g. `methodology/workflow-overview.md`)
 * to an absolute filesystem path, with directory traversal protection.
 * Returns null if the path is invalid or escapes the corpus boundary.
 */
export function resolveCorpusPath(urlPath, corpus = CORPUS) {
  if (!urlPath || typeof urlPath !== 'string') return null;
  const normalized = urlPath.replace(/^\/+/, '').replace(/\\/g, '/');
  const [rootKey, ...rest] = normalized.split('/');
  const root = corpus.find((r) => r.key === rootKey);
  if (!root) return null;
  if (root.kind === 'file') {
    if (rest.length > 0) return null;
    return root.abs;
  }
  const candidate = path.resolve(root.abs, rest.join('/'));
  if (!candidate.startsWith(root.abs + path.sep) && candidate !== root.abs) return null;
  if (!candidate.toLowerCase().endsWith('.md')) return null;
  return candidate;
}

/** Convert an absolute file path back to its URL-style corpus path. */
export function toCorpusUrlPath(absPath, corpus = CORPUS) {
  for (const root of corpus) {
    if (root.kind === 'file' && absPath === root.abs) return root.key;
    if (root.kind === 'dir' && (absPath === root.abs || absPath.startsWith(root.abs + path.sep))) {
      const rel = path.relative(root.abs, absPath).split(path.sep).join('/');
      return rel ? `${root.key}/${rel}` : root.key;
    }
  }
  return null;
}

// ── Pure: markdown rendering ──────────────────────────────────────────────────

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Build a per-document Marked instance with:
 *   - GFM heading ids (stable anchors for the TOC),
 *   - highlight.js syntax highlighting,
 *   - a Mermaid placeholder (Phase 2 will render interactively),
 *   - relative-link rewriting that targets the client-side hash router.
 */
function buildMarked(docAbsDir) {
  const m = new Marked();
  m.use(gfmHeadingId());
  m.use(
    markedHighlight({
      langPrefix: 'hljs language-',
      highlight(code, lang) {
        if (!lang || lang === 'mermaid') return code;
        const language = hljs.getLanguage(lang) ? lang : 'plaintext';
        try {
          return hljs.highlight(code, { language, ignoreIllegals: true }).value;
        } catch {
          return escapeHtml(code);
        }
      },
    }),
  );
  m.use({
    extensions: [
      {
        name: 'mermaidBlock',
        level: 'block',
        start(src) {
          const i = src.indexOf('```mermaid');
          return i < 0 ? undefined : i;
        },
        tokenizer(src) {
          const rule = /^```mermaid\n([\s\S]*?)\n```\n?/;
          const match = rule.exec(src);
          if (match) {
            return { type: 'mermaidBlock', raw: match[0], code: match[1] };
          }
          return undefined;
        },
        renderer(token) {
          return (
            `<figure class="diagram-placeholder" data-diagram="mermaid">` +
            `<figcaption class="diagram-badge">Mermaid diagram — interactive rendering in Phase 2</figcaption>` +
            `<pre><code class="language-mermaid">${escapeHtml(token.code)}</code></pre>` +
            `</figure>`
          );
        },
      },
    ],
    walkTokens(token) {
      if (token.type !== 'link' || !token.href) return;
      rewriteLink(token, docAbsDir);
    },
  });
  return m;
}

function rewriteLink(token, docAbsDir) {
  const href = token.href;
  // Leave external / anchor / protocol-relative links alone.
  if (/^(https?:|mailto:|tel:|#|\/\/)/i.test(href)) return;
  // Split optional hash fragment.
  const hashIndex = href.indexOf('#');
  const pathPart = hashIndex >= 0 ? href.slice(0, hashIndex) : href;
  const fragment = hashIndex >= 0 ? href.slice(hashIndex + 1) : '';
  if (!pathPart) {
    // Pure fragment — already absolute on the page.
    return;
  }
  let target;
  try {
    target = path.resolve(docAbsDir, pathPart);
  } catch {
    return;
  }
  // Only rewrite if the target points to a corpus markdown file.
  if (!target.toLowerCase().endsWith('.md')) return;
  const urlPath = toCorpusUrlPath(target);
  if (!urlPath) return;
  token.href = `#/${urlPath}${fragment ? `?h=${encodeURIComponent(fragment)}` : ''}`;
}

/**
 * Extract H2/H3 headings from rendered HTML to build the right-hand TOC.
 */
export function extractToc(html) {
  const re = /<h([23])\s+id="([^"]+)"[^>]*>([\s\S]*?)<\/h\1>/g;
  const toc = [];
  let m;
  while ((m = re.exec(html))) {
    toc.push({
      level: Number(m[1]),
      id: m[2],
      text: stripTags(m[3]).trim(),
    });
  }
  return toc;
}

function stripTags(s) {
  return s.replace(/<[^>]+>/g, '');
}

/**
 * Render a single corpus document to its viewable form.
 * Returns { path, title, frontmatter, html, toc, phase, mtime, backlinks }.
 */
export function renderDoc(absPath) {
  const raw = fs.readFileSync(absPath, 'utf8');
  const parsed = matter(raw);
  const docDir = path.dirname(absPath);
  const m = buildMarked(docDir);
  const html = m.parse(parsed.content);
  const toc = extractToc(html);
  const title = pickTitle(parsed.content, absPath);
  const urlPath = toCorpusUrlPath(absPath);
  let mtime = null;
  try { mtime = fs.statSync(absPath).mtime.toISOString(); } catch { /* ignore */ }
  return {
    path: urlPath,
    title,
    frontmatter: parsed.data || {},
    html,
    toc,
    phase: phaseForPath(urlPath),
    mtime,
    backlinks: backlinksFor(urlPath),
  };
}

function pickTitle(markdown, absPath) {
  const m = /^#\s+(.+?)\s*$/m.exec(markdown);
  if (m) return m[1].trim();
  return path.basename(absPath).replace(/\.md$/i, '');
}

// ── Express transport layer ───────────────────────────────────────────────────

const app = express();
const PORT = process.env.PORT ? Number(process.env.PORT) : 4321;

app.use(express.static(path.join(__dirname, 'public')));

app.get('/api/tree', (_req, res) => {
  try {
    res.json({ corpus: buildTree() });
  } catch (err) {
    console.error('[/api/tree]', err);
    res.status(500).json({ error: 'tree_build_failed', message: String(err.message || err) });
  }
});

app.get('/api/phases', (_req, res) => {
  res.json(PHASES);
});

app.get('/api/tracks', (_req, res) => {
  res.json(TRACKS);
});

app.get('/api/glossary', (_req, res) => {
  res.json(buildGlossary());
});

// ── Phase 3: backlinks + search index ────────────────────────────────────────

let BACKLINKS = new Map(); // url-path -> [{ source, title }]
let SEARCH_INDEX = [];     // [{ path, title, headings, text }]

/**
 * Walk every corpus file once to build:
 *   - BACKLINKS: reverse-link map (target → list of {source, title}),
 *   - SEARCH_INDEX: per-doc search payload (title + headings + plain excerpt).
 */
function buildCorpusIndices() {
  BACKLINKS = new Map();
  SEARCH_INDEX = [];
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
    SEARCH_INDEX.push({ path: urlPath, title, headings, text });

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
      const list = BACKLINKS.get(targetUrl) || [];
      // Dedupe by source.
      if (!list.some((e) => e.source === urlPath)) {
        list.push({ source: urlPath, sourceTitle: title, linkText });
        BACKLINKS.set(targetUrl, list);
      }
    }
  }
}

function plainifyMarkdown(md) {
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

function backlinksFor(urlPath) {
  return BACKLINKS.get(urlPath) || [];
}

app.get('/api/search-index', (_req, res) => {
  res.json({ docs: SEARCH_INDEX });
});

/**
 * Curated glossary served to the client for hover tooltips.
 * Sourced from product-docs/glossary.md and product-docs/methodology/nomenclature.md.
 * Kept hand-curated (small, high-signal) rather than full-parsed for predictable UX.
 */
function buildGlossary() {
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

app.get('/api/doc', (req, res) => {
  const urlPath = String(req.query.path || '');
  const abs = resolveCorpusPath(urlPath);
  if (!abs) {
    return res.status(400).json({ error: 'invalid_path', path: urlPath });
  }
  if (!fs.existsSync(abs)) {
    return res.status(404).json({ error: 'not_found', path: urlPath });
  }
  try {
    res.json(renderDoc(abs));
  } catch (err) {
    console.error('[/api/doc]', urlPath, err);
    res.status(500).json({ error: 'render_failed', message: String(err.message || err) });
  }
});

app.listen(PORT, () => {
  buildCorpusIndices();
  // eslint-disable-next-line no-console
  console.log(
    `Ezkey Method · Local Explorer running at http://localhost:${PORT} ` +
      `(${SEARCH_INDEX.length} docs indexed, ${BACKLINKS.size} backlink targets)`,
  );
});
