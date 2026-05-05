# ezkey.org public site (static)

This folder is the **source of truth** for the static public site served at **ezkey.org** (and related Cloudflare deployments).

## Contents

| Path | Purpose |
|------|---------|
| `index.html` | English (`lang="en"`): compact landing — hero, pillars, evaluation CTAs (run locally, docs, trust), secondary links to updates/articles. Inline CSS. |
| `fr/index.html` | French (`lang="fr"`): same structure, translated copy. Served at **`/fr/`** when deployed. |
| `source-and-evaluation.html` / `fr/source-and-evaluation.html` | Explains private repository until public opening; targets for **Docs** / **Run locally** in nav (anchors `#documentation`, `#run-locally`). |
| `trust.html` / `fr/trust.html` | Short trust / security diligence (no off-site repo links while private). |
| `updates.html` / `fr/updates.html` | Product updates: reverse-chronological, dated entries (optional type pill per entry). |
| `articles.html` / `fr/articles.html` | Guides & walkthroughs lane + index of essays (cards link to standalone HTML pages). |
| `changelog.html` / `fr/changelog.html` | Placeholder for future technical release notes; linked from Updates; omitted from primary nav until filled. |
| `*.html` under root / `fr/` | Article bodies and other standalone pages (each includes Open Graph + Twitter Card meta for sharing). |

| File | Purpose |
|------|---------|
| `sitemap.xml` | Sitemap; URL listed in `robots.txt`. |
| `robots.txt` | `Allow: /` and `Sitemap:` pointer. |
| `updates.rss` / `fr/updates.rss` | RSS feeds for **Updates**; keep in sync when adding entries (see **AGENTS.md**). |

**Locales:** Default URL `/` is English; `/fr/` is French. Hub pages expose `hreflang` alternates; in-page language switches link between paired locales.

## Build

No build step. Deploy as **static files** (e.g. Cloudflare Pages with publish directory = this folder).

## Documentation

- Agent-specific notes (IA, bilingual paths, draft workflow): **[AGENTS.md](AGENTS.md)**
- Cloudflare workflow, preview vs production: **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)**
