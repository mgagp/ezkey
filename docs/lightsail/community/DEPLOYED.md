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
| **Full git SHA** | `36bac04e833ec359e4453cf17003e207653d454c` |
| **Short SHA** | `36bac04e` |
| **Deployed at (UTC)** | 2026-10-20T14:31:00Z |
| **Operator** | Edgar |
| **Note** | Full backend deploy 2026-10-20 ~14:31–14:33 UTC (10:31–10:33 EDT). Flyway schema version 25; no new migration applied. Image IDs: admin `547e07a91e8f`, auth `e028eab2ab6c`, integration `4203f0871a87`, acme `728769c0560f`. Pre-deploy rollback tag: `rollback-pre-36bac04e`. Host: `<static-ip ezkey-online-community>`. |

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
