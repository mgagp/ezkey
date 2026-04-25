# Cloudflare (Ezkey)

This directory holds **workflow notes, scope, and time-horizon planning** for using Cloudflare with Ezkey (DNS, static hosting, edge headers, and future controls). It complements product and stack docs elsewhere in `docs/`.

## What belongs here

- Operator and assistant-oriented runbooks for **this repository’s** Cloudflare-related work.
- Explicit separation of **current reality** vs **planned** vs **exploratory** items (see [ezkey-org-site.md](ezkey-org-site.md)).

## Canonical cross-links

| Topic | Document |
|-------|----------|
| Proxies, client IP (`CF-Connecting-IP`), rate limiting context | [OPERATIONAL.md](../OPERATIONAL.md) |
| Admin UI CSP, split UI/API deployment, mirroring headers at the edge | [admin-ui-security.md](../admin-ui-security.md) |
| Admin UI on Cloudflare Pages (build, CORS on API, Wrangler, edge headers) | [admin-ui-pages.md](admin-ui-pages.md) |

## Repository root `.env`

Wrangler deploy scripts source a gitignored **`.env`** at the **repository root** when present. Copy **[`.env.example`](../../.env.example)** to `.env` and set at least `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`. For Admin UI Cloudflare builds, the same file may set **`VITE_API_BASE_URL`** (defaults to `https://exp1-admin-api.ezkey.org` in `deploy-admin-ui-preview.sh` if omitted). See [admin-ui-pages.md](admin-ui-pages.md).

## Automation (`scripts/cloudflare/`)

| Script | Purpose |
|--------|---------|
| [deploy-ezkey-org-preview.sh](../../scripts/cloudflare/deploy-ezkey-org-preview.sh) | Deploy `sites/ezkey-org/` to Pages as a **preview** (requires `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` on your machine). |
| [deploy-ezkey-org-production.sh](../../scripts/cloudflare/deploy-ezkey-org-production.sh) | Deploy `sites/ezkey-org/` to the Pages **production** branch (default `main` via `CLOUDFLARE_PAGES_PRODUCTION_BRANCH`); same credentials as preview. Serves the custom domain (e.g. `ezkey.org`) when the project is configured for it. |
| [deploy-admin-ui-preview.sh](../../scripts/cloudflare/deploy-admin-ui-preview.sh) | Deploy `ezkey-admin-ui/dist` to Pages as a **preview** after `npm run build:cloudflare`. Optional `--build` runs the build first using `VITE_API_BASE_URL` from `.env` or the exp1 default. |
| [deploy-admin-ui-production.sh](../../scripts/cloudflare/deploy-admin-ui-production.sh) | Deploy `ezkey-admin-ui/dist` to the Pages **production** branch (default `main`). Same `--build` and `.env` behavior. Use after custom domain (e.g. `exp1-admin-ui.ezkey.org`) is configured. |

Add more scripts here only when a **repeated** automation need appears (e.g. standardized production promotion, DNS verification).

## Site source

The public static site for ezkey.org lives at **[sites/ezkey-org/](../../sites/ezkey-org/)**. Details: [ezkey-org-site.md](ezkey-org-site.md) (includes why preview URLs may still show **`ezkey-teaser.pages.dev`** — legacy hostname, same project as **`ezkey-org`**). Agent notes: **[sites/ezkey-org/AGENTS.md](../../sites/ezkey-org/AGENTS.md)**.
