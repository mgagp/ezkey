# ezkey.org public site (static)

This folder is the **source of truth** for the static public landing page served at **ezkey.org** (and related Cloudflare deployments).

## Contents

| Path | Purpose |
|------|---------|
| `index.html` | English (`lang="en"`): hero, feature blocks, mini blog. Inline CSS and **inline SVG logo** (no separate image file). |
| `fr/index.html` | French (`lang="fr"`): same layout and logo, translated copy. Served at **`/fr/`** when deployed. |

**Locales:** Default URL `/` is English; `/fr/` is French. Small in-page links + `hreflang` in `<head>` point search engines at both versions.

## Build

No build step. Deploy as **static files** (e.g. Cloudflare Pages with publish directory = this folder).

## Documentation

- Agent-specific notes (dictation / Ezkey vs “easy”, Cloudflare vs repo naming): **[AGENTS.md](AGENTS.md)**
- Cloudflare workflow, preview vs production: **[../../docs/cloudflare/ezkey-org-site.md](../../docs/cloudflare/ezkey-org-site.md)**
