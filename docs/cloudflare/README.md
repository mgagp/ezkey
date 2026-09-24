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
| Auth API EXP1 / community schema validation (package + upload; EXP1 Block exception; community None) | [auth-api-schema-validation.md](auth-api-schema-validation.md) |
| Integration API EXP1 / community schema validation (package + upload; default None) | [integration-api-schema-validation.md](integration-api-schema-validation.md) |

## Repository root `.env`

Wrangler deploy scripts source a gitignored **`.env`** at the **repository root** when present. Copy **[`.env.example`](../../.env.example)** to `.env` and set at least `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID`. Schema upload uses a **separate** `CLOUDFLARE_API_SHIELD_TOKEN` (and optional `CLOUDFLARE_ZONE_ID`) — do not reuse the Pages token. For Admin UI Cloudflare builds with `--build`, the same file must set **`VITE_API_BASE_URL`** explicitly (deploy scripts exit ≠ 0 if missing — no product default hostname). See [admin-ui-pages.md](admin-ui-pages.md).

## Automation (`scripts/cloudflare/`)

| Script | Purpose |
|--------|---------|
| [deploy-ezkey-org-preview.sh](../../scripts/cloudflare/deploy-ezkey-org-preview.sh) | Deploy `sites/ezkey-org/` to Pages as a **preview** (requires `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` on your machine). |
| [deploy-ezkey-org-production.sh](../../scripts/cloudflare/deploy-ezkey-org-production.sh) | Deploy `sites/ezkey-org/` to the Pages **production** branch (default `main` via `CLOUDFLARE_PAGES_PRODUCTION_BRANCH`); same credentials as preview. Serves the custom domain (e.g. `ezkey.org`) when the project is configured for it. |
| [deploy-admin-ui-preview.sh](../../scripts/cloudflare/deploy-admin-ui-preview.sh) | Deploy `ezkey-admin-ui/dist` to Pages as a **preview** after `npm run build:cloudflare`. Optional `--build` runs the build first and **requires** `VITE_API_BASE_URL` from `.env` (no silent EXP1 default). |
| [deploy-admin-ui-production.sh](../../scripts/cloudflare/deploy-admin-ui-production.sh) | Deploy `ezkey-admin-ui/dist` to the Pages **production** branch (default `main`). Same `--build` and `.env` behavior. Use after custom domain (e.g. `exp1-admin-ui.ezkey.org` or `admin-ui.ezkey.online`) is configured. |
| [cleanup-pages-deployments.sh](../../scripts/cloudflare/cleanup-pages-deployments.sh) | **Age-aware** cleanup of Pages **preview** deployments (default dry-run, >24h, both `ezkey-org` and `methodology-ezkey-org`). Use `--profile prudent` for 7d, `--apply` to delete, `--env production` for production history (with safeguards). |
| [cleanup-ezkey-org-previews.sh](../../scripts/cloudflare/cleanup-ezkey-org-previews.sh) | Legacy: list/delete **all** preview deployments for `ezkey-org` (no age filter). Prefer `cleanup-pages-deployments.sh`. |
| [cleanup-methodology-previews.sh](../../scripts/cloudflare/cleanup-methodology-previews.sh) | Legacy: same as above for `methodology-ezkey-org`. Prefer `cleanup-pages-deployments.sh`. |
| [upload-auth-api-schema-exp1.sh](../../scripts/cloudflare/upload-auth-api-schema-exp1.sh) | List, upload, or delete Auth API schemas on `ezkey.org` (`--list` / `--upload` / `--delete <id>`). Requires `CLOUDFLARE_API_SHIELD_TOKEN`. Does not use Wrangler or the Pages token. |
| [upload-auth-api-schema-community.sh](../../scripts/cloudflare/upload-auth-api-schema-community.sh) | Thin wrapper: zone `ezkey.online`, schema `ezkey-auth-api-community`, community artifact. Same upload script underneath. Mitigation stays **None**. |
| [upload-integration-api-schema-exp1.sh](../../scripts/cloudflare/upload-integration-api-schema-exp1.sh) | List, upload, or delete Integration API schemas on `ezkey.org` (`--list` / `--upload` / `--delete <id>`). Same shield token. Refuses to delete the Auth schema. |
| [upload-integration-api-schema-community.sh](../../scripts/cloudflare/upload-integration-api-schema-community.sh) | Thin wrapper: zone `ezkey.online`, schema `ezkey-integration-api-community`, community artifact. Mitigation stays **None**. |

Add more scripts here only when a **repeated** automation need appears (e.g. standardized production promotion, DNS verification).

Schema packaging (local, no Cloudflare call):
[`scripts/package-auth-api-cloudflare-schema.sh`](../../scripts/package-auth-api-cloudflare-schema.sh)
and
[`scripts/package-integration-api-cloudflare-schema.sh`](../../scripts/package-integration-api-cloudflare-schema.sh)
(default EXP1; `--community` or `--server` for `ezkey.online`) — see
[auth-api-schema-validation.md](auth-api-schema-validation.md) and
[integration-api-schema-validation.md](integration-api-schema-validation.md).

## Site source

The public static site for ezkey.org lives at **[sites/ezkey-org/](../../sites/ezkey-org/)**. Details: [ezkey-org-site.md](ezkey-org-site.md) (includes why preview URLs may still show **`ezkey-teaser.pages.dev`** — legacy hostname, same project as **`ezkey-org`**). Agent notes: **[sites/ezkey-org/AGENTS.md](../../sites/ezkey-org/AGENTS.md)**.
