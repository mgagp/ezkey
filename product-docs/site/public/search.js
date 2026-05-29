/*
 * Ezkey Method · Local Explorer — Ctrl+K search palette (Phase 3).
 *
 * Loads MiniSearch on first invocation, indexes /api/search-index, and shows
 * a fixed overlay with arrow-key navigation. Hit Enter to open the document
 * (and jump to the matched heading anchor when applicable).
 */

let miniSearchPromise = null;
let indexPromise = null;
let palette = null;
let overlay = null;
let inputEl = null;
let resultsEl = null;
let currentResults = [];
let activeIdx = 0;
let isOpen = false;

async function loadMiniSearch() {
  if (!miniSearchPromise) {
    miniSearchPromise = import('https://cdn.jsdelivr.net/npm/minisearch@7.1.0/+esm').then(
      (m) => m.default || m.MiniSearch || m,
    );
  }
  return miniSearchPromise;
}

async function loadIndex() {
  if (!indexPromise) {
    indexPromise = (async () => {
      const [MiniSearch, data] = await Promise.all([
        loadMiniSearch(),
        import('./apiClient.js').then((m) => m.fetchSearchIndex()),
      ]);
      // Flatten: one searchable entry per doc + one per heading.
      const docs = [];
      let id = 1;
      for (const d of data.docs || []) {
        docs.push({
          id: id++,
          kind: 'doc',
          path: d.path,
          title: d.title,
          heading: '',
          anchor: '',
          text: d.text || '',
        });
        for (const h of d.headings || []) {
          if (h.level < 1 || h.level > 4) continue;
          docs.push({
            id: id++,
            kind: 'heading',
            path: d.path,
            title: d.title,
            heading: h.text,
            anchor: slugify(h.text),
            text: '',
          });
        }
      }
      const ms = new MiniSearch({
        fields: ['title', 'heading', 'text'],
        storeFields: ['kind', 'path', 'title', 'heading', 'anchor'],
        searchOptions: {
          boost: { title: 4, heading: 2, text: 1 },
          prefix: true,
          fuzzy: 0.2,
        },
      });
      ms.addAll(docs);
      return ms;
    })();
  }
  return indexPromise;
}

// Mirror of marked-gfm-heading-id slug behavior (good-enough subset).
function slugify(text) {
  return text
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9\s-]/g, '')
    .replace(/\s+/g, '-')
    .replace(/-+/g, '-');
}

function buildPalette() {
  overlay = document.createElement('div');
  overlay.className = 'search-overlay';
  overlay.addEventListener('click', (e) => {
    if (e.target === overlay) close();
  });

  palette = document.createElement('div');
  palette.className = 'search-palette';
  palette.setAttribute('role', 'dialog');
  palette.setAttribute('aria-label', 'Search the methodology corpus');

  inputEl = document.createElement('input');
  inputEl.type = 'search';
  inputEl.placeholder = 'Search titles, headings, content…   (↑↓ navigate · Enter open · Esc close)';
  inputEl.className = 'search-input';
  inputEl.autocomplete = 'off';
  inputEl.spellcheck = false;
  inputEl.addEventListener('input', onInput);
  inputEl.addEventListener('keydown', onKey);

  resultsEl = document.createElement('div');
  resultsEl.className = 'search-results';

  palette.appendChild(inputEl);
  palette.appendChild(resultsEl);
  overlay.appendChild(palette);
  document.body.appendChild(overlay);
}

function onInput() {
  const q = inputEl.value.trim();
  if (!q) {
    currentResults = [];
    renderResults();
    return;
  }
  loadIndex().then((ms) => {
    if (!isOpen) return;
    const hits = ms.search(q, { combineWith: 'AND' }).slice(0, 30);
    currentResults = hits;
    activeIdx = 0;
    renderResults();
  });
}

function renderResults() {
  resultsEl.innerHTML = '';
  if (currentResults.length === 0) {
    const empty = document.createElement('div');
    empty.className = 'search-empty';
    empty.textContent = inputEl.value.trim()
      ? 'No results.'
      : 'Type to search the corpus.';
    resultsEl.appendChild(empty);
    return;
  }
  currentResults.forEach((hit, i) => {
    const row = document.createElement('a');
    row.className = 'search-result' + (i === activeIdx ? ' is-active' : '');
    row.href = buildHrefForHit(hit);
    row.dataset.idx = String(i);
    row.addEventListener('click', (e) => {
      e.preventDefault();
      openHit(hit);
    });

    const kind = document.createElement('span');
    kind.className = 'search-kind ' + (hit.kind === 'heading' ? 'kind-heading' : 'kind-doc');
    kind.textContent = hit.kind === 'heading' ? '§' : '¶';

    const main = document.createElement('div');
    main.className = 'search-main';
    const primary = document.createElement('div');
    primary.className = 'search-primary';
    primary.textContent = hit.kind === 'heading' ? hit.heading : hit.title;
    const secondary = document.createElement('div');
    secondary.className = 'search-secondary';
    secondary.textContent = hit.kind === 'heading' ? hit.title + ' · ' + hit.path : hit.path;
    main.appendChild(primary);
    main.appendChild(secondary);

    row.appendChild(kind);
    row.appendChild(main);
    resultsEl.appendChild(row);
  });
}

function buildHrefForHit(hit) {
  return hit.anchor
    ? `#/${hit.path}?h=${encodeURIComponent(hit.anchor)}`
    : `#/${hit.path}`;
}

function openHit(hit) {
  window.location.hash = buildHrefForHit(hit);
  close();
}

function onKey(e) {
  if (e.key === 'Escape') {
    e.preventDefault();
    close();
    return;
  }
  if (e.key === 'ArrowDown') {
    e.preventDefault();
    if (currentResults.length === 0) return;
    activeIdx = (activeIdx + 1) % currentResults.length;
    renderResults();
    scrollActiveIntoView();
    return;
  }
  if (e.key === 'ArrowUp') {
    e.preventDefault();
    if (currentResults.length === 0) return;
    activeIdx = (activeIdx - 1 + currentResults.length) % currentResults.length;
    renderResults();
    scrollActiveIntoView();
    return;
  }
  if (e.key === 'Enter') {
    e.preventDefault();
    if (currentResults.length === 0) return;
    openHit(currentResults[activeIdx]);
  }
}

function scrollActiveIntoView() {
  const active = resultsEl.querySelector('.search-result.is-active');
  if (active && active.scrollIntoView) active.scrollIntoView({ block: 'nearest' });
}

export function openSearch() {
  if (!overlay) buildPalette();
  isOpen = true;
  overlay.classList.add('is-open');
  inputEl.value = '';
  currentResults = [];
  renderResults();
  // Warm-load the index in the background.
  loadIndex().catch((err) => console.error('[search] index load failed', err));
  setTimeout(() => inputEl.focus(), 0);
}

export function close() {
  isOpen = false;
  if (overlay) overlay.classList.remove('is-open');
}

export function setupSearchShortcut() {
  document.addEventListener('keydown', (e) => {
    // Ctrl+K or Cmd+K
    if ((e.ctrlKey || e.metaKey) && (e.key === 'k' || e.key === 'K')) {
      e.preventDefault();
      isOpen ? close() : openSearch();
    }
  });
}
