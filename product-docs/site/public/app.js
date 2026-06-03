/*
 * Ezkey Method · Local Explorer — client app
 *
 * Responsibilities:
 *   - Bootstrap: fetch /api/tree, /api/phases, /api/tracks, /api/glossary.
 *   - Render the left navigation tree (with optional track pastilles).
 *   - Hash router: #/<corpus-path>  (e.g. #/methodology/workflow-overview.md).
 *   - Doc loader: fetch /api/doc, inject HTML, build the right-hand TOC.
 *   - Scroll-spy: highlight the current heading in the TOC.
 *   - Tree state: collapsed/expanded folders persisted in localStorage.
 *   - Phase 2: phase ribbon, persona-track wizard, Mermaid render, glossary tooltips.
 */

import {
  initWizard,
  activate as activateTrack,
  getActiveTrack,
  getActiveStep,
  getActiveStepIndex,
  isFileInActiveTrack,
  stepIndexForFile,
  syncStepFromFile,
  setActive as setActiveTrack,
} from './wizard.js';
import { renderMermaidIn } from './mermaid-loader.js';
import { decorateGlossaryIn, setupGlossaryTooltips } from './glossary.js';
import { setupSearchShortcut, openSearch } from './search.js';
import { setupPresentationShortcuts } from './presentation.js';
import { renderMap } from './map.js';
import {
  fetchTree,
  fetchPhases,
  fetchTracks,
  fetchGlossary,
  fetchDoc,
  fetchVersion,
  isStatic,
} from './apiClient.js';

const STORAGE_TREE_KEY = 'ezkey-method-explorer:tree:v1';
const STORAGE_THEME_KEY = 'ezkey-method-explorer:theme:v1';

const els = {
  tree: document.getElementById('tree'),
  doc: document.getElementById('doc'),
  breadcrumb: document.getElementById('breadcrumb'),
  toc: document.getElementById('toc'),
  phaseRibbon: document.getElementById('phase-ribbon'),
  home: () => document.getElementById('home'),
};

let treeData = null;
let currentPath = null;
let scrollSpyObserver = null;
let pathToNodeIndex = new Map();
let slugToCorpusPath = new Map(); // static-mode: '/methodology/foo/' -> 'methodology/foo.md'
let phasesData = { phases: [], files: {} };
let phasesById = new Map();
let tracksData = { tracks: [] };
let glossaryData = { entries: [] };
let initialHomeHtml = '';

// ── Boot ─────────────────────────────────────────────────────────────────────

window.addEventListener('hashchange', handleRoute);
boot();

async function boot() {
  try {
    const [treeJson, phasesJson, tracksJson, glossaryJson, versionJson] = await Promise.allSettled([
      fetchTree(),
      fetchPhases(),
      fetchTracks(),
      fetchGlossary(),
      fetchVersion(),
    ]);
    if (treeJson.status !== 'fulfilled') {
      throw new Error(treeJson.reason?.message || 'tree fetch failed');
    }
    treeData = treeJson.value.corpus || [];
    indexTree(treeData);
    if (phasesJson.status === 'fulfilled') {
      phasesData = phasesJson.value;
      phasesById = new Map((phasesData.phases || []).map((p) => [p.id, p]));
    }
    if (tracksJson.status === 'fulfilled') {
      tracksData = tracksJson.value;
    }
    if (glossaryJson.status === 'fulfilled') {
      glossaryData = glossaryJson.value;
    }
    if (versionJson.status === 'fulfilled') {
      renderVersionBadge(versionJson.value);
    }
    setupHomePersonaCards();
    setupGlossaryTooltips();
    setupSearchShortcut();
    setupPresentationShortcuts();
    setupSearchButton();
    setupThemeToggle();
    initialHomeHtml = els.doc.innerHTML;
    initWizard({
      tracks: tracksData.tracks || [],
      onTrackChange: (track) => {
        renderTree(); // re-render to apply / remove pastilles
        if (track) {
          // Auto-navigate to the first step on initial activation.
          const step = getActiveStep();
          if (step) navigateToStep(step);
        }
      },
      onStepChange: (track, step) => {
        if (step) navigateToStep(step);
      },
    });
    renderTree();
    handleRoute();
  } catch (err) {
    els.tree.innerHTML = `<div class="error">Failed to load corpus tree: ${escapeHtml(
      err.message || String(err),
    )}</div>`;
  }
}

function navigateToStep(step) {
  if (!step || !step.file) return;
  const hash = step.anchor
    ? `#/${step.file}?h=${encodeURIComponent(step.anchor)}`
    : `#/${step.file}`;
  if (window.location.hash !== hash) {
    window.location.hash = hash;
  }
}

function renderVersionBadge(versionInfo) {
  const el = document.getElementById('version-badge');
  if (!el || !versionInfo || !versionInfo.version) return;
  const label = `v${versionInfo.version}`;
  el.textContent = label;
  el.title = versionInfo.released
    ? `Methodology ${label} · released ${versionInfo.released}`
    : `Methodology ${label}`;
  el.hidden = false;
  el.href = '#/methodology/release-notes/README.md';
}

function setupHomePersonaCards() {
  // Activate persona cards on the home page as track entry points.
  document.addEventListener('click', (e) => {
    const card = e.target.closest('.persona-card[data-track]');
    if (!card) return;
    e.preventDefault();
    activateTrack(card.dataset.track);
  });
}

function setupSearchButton() {
  const btn = document.getElementById('search-btn');
  if (btn) btn.addEventListener('click', () => openSearch());
}

function readStoredTheme() {
  try {
    return localStorage.getItem(STORAGE_THEME_KEY);
  } catch {
    return null;
  }
}

function prefersDarkScheme() {
  return (
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-color-scheme: dark)').matches
  );
}

function shouldUseDarkTheme(stored = readStoredTheme()) {
  if (stored === 'dark') return true;
  if (stored === 'light') return false;
  return prefersDarkScheme();
}

function applyTheme(isDark) {
  document.body.classList.toggle('is-dark', isDark);
}

function setupThemeToggle() {
  const btn = document.getElementById('theme-btn');
  applyTheme(shouldUseDarkTheme());
  if (!btn) return;
  const sync = () => {
    btn.textContent = document.body.classList.contains('is-dark') ? '☼' : '☾';
    btn.title = document.body.classList.contains('is-dark') ? 'Switch to light theme' : 'Switch to dark theme';
  };
  sync();
  btn.addEventListener('click', () => {
    applyTheme(!document.body.classList.contains('is-dark'));
    try {
      localStorage.setItem(
        STORAGE_THEME_KEY,
        document.body.classList.contains('is-dark') ? 'dark' : 'light',
      );
    } catch { /* ignore */ }
    sync();
  });
}

// ── Tree ─────────────────────────────────────────────────────────────────────

function indexTree(nodes) {
  pathToNodeIndex = new Map();
  slugToCorpusPath = new Map();
  const walk = (list, parents) => {
    for (const n of list) {
      pathToNodeIndex.set(n.path, { node: n, parents: [...parents] });
      if (n.kind === 'file') {
        const slug = n.path.replace(/\.md$/i, '');
        slugToCorpusPath.set(`/${slug}/`, n.path);
        slugToCorpusPath.set(`/${slug}`, n.path);
      }
      if (n.children) walk(n.children, [...parents, n]);
    }
  };
  walk(nodes, []);
}

function loadCollapsedState() {
  try {
    const raw = localStorage.getItem(STORAGE_TREE_KEY);
    if (!raw) return new Set();
    const arr = JSON.parse(raw);
    return new Set(Array.isArray(arr) ? arr : []);
  } catch {
    return new Set();
  }
}

function saveCollapsedState(collapsed) {
  try {
    localStorage.setItem(STORAGE_TREE_KEY, JSON.stringify([...collapsed]));
  } catch {
    /* ignore quota */
  }
}

function renderTree() {
  const collapsed = loadCollapsedState();
  els.tree.removeAttribute('aria-busy');
  els.tree.innerHTML = '';
  const ul = document.createElement('ul');
  for (const root of treeData) {
    ul.appendChild(renderNode(root, collapsed, true));
  }
  els.tree.appendChild(ul);
  els.tree.addEventListener('click', onTreeClick);
}

function renderNode(node, collapsed, isRoot) {
  const li = document.createElement('li');
  if (isRoot) li.classList.add('root');

  const row = document.createElement('a');
  row.className = 'node';
  row.dataset.path = node.path;
  row.dataset.kind = node.kind;
  row.href = node.kind === 'file' ? `#/${node.path}` : '#';

  const isCollapsible = node.children && node.children.length > 0;
  const isCollapsed = isCollapsible && collapsed.has(node.path);

  const toggle = document.createElement('span');
  toggle.className = 'toggle' + (isCollapsible ? (isCollapsed ? '' : ' open') : ' leaf');
  toggle.textContent = isCollapsible ? '▶' : '';
  row.appendChild(toggle);

  const icon = document.createElement('span');
  icon.className = 'icon';
  icon.textContent = node.kind === 'file' ? '·' : '';
  row.appendChild(icon);

  const label = document.createElement('span');
  label.className = 'label';
  label.textContent = node.label;
  row.title = node.label;
  row.appendChild(label);

  // Track pastille for files in the active wizard track.
  if (node.kind === 'file' && isFileInActiveTrack(node.path)) {
    const pastille = document.createElement('span');
    const track = getActiveTrack();
    pastille.className = 'track-pastille';
    if (track) pastille.style.setProperty('--track-color', track.color || '#2563eb');
    const idx = stepIndexForFile(node.path);
    pastille.title = track ? `${track.label} · step ${idx + 1}` : 'In active track';
    row.appendChild(pastille);
  }

  li.appendChild(row);

  if (isCollapsible) {
    const childUl = document.createElement('ul');
    if (isCollapsed) childUl.classList.add('collapsed');
    for (const child of node.children) {
      childUl.appendChild(renderNode(child, collapsed, false));
    }
    li.appendChild(childUl);
  }

  return li;
}

function onTreeClick(e) {
  const row = e.target.closest('.node');
  if (!row) return;
  const path = row.dataset.path;
  const kind = row.dataset.kind;
  if (kind === 'file') {
    // Let the anchor navigate via hashchange; nothing else to do.
    return;
  }
  // Toggle folder
  e.preventDefault();
  const li = row.parentElement;
  const childUl = li.querySelector(':scope > ul');
  if (!childUl) return;
  const toggle = row.querySelector('.toggle');
  const collapsed = loadCollapsedState();
  const isNowCollapsed = !childUl.classList.contains('collapsed');
  childUl.classList.toggle('collapsed', isNowCollapsed);
  if (toggle) toggle.classList.toggle('open', !isNowCollapsed);
  if (isNowCollapsed) collapsed.add(path);
  else collapsed.delete(path);
  saveCollapsedState(collapsed);
}

function ensurePathExpanded(targetPath) {
  const entry = pathToNodeIndex.get(targetPath);
  if (!entry) return;
  const collapsed = loadCollapsedState();
  let changed = false;
  for (const parent of entry.parents) {
    if (collapsed.has(parent.path)) {
      collapsed.delete(parent.path);
      changed = true;
    }
    const row = els.tree.querySelector(`.node[data-path="${cssEscape(parent.path)}"]`);
    if (row) {
      const li = row.parentElement;
      const childUl = li.querySelector(':scope > ul');
      const toggle = row.querySelector('.toggle');
      if (childUl) childUl.classList.remove('collapsed');
      if (toggle) toggle.classList.add('open');
    }
  }
  if (changed) saveCollapsedState(collapsed);
}

function setActiveTreeNode(path) {
  for (const el of els.tree.querySelectorAll('.node.active')) {
    el.classList.remove('active');
  }
  if (!path) return;
  const row = els.tree.querySelector(`.node[data-path="${cssEscape(path)}"]`);
  if (row) {
    row.classList.add('active');
    row.scrollIntoView({ block: 'nearest', behavior: 'auto' });
  }
}

// ── Router ───────────────────────────────────────────────────────────────────

function handleRoute() {
  const hash = window.location.hash || '';
  // Static build: when there is no hash, derive the route from the URL pathname
  // so pre-rendered shells (/methodology/foo/) load the matching doc instead of
  // flashing the home view.
  if (!hash && isStatic) {
    const pathname = window.location.pathname || '/';
    if (pathname === '/map' || pathname === '/map/') { showMap(); return; }
    if (pathname === '/' || pathname === '/index.html') { showHome(); return; }
    const corpusPath = slugToCorpusPath.get(pathname);
    if (corpusPath) { loadDoc(corpusPath, ''); return; }
  }
  // Special route: cognitive map.
  if (hash === '#/map' || hash.startsWith('#/map?')) {
    showMap();
    return;
  }
  // Format: #/<corpus-path>(?<query>)?  where query may include h, track, step.
  const m = /^#\/([^?]+)(?:\?(.*))?$/.exec(hash);
  if (!m) {
    showHome();
    return;
  }
  const path = decodeURIComponent(m[1]);
  const params = parseQuery(m[2]);
  // Apply ?track=&step= silently so the wizard reflects the permalink state.
  if (params.track) {
    const stepIdx = params.step != null ? Number(params.step) : 0;
    setActiveTrack(params.track, Number.isFinite(stepIdx) ? stepIdx : 0);
    renderTree(); // re-render to show pastilles after silent activation
  }
  const headingId = params.h ? decodeURIComponent(params.h) : '';
  loadDoc(path, headingId);
}

function parseQuery(qs) {
  const out = {};
  if (!qs) return out;
  for (const part of qs.split('&')) {
    if (!part) continue;
    const eq = part.indexOf('=');
    const k = eq >= 0 ? part.slice(0, eq) : part;
    const v = eq >= 0 ? part.slice(eq + 1) : '';
    out[decodeURIComponent(k)] = v;
  }
  return out;
}

function showMap() {
  currentPath = null;
  setActiveTreeNode(null);
  els.breadcrumb.innerHTML = '';
  els.toc.innerHTML = '<p class="toc-empty">Cognitive map view.</p>';
  if (els.phaseRibbon) {
    els.phaseRibbon.hidden = true;
    els.phaseRibbon.innerHTML = '';
  }
  renderMap(els.doc);
  document.title = 'Workflow map · ezkey methodology';
  teardownScrollSpy();
}

function showHome() {
  currentPath = null;
  setActiveTreeNode(null);
  els.breadcrumb.innerHTML = '';
  els.toc.innerHTML = '<p class="toc-empty">Select a document to see its outline.</p>';
  if (els.phaseRibbon) {
    els.phaseRibbon.hidden = true;
    els.phaseRibbon.innerHTML = '';
  }
  // Restore home content if missing
  if (!els.home()) {
    if (initialHomeHtml) {
      els.doc.innerHTML = initialHomeHtml;
    } else {
      els.doc.innerHTML = '';
      const tpl = `
        <div id="home" class="home">
          <h1>ezkey · methodology</h1>
          <p class="lede">Pick a document on the left to start exploring.</p>
          <p class="home-shortcut">Or jump straight to <a href="#/methodology/README.md">methodology/README.md</a> · <a href="#/skills/README.md">skills/README.md</a>.</p>
        </div>`;
      els.doc.innerHTML = tpl;
    }
  }
  document.title = 'ezkey · methodology';
  teardownScrollSpy();
}

async function loadDoc(path, headingId) {
  currentPath = path;
  els.doc.innerHTML = '<p class="loading">Loading…</p>';
  els.toc.innerHTML = '<p class="loading">Loading…</p>';
  ensurePathExpanded(path);
  setActiveTreeNode(path);

  try {
    const data = await fetchDoc(path);
    renderDoc(data);
    if (headingId) {
      // Defer to allow layout.
      requestAnimationFrame(() => {
        const target = document.getElementById(headingId);
        if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      });
    } else {
      // Scroll content area to top.
      document.querySelector('.content').scrollTop = 0;
    }
  } catch (err) {
    els.doc.innerHTML = `<div class="error">Failed to load <code>${escapeHtml(
      path,
    )}</code>: ${escapeHtml(err.message || String(err))}</div>`;
    els.toc.innerHTML = '<p class="toc-empty">No outline available.</p>';
  }
}

function renderDoc(data) {
  els.doc.innerHTML = data.html;
  document.title = `${data.title} · ezkey methodology`;
  renderBreadcrumb(data.path, data.title, data.mtime);
  renderPhaseRibbon(data.phase);
  renderToc(data.toc);
  setupScrollSpy(data.toc);
  // Phase 2 decorations.
  decorateGlossaryIn(els.doc, glossaryData);
  renderMermaidIn(els.doc);
  // Phase 3: backlinks panel appended to the doc.
  renderBacklinks(data.backlinks);
  // Keep wizard pointer in sync if user navigated manually to a track step.
  syncStepFromFile(data.path);
}

function renderBacklinks(backlinks) {
  if (!backlinks || backlinks.length === 0) return;
  const aside = document.createElement('aside');
  aside.className = 'backlinks';
  const h = document.createElement('h3');
  h.textContent = 'Documents linking here';
  aside.appendChild(h);
  const ul = document.createElement('ul');
  for (const link of backlinks) {
    const li = document.createElement('li');
    const a = document.createElement('a');
    a.href = `#/${link.source}`;
    a.textContent = link.sourceTitle || link.source;
    li.appendChild(a);
    if (link.linkText && link.linkText !== link.sourceTitle) {
      const ctx = document.createElement('span');
      ctx.className = 'backlink-ctx';
      ctx.textContent = ` — “${link.linkText}”`;
      li.appendChild(ctx);
    }
    ul.appendChild(li);
  }
  aside.appendChild(ul);
  els.doc.appendChild(aside);
}

function renderPhaseRibbon(phaseId) {
  if (!els.phaseRibbon) return;
  if (!phaseId || !phasesById.has(phaseId)) {
    els.phaseRibbon.hidden = true;
    els.phaseRibbon.innerHTML = '';
    return;
  }
  const phase = phasesById.get(phaseId);
  const color = phase.color || '#2563eb';
  els.phaseRibbon.hidden = false;
  els.phaseRibbon.style.setProperty('--phase-color', color);
  els.phaseRibbon.style.setProperty('--phase-color-bg', hexToRgba(color, 0.08));
  els.phaseRibbon.style.setProperty('--phase-color-border', hexToRgba(color, 0.28));
  els.phaseRibbon.innerHTML = '';
  const dot = document.createElement('span');
  dot.className = 'phase-dot';
  const label = document.createElement('span');
  label.className = 'phase-label';
  label.textContent = phase.label;
  els.phaseRibbon.appendChild(dot);
  els.phaseRibbon.appendChild(label);
  if (phase.description) {
    const desc = document.createElement('span');
    desc.className = 'phase-desc';
    desc.textContent = phase.description;
    els.phaseRibbon.appendChild(desc);
  }
  els.phaseRibbon.title = 'Workflow phase';
}

function hexToRgba(hex, alpha) {
  const m = /^#?([a-f\d]{2})([a-f\d]{2})([a-f\d]{2})$/i.exec(hex);
  if (!m) return `rgba(37, 99, 235, ${alpha})`;
  return `rgba(${parseInt(m[1], 16)}, ${parseInt(m[2], 16)}, ${parseInt(m[3], 16)}, ${alpha})`;
}

function renderBreadcrumb(path, title, mtime) {
  const parts = path.split('/');
  const frag = document.createDocumentFragment();
  // Root link
  const rootLink = document.createElement('a');
  rootLink.href = '#/';
  rootLink.className = 'crumb';
  rootLink.textContent = 'Home';
  frag.appendChild(rootLink);
  for (let i = 0; i < parts.length; i++) {
    const sep = document.createElement('span');
    sep.className = 'sep';
    sep.textContent = '›';
    frag.appendChild(sep);
    const crumb = document.createElement('span');
    crumb.className = 'crumb';
    crumb.textContent = i === parts.length - 1 ? title : parts[i];
    frag.appendChild(crumb);
  }
  if (mtime) {
    const badge = document.createElement('span');
    badge.className = 'mtime-badge';
    badge.textContent = formatRelativeMtime(mtime);
    badge.title = `Last modified: ${new Date(mtime).toLocaleString()}`;
    frag.appendChild(badge);
  }
  els.breadcrumb.innerHTML = '';
  els.breadcrumb.appendChild(frag);
}

function formatRelativeMtime(iso) {
  const then = new Date(iso).getTime();
  if (!Number.isFinite(then)) return '';
  const diffMs = Date.now() - then;
  const day = 24 * 3600 * 1000;
  const days = Math.floor(diffMs / day);
  if (days < 1) return 'updated today';
  if (days === 1) return 'updated yesterday';
  if (days < 30) return `updated ${days}d ago`;
  const months = Math.floor(days / 30);
  if (months < 12) return `updated ${months}mo ago`;
  const years = Math.floor(days / 365);
  return `updated ${years}y ago`;
}

// ── TOC + scroll-spy ─────────────────────────────────────────────────────────

function renderToc(toc) {
  if (!toc || toc.length === 0) {
    els.toc.innerHTML = '<p class="toc-empty">No headings in this document.</p>';
    return;
  }
  const ul = document.createElement('ul');
  for (const item of toc) {
    const li = document.createElement('li');
    li.className = `lvl-${item.level}`;
    const a = document.createElement('a');
    a.href = `#/${currentPath}?h=${encodeURIComponent(item.id)}`;
    a.textContent = item.text;
    a.dataset.tocId = item.id;
    a.addEventListener('click', (e) => {
      // Manual scroll without re-fetching the document.
      e.preventDefault();
      const target = document.getElementById(item.id);
      if (target) target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      // Update URL without triggering hashchange handler.
      history.replaceState(null, '', `#/${currentPath}?h=${encodeURIComponent(item.id)}`);
    });
    li.appendChild(a);
    ul.appendChild(li);
  }
  els.toc.innerHTML = '';
  els.toc.appendChild(ul);
}

function teardownScrollSpy() {
  if (scrollSpyObserver) {
    scrollSpyObserver.disconnect();
    scrollSpyObserver = null;
  }
}

function setupScrollSpy(toc) {
  teardownScrollSpy();
  if (!toc || toc.length === 0) return;
  const headingIds = new Set(toc.map((t) => t.id));
  const visible = new Map();
  scrollSpyObserver = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          visible.set(entry.target.id, entry.boundingClientRect.top);
        } else {
          visible.delete(entry.target.id);
        }
      }
      // Pick the topmost visible heading.
      let activeId = null;
      let bestTop = Infinity;
      for (const [id, top] of visible) {
        if (top < bestTop) {
          bestTop = top;
          activeId = id;
        }
      }
      updateTocActive(activeId);
    },
    {
      root: document.querySelector('.content'),
      rootMargin: '0px 0px -75% 0px',
      threshold: [0, 1],
    },
  );
  for (const id of headingIds) {
    const el = document.getElementById(id);
    if (el) scrollSpyObserver.observe(el);
  }
}

function updateTocActive(id) {
  for (const a of els.toc.querySelectorAll('a.active')) a.classList.remove('active');
  if (!id) return;
  const a = els.toc.querySelector(`a[data-toc-id="${cssEscape(id)}"]`);
  if (a) a.classList.add('active');
}

// ── Utilities ────────────────────────────────────────────────────────────────

function escapeHtml(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function cssEscape(s) {
  if (typeof CSS !== 'undefined' && CSS.escape) return CSS.escape(s);
  return String(s).replace(/[^a-zA-Z0-9_-]/g, (c) => `\\${c}`);
}
