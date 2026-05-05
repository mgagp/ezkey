# ezkey.org public site (static)

This folder is the **source of truth** for the static public site served at **ezkey.org** (and related Cloudflare deployments).

## Contents

| Path | Purpose |
|------|---------|
| `index.html` | English (`lang="en"`): compact landing — hero, pillars, nav, CTAs to content hubs. Inline CSS. |
| `fr/index.html` | French (`lang="fr"`): same structure, translated copy. Served at **`/fr/`** when deployed. |
| `updates.html` / `fr/updates.html` | Product updates: reverse-chronological, dated entries (optional type pill per entry). |
| `articles.html` / `fr/articles.html` | Index of long-form articles (cards link to standalone HTML pages). |
| `changelog.html` / `fr/changelog.html` | Placeholder for future technical release notes; see Updates for build status today. |
| `*.html` under root / `fr/` | Article bodies and other standalone pages. |

**Locales:** Default URL `/` is English; `/fr/` is French. Hub pages expose `hreflang` alternates; in-page language switches link between paired locales.

## Build

No build step. Deploy as **static files** (e.g. Cloudflare Pages with publish directory = this folder).

## Documentation

- Agent-specific notes (IA, bilingual paths, draft workflow): **[AGENTS.md](AGENTS.md)**
- Cloudflare workflow, preview vs production: **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)**
