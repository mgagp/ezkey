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
import { buildCorpusIndices, buildGlossary, pickTitle } from './indices.js';
import {
  GENERATED_SKILLS_DIR,
  prepareSkillsPublicCorpus,
  publicSkillRank,
} from './skillsPublic.js';

// ── Paths ─────────────────────────────────────────────────────────────────────

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const PROJECT_ROOT = path.resolve(__dirname, '..', '..');
const DOCS_ROOT = path.join(PROJECT_ROOT, 'product-docs');

prepareSkillsPublicCorpus();

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
  { key: 'skills',      label: 'Skills',      kind: 'dir',  abs: GENERATED_SKILLS_DIR },
  { key: 'glossary',    label: 'Glossary',    kind: 'file', abs: path.join(DOCS_ROOT, 'glossary.md') },
];

const EXCLUDED_PUBLIC_SOURCE_ROOTS = [
  { label: 'source-project global canon', abs: path.join(DOCS_ROOT, 'global') },
  { label: 'source-project component packs', abs: path.join(DOCS_ROOT, 'components') },
  { label: 'editor-local assets', abs: path.join(PROJECT_ROOT, '.cursor') },
];

const PUBLIC_METHODOLOGY_SEQUENCE = [
  'README',
  'minimum-viable-method',
  'workflow-overview',
  'session-start-guide',
  'analysis-and-design-canon',
  'tracer-bullet-method',
  'testing-strategy-in-workflow',
  'quality-gates',
  'plan-incubation-workflow',
  'legacy-retrofit-workflow',
  'blitz-intake-pattern',
  'github-issues-workflow',
  'multi-branch-workflow',
  'methodological-values',
  'ai-collaboration-model',
  'nomenclature',
  'case-study-ezkey',
  'decisions',
];

const PUBLIC_TEMPLATE_SEQUENCE = [
  'README',
  'vision-note.template',
  'backlog-idea.template',
  'tracer-bullet-brief.template',
  'test-plan-slice.template',
  'legacy-plan-retrofit.template',
  'feature-brief.template',
  'component-design-brief.template',
  'functional-workflow.template',
  'decision-table.template',
  'mapping-matrix.template',
  'error-and-exception.template',
  'persistence-and-lifecycle.template',
  'screens-and-wireflow.template',
  'spec-test-traceability.template',
  'architecture-decision.template',
  'product-intent.template',
  'roadmap.template',
];

const PUBLIC_METHODOLOGY_RANK = new Map(
  PUBLIC_METHODOLOGY_SEQUENCE.map((name, index) => [name, index]),
);

const PUBLIC_TEMPLATE_RANK = new Map(
  PUBLIC_TEMPLATE_SEQUENCE.map((name, index) => [name, index]),
);

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
  const nodes = [];
  for (const entry of entries) {
    if (entry.name.startsWith('.')) continue;
    const abs = path.join(absDir, entry.name);
    const urlPath = `${urlPrefix}/${entry.name}`;
    if (entry.isDirectory()) {
      const children = walkDir(abs, urlPath);
      if (children.length > 0) {
        nodes.push({ key: entry.name, label: entry.name, kind: 'dir', path: urlPath, children });
      }
    } else if (entry.isFile() && entry.name.toLowerCase().endsWith('.md')) {
      let mtime = null;
      try { mtime = fs.statSync(abs).mtime.toISOString(); } catch { /* ignore */ }
      nodes.push({
        key: entry.name,
        label: entry.name.replace(/\.md$/i, ''),
        kind: 'file',
        path: urlPath,
        mtime,
      });
    }
  }
  nodes.sort((a, b) => compareEntriesForUiOrder(a, b, urlPrefix));
  return nodes;
}

function compareEntriesForUiOrder(left, right, urlPrefix) {
  const leftRank = publicUiRank(left, urlPrefix);
  const rightRank = publicUiRank(right, urlPrefix);
  if (leftRank !== rightRank) return leftRank - rightRank;

  if (left.kind !== right.kind) {
    if (left.kind === 'file' && isReadmeEntry(left)) return -1;
    if (right.kind === 'file' && isReadmeEntry(right)) return 1;
  }

  if (left.kind !== right.kind) {
    return left.kind === 'dir' ? -1 : 1;
  }

  return left.label.localeCompare(right.label);
}

function publicUiRank(entry, urlPrefix) {
  const entryName = entry.key.replace(/\.md$/i, '');

  if (urlPrefix === 'skills') {
    return publicSkillRank(entryName);
  }

  if (urlPrefix === 'methodology') {
    return PUBLIC_METHODOLOGY_RANK.get(entryName) ?? Number.MAX_SAFE_INTEGER;
  }

  if (urlPrefix === 'templates') {
    return PUBLIC_TEMPLATE_RANK.get(entryName) ?? Number.MAX_SAFE_INTEGER;
  }

  if (isReadmeEntry(entry)) {
    return -1;
  }

  return Number.MAX_SAFE_INTEGER;
}

function isReadmeEntry(entry) {
  return entry.key.toLowerCase() === 'readme.md' || entry.key.toLowerCase() === 'readme';
}

function collectFileNodes(nodes, acc = []) {
  for (const node of nodes) {
    if (node.kind === 'file') {
      acc.push(node.path);
      continue;
    }
    if (node.children) collectFileNodes(node.children, acc);
  }
  return acc;
}

function stripFencedCodeBlocks(markdown) {
  return markdown.replace(/```[\s\S]*?```/g, '');
}

function extractInlineLinks(markdown) {
  const links = [];
  const re = /\[[^\]]+\]\(([^)]+)\)/g;
  let match;
  while ((match = re.exec(markdown)) !== null) {
    const rawHref = match[1].trim();
    const href = rawHref.replace(/^<|>$/g, '').split(/\s+"/)[0];
    links.push(href);
  }
  return links;
}

function excludedPublicRootFor(absTarget) {
  return EXCLUDED_PUBLIC_SOURCE_ROOTS.find(
    (root) => absTarget === root.abs || absTarget.startsWith(root.abs + path.sep),
  );
}

export function auditPublicPublicationBoundary(corpus = CORPUS) {
  const publicTree = filterTree(buildTree(corpus));
  const publicFiles = collectFileNodes(publicTree);
  const issues = [];

  for (const urlPath of publicFiles) {
    const abs = resolveCorpusPath(urlPath, corpus);
    if (!abs || !fs.existsSync(abs)) continue;

    const parsed = matter(fs.readFileSync(abs, 'utf8'));
    const markdown = stripFencedCodeBlocks(parsed.content || '');

    for (const href of extractInlineLinks(markdown)) {
      if (/^(https?:|mailto:|tel:|#|\/\/)/i.test(href)) continue;
      if (href.startsWith('/')) continue;

      const hashIndex = href.indexOf('#');
      const pathPart = hashIndex >= 0 ? href.slice(0, hashIndex) : href;
      if (!pathPart) continue;

      const normalizedPath = pathPart.replace(/\\/g, '/');
      if (/(^|\/)(global|components|\.cursor)\//.test(normalizedPath)) {
        issues.push(`${urlPath} -> ${href} escapes the public publication boundary`);
        continue;
      }

      let target;
      try {
        target = path.resolve(path.dirname(abs), pathPart);
      } catch {
        continue;
      }

      const excludedRoot = excludedPublicRootFor(target);
      if (excludedRoot) {
        issues.push(`${urlPath} -> ${href} points to ${excludedRoot.label}`);
        continue;
      }

      const ext = path.extname(target).toLowerCase();
      if (!fs.existsSync(target)) continue;
      if (ext === '.md' && !toCorpusUrlPath(target, corpus)) {
        issues.push(`${urlPath} -> ${href} is not published in the public corpus`);
        continue;
      }
      if (ext === '.html' && !toCorpusAssetPath(target, corpus)) {
        issues.push(`${urlPath} -> ${href} is not published as a public asset`);
      }
    }
  }

  if (issues.length > 0) {
    throw new Error(
      `Public methodology publication boundary audit failed:\n- ${issues.join('\n- ')}`,
    );
  }
}

export function rebuildGeneratedPublicArtifacts() {
  prepareSkillsPublicCorpus();
  auditPublicPublicationBoundary();
  prepareDownloadPack({
    tree: filterTree(buildTree()),
    resolveCorpusPath,
  });
}

rebuildGeneratedPublicArtifacts();

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

export function toCorpusAssetPath(absPath, corpus = CORPUS) {
  for (const root of corpus) {
    if (root.kind !== 'dir') continue;
    if (absPath === root.abs || absPath.startsWith(root.abs + path.sep)) {
      const rel = path.relative(root.abs, absPath).split(path.sep).join('/');
      return rel ? `${root.key}/${rel}` : root.key;
    }
  }
  return null;
}

export function publicSupplementDirs(corpus = CORPUS) {
  return corpus
    .filter((root) => root.kind === 'dir')
    .map((root) => ({
      mountPath: `/${root.key}/view`,
      abs: path.join(root.abs, 'view'),
    }))
    .filter((entry) => fs.existsSync(entry.abs));
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
  if (target.toLowerCase().endsWith('.md')) {
  const urlPath = toCorpusUrlPath(target);
  if (!urlPath) return;
  token.href = `#/${urlPath}${fragment ? `?h=${encodeURIComponent(fragment)}` : ''}`;
    return;
}
  if (!target.toLowerCase().endsWith('.html')) return;
  const assetPath = toCorpusAssetPath(target);
  if (!assetPath) return;
  token.href = `/${assetPath}${fragment ? `#${fragment}` : ''}`;
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

// ── Express transport layer ───────────────────────────────────────────────────

const app = express();
const PORT = process.env.PORT ? Number(process.env.PORT) : 4321;

app.use(express.static(path.join(__dirname, 'public')));
for (const supplement of publicSupplementDirs()) {
  app.use(supplement.mountPath, express.static(supplement.abs));
}

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

app.get('/api/search-index', (_req, res) => {
  res.json({ docs: SEARCH_INDEX });
});

// ── Phase 3: backlinks + search index (computed via indices.js) ──────────────

let BACKLINKS = new Map(); // url-path -> [{ source, sourceTitle, linkText }]
let SEARCH_INDEX = [];     // [{ path, title, headings, text }]

function rebuildCorpusIndices() {
  const result = buildCorpusIndices({
    buildTree: () => buildTree(),
    resolveCorpusPath: (p) => resolveCorpusPath(p),
    toCorpusUrlPath: (p) => toCorpusUrlPath(p),
  });
  BACKLINKS = result.backlinks;
  SEARCH_INDEX = result.searchIndex;
}
export { rebuildCorpusIndices };

function backlinksFor(urlPath) {
  return BACKLINKS.get(urlPath) || [];
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

// Only listen when executed directly (e.g. `node server.js`). When imported
// by build.js or tests, exports remain available without starting a server.
const invokedDirectly = (() => {
  try {
    const entry = process.argv[1] ? fs.realpathSync(process.argv[1]) : '';
    const self = fileURLToPath(import.meta.url);
    return entry && fs.realpathSync(self) === entry;
  } catch {
    return false;
  }
})();

if (invokedDirectly) {
  app.listen(PORT, () => {
    rebuildCorpusIndices();
    // eslint-disable-next-line no-console
    console.log(
      `Ezkey Method · Local Explorer running at http://localhost:${PORT} ` +
        `(${SEARCH_INDEX.length} docs indexed, ${BACKLINKS.size} backlink targets)`,
    );
  });
}
