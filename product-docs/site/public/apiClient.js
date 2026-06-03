/*
 * Ezkey Method · Explorer — API client wrapper
 *
 * Resolves API calls to either the live Express server (local dev) or to
 * pre-built JSON snapshots (Cloudflare Pages static build).
 *
 * The static build injects `window.__EZKEY_STATIC__ = true` into each shell
 * HTML page. When that flag is absent, we hit the dynamic `/api/...` routes
 * served by server.js.
 */

const STATIC = typeof window !== 'undefined' && window.__EZKEY_STATIC__ === true;

async function getJson(url) {
  const res = await fetch(url);
  if (!res.ok) throw new Error(`${url} HTTP ${res.status}`);
  return res.json();
}

export function fetchTree() {
  return getJson(STATIC ? '/api/tree.json' : '/api/tree');
}

export function fetchPhases() {
  return getJson(STATIC ? '/api/phases.json' : '/api/phases');
}

export function fetchTracks() {
  return getJson(STATIC ? '/api/tracks.json' : '/api/tracks');
}

export function fetchGlossary() {
  return getJson(STATIC ? '/api/glossary.json' : '/api/glossary');
}

export function fetchVersion() {
  return getJson(STATIC ? '/api/version.json' : '/api/version');
}

export function fetchSearchIndex() {
  return getJson(STATIC ? '/api/search-index.json' : '/api/search-index');
}

export function fetchDoc(urlPath) {
  if (STATIC) {
    // Strip trailing .md so the static snapshot path is stable for both
    // `glossary` (no extension) and `methodology/foo.md` corpus entries.
    const clean = urlPath.replace(/\.md$/i, '');
    return getJson(`/api/doc/${clean}.json`);
  }
  return getJson(`/api/doc?path=${encodeURIComponent(urlPath)}`);
}

export const isStatic = STATIC;
