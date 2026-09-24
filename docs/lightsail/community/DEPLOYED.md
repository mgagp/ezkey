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
| **Full git SHA** | `435f9cf0f32580bfa3e55f8906d17e5dfc45baab` |
| **Short SHA** | `435f9cf` |
| **Deployed at (UTC)** | 2026-09-24T13:07:05Z |
| **Operator** | Edgar |
| **Note** | Pages-only republish for Public alpha SHA chrome (#619); backend VM images unchanged (`*:latest`); fail-closed env overrides already live (#620 posture). GUIDED_TOUR/admin-ui/QR already set. |

## Optional tag

After updating this table, operators may move lightweight tag `deploy/community` to the same
full SHA. Tags are convenience only; this file remains SOOT.
