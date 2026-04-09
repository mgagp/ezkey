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

## Automation (`scripts/cloudflare/`)

| Script | Purpose |
|--------|---------|
| [deploy-ezkey-org-preview.sh](../../scripts/cloudflare/deploy-ezkey-org-preview.sh) | Deploy `sites/ezkey-org/` to Pages as a **preview** (requires `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` on your machine). |

Add more scripts here only when a **repeated** automation need appears (e.g. standardized production promotion, DNS verification).

## Site source

The public static site for ezkey.org lives at **[sites/ezkey-org/](../../sites/ezkey-org/)**. Details: [ezkey-org-site.md](ezkey-org-site.md). Agent notes (dictation pitfalls, Cloudflare project naming vs repo): **[sites/ezkey-org/AGENTS.md](../../sites/ezkey-org/AGENTS.md)**.
