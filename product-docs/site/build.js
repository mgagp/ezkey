/*
 * Ezkey Method · Explorer — static build for Cloudflare Pages (Phase 4).
 *
 * Reuses the pure rendering core from server.js + indices.js. Produces a
 * fully static `dist/` tree:
 *
 *   dist/
 *   ├── index.html                       (home shell, hydratable)
 *   ├── map/index.html                   (cognitive map view)
 *   ├── glossary/index.html              (single-file corpus entry)
 *   ├── <urlPath without .md>/index.html (per-doc pre-rendered shells)
 *   ├── api/
 *   │   ├── tree.json
 *   │   ├── phases.json
 *   │   ├── tracks.json
 *   │   ├── glossary.json
 *   │   ├── search-index.json
 *   │   └── doc/<urlPath>.json
 *   ├── styles.css + *.js (mirrored from public/)
 *   ├── sitemap.xml
 *   └── robots.txt
 *
 * Idempotent: deletes dist/ first, then rebuilds from source-of-truth corpus.
 */

import path from 'node:path';
import fs from 'node:fs';
import { fileURLToPath } from 'node:url';
import {
  buildTree,
  renderDoc,
  resolveCorpusPath,
  toCorpusUrlPath,
  CORPUS,
  rebuildCorpusIndices,
} from './server.js';
import { buildGlossary } from './indices.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const SITE_DIR = __dirname;
const PUBLIC_DIR = path.join(SITE_DIR, 'public');
const DIST_DIR = path.join(SITE_DIR, 'dist');

const PHASES = JSON.parse(fs.readFileSync(path.join(SITE_DIR, 'phases.json'), 'utf8'));
const TRACKS = JSON.parse(fs.readFileSync(path.join(SITE_DIR, 'tracks.json'), 'utf8'));

const SITE_ORIGIN = process.env.SITE_ORIGIN || 'https://methodology.ezkey.org';
const CF_ANALYTICS_TOKEN = process.env.CF_ANALYTICS_TOKEN || '';

const ANALYTICS_SNIPPET = CF_ANALYTICS_TOKEN
  ? `<script defer src="https://static.cloudflareinsights.com/beacon.min.js" data-cf-beacon='{"token":"${CF_ANALYTICS_TOKEN}"}'></script>`
  : '';

// ── fs helpers ────────────────────────────────────────────────────────────────

function rmrf(p) {
  if (fs.existsSync(p)) fs.rmSync(p, { recursive: true, force: true });
}

function mkdirp(p) {
  fs.mkdirSync(p, { recursive: true });
}

function writeJson(absPath, value) {
  mkdirp(path.dirname(absPath));
  fs.writeFileSync(absPath, JSON.stringify(value), 'utf8');
}

function writeText(absPath, value) {
  mkdirp(path.dirname(absPath));
  fs.writeFileSync(absPath, value, 'utf8');
}

function copyTree(src, dst) {
  if (!fs.existsSync(src)) return;
  mkdirp(dst);
  for (const entry of fs.readdirSync(src, { withFileTypes: true })) {
    if (entry.name === 'index.html') continue; // shell template only, generated per page
    const s = path.join(src, entry.name);
    const d = path.join(dst, entry.name);
    if (entry.isDirectory()) copyTree(s, d);
    else fs.copyFileSync(s, d);
  }
}

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

// ── corpus iteration ──────────────────────────────────────────────────────────

function allFiles(tree) {
  const out = [];
  function walk(node) {
    if (node.kind === 'file') out.push(node.path);
    if (node.children) for (const c of node.children) walk(c);
  }
  for (const root of tree) walk(root);
  return out;
}

/** Convert a corpus url path (e.g. `methodology/foo.md`) into a clean
 * canonical URL slug (e.g. `methodology/foo`). The glossary single-file
 * entry has no `.md` extension to begin with, so the regex is a no-op. */
function cleanSlug(urlPath) {
  return urlPath.replace(/\.md$/i, '');
}

// ── HTML shell template ───────────────────────────────────────────────────────

function loadShellTemplate() {
  return fs.readFileSync(path.join(PUBLIC_DIR, 'index.html'), 'utf8');
}

/**
 * Inject per-page metadata and content into the shared shell template.
 *
 * @param {object} opts
 * @param {string} opts.title
 * @param {string} opts.description
 * @param {string} opts.canonical                 absolute URL.
 * @param {string} opts.contentHtml               replaces the home placeholder
 *                                                 inside <article id="doc">.
 */
function renderShell({ title, description, canonical, contentHtml }) {
  let html = loadShellTemplate();

  // Rewrite relative asset URLs to absolute root paths so nested pages
  // (e.g. /methodology/foo/) still resolve to /styles.css and /app.js.
  html = html
    .replace(/href="styles\.css"/g, 'href="/styles.css"')
    .replace(/src="app\.js"/g, 'src="/app.js"');

  // Title
  html = html.replace(/<title>[^<]*<\/title>/, `<title>${escapeHtml(title)}</title>`);

  // Head injections: OG/meta + static flag + (optional) analytics.
  const headExtras =
    `<meta name="description" content="${escapeHtml(description)}" />\n` +
    `<link rel="canonical" href="${escapeHtml(canonical)}" />\n` +
    `<meta property="og:type" content="article" />\n` +
    `<meta property="og:title" content="${escapeHtml(title)}" />\n` +
    `<meta property="og:description" content="${escapeHtml(description)}" />\n` +
    `<meta property="og:url" content="${escapeHtml(canonical)}" />\n` +
    `<meta property="og:site_name" content="ezkey methodology" />\n` +
    `<meta name="twitter:card" content="summary" />\n` +
    `<script>window.__EZKEY_STATIC__ = true;</script>\n` +
    (ANALYTICS_SNIPPET ? `${ANALYTICS_SNIPPET}\n` : '');

  html = html.replace('</head>', `${headExtras}</head>`);

  // Replace the home placeholder with pre-rendered content when applicable.
  // The doc article contains nested <article class="persona-card">, so a plain
  // non-greedy regex would stop at the first inner </article>. Walk the tag
  // structure to find the matching closing </article>.
  if (contentHtml) {
    const openTag = '<article id="doc" class="doc">';
    const openIdx = html.indexOf(openTag);
    if (openIdx >= 0) {
      const innerStart = openIdx + openTag.length;
      const closeIdx = findMatchingArticleClose(html, innerStart);
      if (closeIdx > innerStart) {
        html =
          html.slice(0, innerStart) + contentHtml + html.slice(closeIdx);
      }
    }
  }

  return html;
}

/**
 * Starting at `from`, find the index of the </article> tag that closes the
 * outer <article> opened just before. Counts nested <article ...> opens.
 */
function findMatchingArticleClose(html, from) {
  const openRe = /<article\b[^>]*>/gi;
  const closeRe = /<\/article\s*>/gi;
  openRe.lastIndex = from;
  closeRe.lastIndex = from;
  let depth = 1;
  let cursor = from;
  while (depth > 0) {
    openRe.lastIndex = cursor;
    closeRe.lastIndex = cursor;
    const o = openRe.exec(html);
    const c = closeRe.exec(html);
    if (!c) return -1;
    if (o && o.index < c.index) {
      depth++;
      cursor = openRe.lastIndex;
    } else {
      depth--;
      if (depth === 0) return c.index;
      cursor = closeRe.lastIndex;
    }
  }
  return -1;
}

function summarize(text, max = 200) {
  const flat = String(text || '').replace(/\s+/g, ' ').trim();
  if (flat.length <= max) return flat;
  return flat.slice(0, max - 1).trimEnd() + '…';
}

// ── main ──────────────────────────────────────────────────────────────────────

function build() {
  console.log(`[build] output → ${DIST_DIR}`);
  rmrf(DIST_DIR);
  mkdirp(DIST_DIR);

  // 1. Mirror public/ assets (everything except index.html).
  copyTree(PUBLIC_DIR, DIST_DIR);

  // 2. Build indices and snapshot the dynamic JSON endpoints.
  rebuildCorpusIndices();
  const tree = buildTree();
  writeJson(path.join(DIST_DIR, 'api', 'tree.json'), { corpus: tree });
  writeJson(path.join(DIST_DIR, 'api', 'phases.json'), PHASES);
  writeJson(path.join(DIST_DIR, 'api', 'tracks.json'), TRACKS);
  writeJson(path.join(DIST_DIR, 'api', 'glossary.json'), buildGlossary());

  // 3. Per-doc snapshots + pre-rendered shells.
  const files = allFiles(tree);
  const searchIndexDocs = [];
  const sitemapEntries = [];

  for (const urlPath of files) {
    const abs = resolveCorpusPath(urlPath);
    if (!abs) continue;
    const data = renderDoc(abs);
    const slug = cleanSlug(urlPath);

    // JSON snapshot.
    writeJson(path.join(DIST_DIR, 'api', 'doc', `${slug}.json`), data);

    // Search index entry (mirrors what the dynamic /api/search-index returned).
    const headings = (data.toc || []).map((t) => ({ level: t.level, text: t.text }));
    searchIndexDocs.push({
      path: urlPath,
      title: data.title,
      headings,
      text: summarize(stripHtml(data.html), 3000),
    });

    // Pre-rendered shell.
    const canonical = `${SITE_ORIGIN}/${slug}/`;
    const html = renderShell({
      title: `${data.title} · ezkey methodology`,
      description: summarize(stripHtml(data.html), 200),
      canonical,
      contentHtml: data.html,
    });
    writeText(path.join(DIST_DIR, slug, 'index.html'), html);
    sitemapEntries.push({ loc: canonical, lastmod: data.mtime });
  }

  // 4. Static search-index built from the same data path.
  writeJson(path.join(DIST_DIR, 'api', 'search-index.json'), { docs: searchIndexDocs });

  // 5. Home shell (kept dynamic — JS router fills in the home panel).
  writeText(
    path.join(DIST_DIR, 'index.html'),
    renderShell({
      title: 'ezkey · methodology',
      description:
        'Working explorer for the ezkey methodology corpus: workflow, design canon, ' +
        'tracer-bullet method, templates, and glossary.',
      canonical: `${SITE_ORIGIN}/`,
      contentHtml: '', // keep the original home placeholder from the template
    }),
  );
  sitemapEntries.unshift({ loc: `${SITE_ORIGIN}/`, lastmod: new Date().toISOString() });

  // 6. Map shell (also JS-driven; we just emit the wrapper).
  writeText(
    path.join(DIST_DIR, 'map', 'index.html'),
    renderShell({
      title: 'Workflow map · ezkey methodology',
      description: 'Cognitive map of the ezkey methodology workflow.',
      canonical: `${SITE_ORIGIN}/map/`,
      contentHtml: '<div id="home" class="home"><p class="loading">Loading map…</p></div>',
    }),
  );

  // 7. sitemap.xml + robots.txt
  writeText(path.join(DIST_DIR, 'sitemap.xml'), renderSitemap(sitemapEntries));
  writeText(
    path.join(DIST_DIR, 'robots.txt'),
    `User-agent: *\nAllow: /\nSitemap: ${SITE_ORIGIN}/sitemap.xml\n`,
  );

  console.log(
    `[build] done · ${files.length} docs · sitemap with ${sitemapEntries.length} URLs · ` +
      `analytics=${CF_ANALYTICS_TOKEN ? 'on' : 'off'}`,
  );
}

function stripHtml(html) {
  return String(html || '')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<[^>]+>/g, ' ')
    .replace(/&[a-z#0-9]+;/gi, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function renderSitemap(entries) {
  const items = entries
    .map(({ loc, lastmod }) => {
      const lm = lastmod ? `<lastmod>${escapeHtml(lastmod)}</lastmod>` : '';
      return `<url><loc>${escapeHtml(loc)}</loc>${lm}</url>`;
    })
    .join('\n');
  return (
    `<?xml version="1.0" encoding="UTF-8"?>\n` +
    `<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${items}\n</urlset>\n`
  );
}

// Silence the unused-import lint: CORPUS is re-exported by server.js and may
// be useful for downstream scripts; we keep the explicit import for readability.
void CORPUS;
void toCorpusUrlPath;

build();
