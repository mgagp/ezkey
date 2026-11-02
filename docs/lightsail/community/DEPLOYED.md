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
| **Full git SHA** | `10264a0b778bc6c31c4a0469b2f22347ce126e2c` |
| **Short SHA** | `10264a0b` |
| **Deployed at (UTC)** | 2026-11-01T20:11:00Z |
| **Operator** | Edgar |
| **Note** | Backend upgrade 2026-11-01 ~15:11–15:14 ET / America/Toronto (20:11–20:14 UTC) to main `10264a0b` (#1114). Sequential recreate (~35 s each): admin-api, auth-api, integration-api, demo-app-acme; postgres, caddy, and collector untouched. Flyway schema version 25; no migration applied. Keyset unchanged. Image IDs: admin-api `ad2fcfde4ca1`, auth-api `824fb8ba6d80`, integration-api `5bf27c154c19`, demo-app-acme `8790d8556b3c`, migration `57dddf6727ea` (OCI revision `10264a0b`). Rollback tags: `:rollback-pre-10264a0b` (previous revision `92717a40a`, #961, 2026-10-25). Admin UI (Cloudflare Pages project `admin-ui-ezkey-online`, production branch `main`, API base `https://admin-api.ezkey.online`) published from `10264a0b` ~15:22 ET. Host: `<static-ip ezkey-online-community>`. |

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
