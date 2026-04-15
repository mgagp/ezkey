# Configuration Reference — ezkey-auth-api

Auth API is the device-facing plane: it receives pending auth requests from Integration API,
serves them to enrolled mobile devices, and processes responses. It runs **no scheduled jobs**
(rotation, re-encryption, and chain checkpoints run only in Admin API).

> **Shared properties** (encryption, audit integrity, organization, QR, core auth-attempt)
> are documented in [ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md).

---

## Quick Overview

| Property | Docker env var | Default | Obligation |
|---|---|---|---|
| `ezkey.rate-limit.enabled` | — | `false` | requis [docker] |
| `ezkey.rate-limit.pending.requests` | — | `10` | optionnel |
| `ezkey.rate-limit.pending.window-minutes` | — | `1` | optionnel |
| `ezkey.rate-limit.respond.requests` | — | `1` | optionnel |
| `ezkey.rate-limit.respond.window-minutes` | — | `5` | optionnel |
| `ezkey.rate-limit.verify.requests` | — | `10` | optionnel |
| `ezkey.rate-limit.bind.requests` | — | `10` | optionnel |
| `ezkey.trusted-proxies.cidrs` | — | *(empty list)* | optionnel |
| `ezkey.demo.mitm-signature-enabled` | `EZKEY_DEMO_MITM_SIGNATURE_ENABLED` | `false` | optionnel |
| `ezkey.qr.auth-base-url` | `EZKEY_QR_AUTH_BASE_URL` | *(null)* | requis [docker] |
| `ezkey.organization.about-url` | `EZKEY_ORGANIZATION_ABOUT_URL` | *(null)* | optionnel |

---

## Properties by Functional Group

### 1. Auth API Rate Limiting (`ezkey.rate-limit.*`)

**Description:** protects the four mobile-device endpoints: `pending` (polling), `respond`
(approve/deny), `verify` (enrollment), and `bind` (enrollment completion). Default `enabled`
is `false` in the Java class; the Docker profile enables it.

**Defined in:** `RateLimitProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.rate-limit.enabled` | `boolean` | `false` | requis [docker] | Global on/off. Docker profile sets `true`. |
| `ezkey.rate-limit.pending.requests` | `int` | `10` | optionnel | Max pending-check requests per window. |
| `ezkey.rate-limit.pending.window-minutes` | `int` | `1` | optionnel | Window for `pending` endpoint (minutes). |
| `ezkey.rate-limit.pending.key-strategy` | `String` | `client-ip` | optionnel | Rate-limit key strategy. Docker: `enrollment-id`. |
| `ezkey.rate-limit.respond.requests` | `int` | `1` | optionnel | Max respond requests per auth-attempt per window. Strict by design (one approve/deny per attempt). |
| `ezkey.rate-limit.respond.window-minutes` | `int` | `5` | optionnel | Window for `respond` endpoint (minutes). |
| `ezkey.rate-limit.respond.key-strategy` | `String` | `auth-attempt-id` | optionnel | Default `auth-attempt-id`. Do not change. |
| `ezkey.rate-limit.verify.requests` | `int` | `10` | optionnel | Max enrollment-verify requests per window. |
| `ezkey.rate-limit.verify.window-minutes` | `int` | `1` | optionnel | Window for `verify` endpoint (minutes). |
| `ezkey.rate-limit.verify.key-strategy` | `String` | `client-ip` | optionnel | Key strategy. Docker: `client-ip`. |
| `ezkey.rate-limit.bind.requests` | `int` | `10` | optionnel | Max enrollment-bind requests per window. |
| `ezkey.rate-limit.bind.window-minutes` | `int` | `1` | optionnel | Window for `bind` endpoint (minutes). |
| `ezkey.rate-limit.bind.key-strategy` | `String` | `client-ip` | optionnel | Key strategy. Docker: `client-ip`. |

**Key strategy options:**

| Value | Description |
|---|---|
| `client-ip` | Rate-limit by remote IP (or forwarded IP when behind trusted proxy). |
| `enrollment-id` | Rate-limit by enrollment ID from the request (device-scoped polling). |
| `auth-attempt-id` | Rate-limit by auth attempt ID (per-attempt — only for `respond`). |

**`docker` profile defaults:**

```properties
ezkey.rate-limit.enabled=true
ezkey.rate-limit.pending.requests=10
ezkey.rate-limit.pending.window-minutes=1
ezkey.rate-limit.pending.key-strategy=enrollment-id
ezkey.rate-limit.respond.requests=1
ezkey.rate-limit.respond.window-minutes=5
ezkey.rate-limit.respond.key-strategy=auth-attempt-id
ezkey.rate-limit.verify.requests=5
ezkey.rate-limit.verify.window-minutes=5
ezkey.rate-limit.verify.key-strategy=client-ip
ezkey.rate-limit.bind.requests=3
ezkey.rate-limit.bind.window-minutes=5
ezkey.rate-limit.bind.key-strategy=client-ip
```

---

### 2. Trusted Proxies (`ezkey.trusted-proxies.*`)

**Description:** trusted proxy CIDR list for client IP resolution from forwarded headers.
See [Admin API §10](../ezkey-admin-api/CONFIGURATION.md#10-trusted-proxies-ezkeytrusted-proxies)
for the full description.

**Defined in:** `TrustedProxyProperties` (copy in `org.ezkey.auth.config`)

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.trusted-proxies.cidrs` | `List<String>` | *(empty)* | optionnel | CIDR ranges of trusted reverse proxies. |

---

## Shared Properties (ezkey-core)

| Prefix | Active in Auth API | Notes |
|---|---|---|
| `ezkey.encryption.*` | ✓ | Rotation and re-encryption **disabled** (`enabled=false`). Keyset loaded from DATABASE. |
| `ezkey.audit.integrity.*` | ✓ | HMAC signing of all audit events. |
| `ezkey.audit.chain.*` | disabled | `ezkey.audit.chain.enabled=false` in docker profile. |
| `ezkey.organization.*` | ✓ | Exposed via `GET /api/v1/public/instance-info` (same contract as Admin API). |
| `ezkey.qr.*` | ✓ | Embedded in `GET /api/v1/public/instance-info` response. |
| `ezkey.core.*` | ✓ | Auth-attempt challenge digits and TTL. |
| `ezkey.demo.*` | ✓ | MITM simulation gate. Default `false`. |

---

## Profile Matrix

| Property | default | docker | docker-test |
|---|---|---|---|
| `ezkey.rate-limit.enabled` | `false` | `true` | *(inherits docker)* |
| `ezkey.rate-limit.pending.key-strategy` | `client-ip` | `enrollment-id` | `enrollment-id` |
| `ezkey.rate-limit.respond.requests` | `1` | `1` | `1` |
| `ezkey.rate-limit.respond.key-strategy` | `auth-attempt-id` | `auth-attempt-id` | `auth-attempt-id` |
| `ezkey.encryption.rotation.enabled` | `true` | `false` | `false` |
| `ezkey.encryption.reencryption.enabled` | `true` | `false` | `false` |
| `ezkey.audit.chain.enabled` | `true` | `false` | `false` |
| `ezkey.demo.mitm-signature-enabled` | `false` | `${EZKEY_DEMO_MITM_SIGNATURE_ENABLED:-true}` | `${EZKEY_DEMO_MITM_SIGNATURE_ENABLED:-true}` |
| `ezkey.qr.auth-base-url` | *(null)* | `${EZKEY_QR_AUTH_BASE_URL:}` | `${EZKEY_QR_AUTH_BASE_URL:}` |

---

## Docker Environment Variable Reference

| Spring property | Docker env var | docker-compose default |
|---|---|---|
| `ezkey.demo.mitm-signature-enabled` | `EZKEY_DEMO_MITM_SIGNATURE_ENABLED` | `true` (dev stack); set `false` for production-like runs |
| `ezkey.qr.auth-base-url` | `EZKEY_QR_AUTH_BASE_URL` | *(empty)* |
| `ezkey.organization.about-url` | `EZKEY_ORGANIZATION_ABOUT_URL` | *(empty)* |
| `ezkey.audit.integrity.instance-id` | `EZKEY_INSTANCE_ID` | `auth-api` |

---

## Security Notes

1. `ezkey.rate-limit.enabled` is `false` by default — always enable it in Docker/production.
2. Do not change `ezkey.rate-limit.respond.key-strategy`; `auth-attempt-id` is required for
   correct per-attempt throttling.
3. `ezkey.demo.mitm-signature-enabled` must be `false` in every production environment. The
   Docker development stack sets it `true` by default for demo convenience.
