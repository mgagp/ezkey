# Community deploy ledger (ezkey.online)

**Source of truth** for what is live on the **community** surface:

- **Admin UI** — Cloudflare Pages (`admin-ui.ezkey.online`), not a VM container
- **APIs / Demo ACME** — Lightsail Docker host behind `*.ezkey.online`

Normative rules: [`../VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../VERSIONING_AND_DEPLOY_TRACEABILITY.md).

History of this file (git log) is the deploy history. Update **only after** a successful
publish — see that doc § *Update procedure*.

**Traceability lock:** record the **full git SHA**. Docker `*:latest`, npm `0.0.0`, and
actuator/health with no version string are **not** deploy identity.

## Current

| Field | Value |
|-------|-------|
| **Surface** | community (ezkey.online) |
| **Full git SHA** | _not yet recorded_ |
| **Short SHA** | _—_ |
| **Deployed at (UTC)** | _—_ |
| **Operator** | _—_ |
| **Note** | Stub. Pre-convention live state: Admin UI on Pages (SHA unknown); VM images `*:latest` (~2026-09-22 inventory) — `:latest` alone is not traceability. First real row lands after the next successful community publish (Edgar / ops): same monorepo SHA for Pages UI build + backend images when possible. |

## Optional tag

After updating this table, operators may move lightweight tag `deploy/community` to the same
full SHA. Tags are convenience only; this file remains SOOT.
