# ezkey.org public site (static)

This folder is the **source of truth** for the static public site served at **ezkey.org** (and related Cloudflare deployments).

## Contents

| Path | Purpose |
|------|---------|
| `index.html` | English (`lang="en"`): compact landing — hero, pillars, evaluation CTAs (run locally, docs, trust), secondary links to monthly digests/articles. Inline CSS. |
| `fr/index.html` | French (`lang="fr"`): same structure, translated copy. Served at **`/fr/`** when deployed. |
| `source-and-evaluation.html` / `fr/source-and-evaluation.html` | Public MIT repository, alpha status, docs and **Run locally** (clean-start); nav anchors `#documentation`, `#run-locally`. |
| `api-docs.html` | English-first public API portal landing page. |
| `admin-api-reference.html` | English-first Admin API public reference page powered by ReDoc CE. |
| `auth-api-reference.html` | English-first Auth API public reference page powered by ReDoc CE. |
| `integration-api-reference.html` | English-first Integration API public reference page powered by ReDoc CE. |
| `fr/api-docs.html` | French API portal landing page. |
| `fr/admin-api-reference.html` | French Admin API public reference page powered by ReDoc CE. |
| `fr/auth-api-reference.html` | French Auth API public reference page powered by ReDoc CE. |
| `fr/integration-api-reference.html` | French Integration API public reference page powered by ReDoc CE. |
| `trust.html` / `fr/trust.html` | Short trust / security diligence (links to public GitHub + on-site evaluation). |
| `monthly-digest.html` / `fr/monthly-digest.html` | Monthly digests: reverse-chronological index (Git-distilled months link to detail pages; legacy entries index-only). |
| `articles.html` / `fr/articles.html` | Guides & walkthroughs lane + index of essays (cards link to standalone HTML pages). |
| `changelog.html` / `fr/changelog.html` | Placeholder for future technical release notes; linked from Monthly digests; omitted from primary nav until filled. |
| `*.html` under root / `fr/` | Article bodies and other standalone pages (each includes Open Graph + Twitter Card meta for sharing). |

| File | Purpose |
|------|---------|
| `sitemap.xml` | Sitemap; URL listed in `robots.txt`. |
| `robots.txt` | `Allow: /` and `Sitemap:` pointer. |
| `monthly-digest.rss` / `fr/monthly-digest.rss` | RSS feeds for **Monthly digests**; keep in sync when adding entries (see **AGENTS.md**). |
| `api-specs/` | Static OpenAPI JSON assets consumed by the public API portal pages. |

**Locales:** Default URL `/` is English; `/fr/` is French. Hub pages expose `hreflang` alternates; in-page language switches link between paired locales.

## Build

No build step. Deploy as **static files** (e.g. Cloudflare Pages with publish directory = this folder).

## Documentation

- Agent-specific notes (IA, bilingual paths, draft workflow): **[AGENTS.md](AGENTS.md)**
- Cloudflare workflow, preview vs production: **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)**
