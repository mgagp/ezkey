# Community deploy ledger (ezkey.online)

**Source of truth** for what is live on the **community** surface (deploy SHA / timing).
Public product hostnames (served vs not served, DNS, expected `/` codes): see
[`../community-host.md`](../community-host.md) § *Product hostnames (ezkey.online)*.

- **Admin UI** — Cloudflare Pages (not a VM container)
- **APIs / Demo Login** — Lightsail Docker host behind Caddy

Normative rules: [`../VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../../VERSIONING_AND_DEPLOY_TRACEABILITY.md).

History of this file (git log) is the deploy history. Update **only after** a successful
publish — see that doc § *Update procedure*.

**Traceability lock:** record the **full git SHA**. Docker `*:latest`, npm `0.0.0`, and
actuator/health with no version string are **not** deploy identity.

**Window rules** (compose flags, `verify-grants.sh` stdin, `--apis-only`, Rootbeer `.env`,
tenant integrations, Admin UI publish order, Admin UI revocation, manual DNS):
[`../community-host.md`](../community-host.md) § *Window rules*.

## Current

| Field | Value |
|-------|-------|
| **Surface** | community (ezkey.online) |
| **Full git SHA** | `91dc5fe3f43315180b0fff337fa01326a8047db0` |
| **Short SHA** | `91dc5fe3` |
| **Deployed at (UTC)** | 2026-11-23T00:06:00Z |
| **Operator** | supervised window |
| **Note** | Supervised window 2026-11-22 15:26–19:06 America/Toronto (2026-11-22T20:26Z–2026-11-23T00:06Z), from `c6d89274` (Flyway V29) to `91dc5fe3` (Flyway V31). Keyset unchanged (v1). DB role passwords rotated (#1017) with `scripts/db/rotate-role-passwords.sh` (merged in #1447). Demo Login is deployed and replaces the Acme demo app; the Demo Login integration is in its own tenant. Acme demo app, its volumes, DNS record, and integrations are retired; keys were revoked in the Admin UI (MFA), not by SQL. Admin UI (Cloudflare Pages project `admin-ui-ezkey-online`, production branch `main`, API base `https://admin-api.ezkey.online`) published from `91dc5fe3`. ezkey.org published from `91dc5fe3`. Auth API Shield schema refreshed; mitigation remains Block. `EZKEY_ADMIN_CORS_ALLOWED_ORIGINS` is the Admin UI origin only (`https://admin-ui.ezkey.online`); legacy `EZKEY_EVALUATOR_*` keys removed. Compose on the VM remained three files: `docker-compose.yml` + `docker-compose.javamelody.yml` + `docker-compose.connector-smtp.yml` (see § PostgreSQL for the transitional fourth file since 2026-11-24). Host OS was Amazon Linux 2023.12.20261102 during that window (no OS window; see § Host OS for the later H1). Stack SHA unchanged by the 2026-11-23 H1 and the 2026-11-24 PostgreSQL 19 window. Host: `<static-ip ezkey-online-community>`. |

## Email delivery (opt-in SMTP connector)

Enabled 2026-11-15 ~19:15 ET / America/Toronto (~2026-11-16T00:15Z), after the `c6d89274`
API upgrade (git history of this file). Opt-in
([ADR-0018](../../../product-docs/global/architecture-decisions.md#adr-0018--connector-spi-for-external-services)):
the on-screen QR stays the path when this connector is absent.
Runbook: [`../community-host.md`](../community-host.md) § *Email delivery connector (Amazon SES)*.

| Field | Value |
|-------|-------|
| **Overlay** | `docker-compose.connector-smtp.yml` |
| **Service** | `ezkey-exp-connector-smtp` |
| **Image** | `ezkey-connector-smtp`, built from `c6d89274702f8f80007fb6149d9204b79ba89bdf` (`c6d89274`) |
| **Version** | `0.2.0-SNAPSHOT` |
| **Contract** | v1 |
| **Template** | `enrollment-qr` (en, fr) |
| **Provider** | AWS SES, region `ca-central-1`, **sandbox** (verified recipients only) |
| **Sender** | `no-reply@mail.ezkey.org` |
| **Admin API** | Recreated with the connector overlay (~32 s). Stack SHA unchanged (`c6d89274`). |
| **Proof** | Operator end-to-end test send from Admin UI succeeded. |

**Compose on this VM.** Every `docker compose` command uses all three files:
`docker-compose.yml` + `docker-compose.javamelody.yml` + `docker-compose.connector-smtp.yml`.
The 2026-11-22 window (`91dc5fe3`) kept this set. Recreating admin-api without
`docker-compose.connector-smtp.yml` drops email delivery with no error. The image line above is
the 2026-11-15 connector build; the **Current** row is the stack SHA.

**Secrets.** SMTP credentials stay in `connector-smtp.env` (mode 600) on the VM only. The delivery
token stays in the VM `.env`. Neither file is in git.

**Rollback.** Recreate admin-api with only `docker-compose.yml` and `docker-compose.javamelody.yml`,
then remove the connector container. Integrated delivery resumes.

## PostgreSQL

Major line change 18 → 19 ([#1421](https://github.com/mgagp/ezkey/issues/1421) lot 2, after
#1474). Runbook: [`../community-host.md`](../community-host.md) § *PostgreSQL major upgrades (#1421)*.

| Field | Value |
|-------|-------|
| **Line** | PostgreSQL 19, image `postgres:19-alpine` |
| **Window** | 2026-11-24, supervised; API outage 08:18:12–08:29:42 America/Toronto (2026-11-24T13:18:12Z–13:29:42Z), **11 min 30 s** |
| **Method** | Lightsail snapshot first; `pg_dump -Fc` of `ezkey_db` and `pg_dumpall --globals-only` with the PostgreSQL 19 client; restore onto a **fresh** volume on `postgres:19-alpine`. The previous PostgreSQL 18 volume is kept as the fallback. Dumps stay off git. |
| **Flyway** | Schema already at V31: no-op on restart. Flyway 12.4.0 logged the expected warning that PostgreSQL 19.0 is newer than its latest verified version; not a failure. |
| **Keyset** | Unchanged (version 1). The kid value stays in operator notes. |
| **Checks** | Public API probes healthy after the window; functional tests by Isabelle and Marc passed. |
| **Stack SHA** | Unchanged (`91dc5fe3`); see **Current**. |
| **Open step** | Volume name alignment back to the stock `postgres-data` (removes the transitional host-only window override, so the stock three-file compose applies again). Separate ops step with its own go; not done yet. |

**Compose on this VM until that alignment.** The three files above plus the host-only PostgreSQL 19
window override (not in git). Do not sync `docker-compose.yml` from main and recreate `postgres`
without it.

## Host OS

Host OS hygiene lane: [`product-docs/global/hygiene/host-os/README.md`](../../../product-docs/global/hygiene/host-os/README.md)
(#909). Update these fields after each H1/H2/H3 campaign (not on app-only deploys). Signal:
`./scripts/lightsail/os-patch-check.sh --target community` (rootbeer-local only).

| Field | Value |
|-------|-------|
| **Blueprint / line** | Amazon Linux 2023 (`amazon_linux_2023`) |
| **releasever (locked)** | `2023.12.20261120` |
| **Kernel (`uname -r`)** | `6.1.190` |
| **Last H1 (security patch)** | 2026-11-23 |
| **Last H2 (AL2023 minor + reboot)** | never |
| **Last H3 (major / new instance)** | never |
| **Last pre-intervention snapshot** | `pre-h1-20261123` (manual, before the 2026-11-23 H1; automatic snapshots off). A separate snapshot preceded the 2026-11-24 PostgreSQL 19 window (§ PostgreSQL). |
| **Host OS note** | H1 done 2026-11-02: AL2023.12.20261102, kernel 6.1.189. 2026-11-15 #1177 applied: hybrid PQ key exchange on the operator SSH channel; authentication still classical. 2026-11-22 app window did not patch the OS (releasever unchanged). H1 done 2026-11-23: AL2023.12.20261102 / kernel 6.1.189 → AL2023.12.20261120 / kernel 6.1.190, outage 08:08:52–08:12:03 America/Toronto (**3 min 11 s**), snapshot `pre-h1-20261123`. |

## Transport TLS (post-quantum key exchange)

Hybrid X25519MLKEM768 (classical X25519 + ML-KEM-768) protects recorded HTTPS against a future
quantum break of the key exchange. It is negotiated per hop only when both ends support it.
Re-probe after any Caddy image refresh or Cloudflare TLS change:
`./scripts/lightsail/tls-pq-probe.sh` (edge) and
`./scripts/lightsail/tls-pq-probe.sh --origin --target community` (origin; rootbeer-local only).
Map: [`V-2026-09-15-post-quantum-crypto-posture`](../../../product-docs/global/vision/V-2026-09-15-post-quantum-crypto-posture.md) § TLS layer (#1174).

| Field | Value |
|-------|-------|
| **Last probe (UTC)** | 2026-11-03T15:29Z (workstation OpenSSL 3.5.7; host OpenSSL 3.5.8) |
| **Client → Cloudflare edge** | **X25519MLKEM768** on `admin-api`, `auth-api`, `integration-api`, `demo-acme` (`.ezkey.online`); negative control (X25519 only) → X25519. TLS 1.3, `TLS_AES_256_GCM_SHA384`. |
| **Origin (Caddy accepts)** | **X25519MLKEM768** on the same four SNI names, probed on the host against `127.0.0.1:443`; control → X25519. TLS 1.3, `TLS_AES_128_GCM_SHA256`. |
| **Cloudflare → origin (Cloudflare chooses)** | Not observable from outside. Cloudflare Automatic Key Exchange prefers X25519MLKEM768 toward origins that support it ([changelog 2026-07-21](https://developers.cloudflare.com/changelog/post/2026-07-21-automatic-origin-key-exchange/), [docs](https://developers.cloudflare.com/ssl/post-quantum-cryptography/pqc-to-origin/)). Documented behaviour + proven origin capability; not directly proven. |
| **Caddy** | `v2.11.4`, image `caddy:2-alpine` (floating tag), digest `sha256:6aeddd44c3078b0f9a35206472a11420648a79c184603ef95957d0a20044cb2b`; community Caddyfile has no `curves` override (Caddy ≥ 2.10 defaults). |
| **Not covered** | Local `docker-compose.with-proxy.yml` Caddy is plain HTTP (no TLS hop). Ezkey mobile and PIA/SDK client stacks decide their own hop. Admin SSH channel: see note. |
| **Admin SSH note** | 2026-11-15 #1177 applied. Hybrid PQ key exchange on the operator SSH channel; authentication still classical. Drop-in `/etc/ssh/sshd_config.d/40-pq-kex.conf` (`root:root` `0600`) on OpenSSH 9.9p1, crypto policy `DEFAULT`. `sshd -T` `kexalgorithms` starts with `mlkem768x25519-sha256`, then `sntrup761x25519-sha512@openssh.com`, then the previous classical list (including `curve25519-sha256`). Classic fallback stays. Rebuilds install the same file from `bootstrap-host.sh`. |

**Probe hostnames.** The 2026-11-03 row names `demo-acme`. That hostname was retired on
2026-11-22. The next probe uses `demo-login` in its place.

## Optional tag

After updating this table, operators may move lightweight tag `deploy/community` to the same
full SHA. Tags are convenience only; this file remains SOOT.
