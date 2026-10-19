# Community deploy ledger (ezkey.online)

**Source of truth** for what is live on the **community** surface (deploy SHA / timing).
Public product hostnames (served vs not served, DNS, expected `/` codes): see
[`../community-host.md`](../community-host.md) § *Product hostnames (ezkey.online)*.

- **Admin UI** — Cloudflare Pages (not a VM container)
- **APIs / Demo ACME** — Lightsail Docker host behind Caddy

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

## Host OS

Host OS hygiene lane: [`product-docs/global/hygiene/host-os/README.md`](../../../product-docs/global/hygiene/host-os/README.md)
(#909). Update these fields after each H1/H2/H3 campaign (not on app-only deploys). Signal:
`./scripts/lightsail/os-patch-check.sh --target community` (rootbeer-local only).

| Field | Value |
|-------|-------|
| **Blueprint / line** | Amazon Linux 2023 (`amazon_linux_2023`) |
| **releasever (locked)** | _(record from first `os-patch-check` / H1)_ |
| **Kernel (`uname -r`)** | _(record from first check)_ |
| **Last H1 (security patch)** | never |
| **Last H2 (AL2023 minor + reboot)** | never |
| **Last H3 (major / new instance)** | never |
| **Last pre-intervention snapshot** | none (0 snapshots verified 2026-10-19; automatic snapshots off) |
| **Host OS note** | Lane established 2026-10-19 (#909); no H1/H2 executed yet. |

## Optional tag

After updating this table, operators may move lightweight tag `deploy/community` to the same
full SHA. Tags are convenience only; this file remains SOOT.
