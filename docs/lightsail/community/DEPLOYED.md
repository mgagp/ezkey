# Community deploy ledger (ezkey.online)

**Source of truth** for what is live on the **community** surface (deploy SHA / timing).
Public product hostnames (served vs not served, DNS, expected `/` codes): see
[`../community-host.md`](../community-host.md) § *Product hostnames (ezkey.online)*.

- **Admin UI** — Cloudflare Pages (not a VM container)
- **APIs / Demo ACME** — Lightsail Docker host behind Caddy

Normative rules: [`../VERSIONING_AND_DEPLOY_TRACEABILITY.md`](../../VERSIONING_AND_DEPLOY_TRACEABILITY.md).

History of this file (git log) is the deploy history. Update **only after** a successful
publish — see that doc § *Update procedure*.

**Traceability lock:** record the **full git SHA**. Docker `*:latest`, npm `0.0.0`, and
actuator/health with no version string are **not** deploy identity.

## Current

| Field | Value |
|-------|-------|
| **Surface** | community (ezkey.online) |
| **Full git SHA** | `c6d89274702f8f80007fb6149d9204b79ba89bdf` |
| **Short SHA** | `c6d89274` |
| **Deployed at (UTC)** | 2026-11-15T17:53:00Z |
| **Operator** | Edgar |
| **Note** | Backend upgrade 2026-11-15 ~12:53–12:56 ET / America/Toronto (17:53–17:56 UTC) to main `c6d89274` (from `10264a0b`). APIs recreated sequentially (~35 s each). Flyway V25 -> V29. V26 activation tokens: 2 converted, 0 revoked. V28: 4 of 13 tenants marked self-registered. V27: all integrations REPLACE. V29: partition job seeded. Keyset unchanged. JavaMelody collector not updated in this window (still previous image). API Shield schemas re-uploaded for Auth and Integration on the ezkey.online zone. Admin UI (Cloudflare Pages project `admin-ui-ezkey-online`, production branch `main`, API base `https://admin-api.ezkey.online`) published from `c6d8927`. Email delivery connector (SES) not enabled yet. Host: `<static-ip ezkey-online-community>`. |

## Host OS

Host OS hygiene lane: [`product-docs/global/hygiene/host-os/README.md`](../../../product-docs/global/hygiene/host-os/README.md)
(#909). Update these fields after each H1/H2/H3 campaign (not on app-only deploys). Signal:
`./scripts/lightsail/os-patch-check.sh --target community` (rootbeer-local only).

| Field | Value |
|-------|-------|
| **Blueprint / line** | Amazon Linux 2023 (`amazon_linux_2023`) |
| **releasever (locked)** | `2023.12.20261102` |
| **Kernel (`uname -r`)** | `6.1.189` |
| **Last H1 (security patch)** | 2026-11-02 |
| **Last H2 (AL2023 minor + reboot)** | never |
| **Last H3 (major / new instance)** | never |
| **Last pre-intervention snapshot** | not named in this ledger (0 snapshots verified 2026-10-19, before this H1; automatic snapshots off) |
| **Host OS note** | H1 done 2026-11-02: AL2023.12.20261102, kernel 6.1.189. 2026-11-15 #1177 applied: hybrid PQ key exchange on the operator SSH channel; authentication still classical. |

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

## Optional tag

After updating this table, operators may move lightweight tag `deploy/community` to the same
full SHA. Tags are convenience only; this file remains SOOT.
