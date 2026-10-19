# Configuration Reference — ezkey-integration-api

Integration API is the tenant-integration plane: it exposes the API-key-authenticated endpoints
that customer backend servers call to create and wait for auth attempts. It runs **no scheduled
jobs** and has **no database of its own** — it reads from the shared schema managed by Admin API.

> **Shared properties** (encryption, audit integrity)
> are documented in [ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md).

> **Datasource:** Docker/default role is `ezkey_integration` on the shared schema. See
> [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md).

---

## Quick Overview

| Property | Docker env var | Default | Obligation |
|---|---|---|---|
| `server.servlet.session.cookie.name` | — | `EZKEY_INTEGRATION_API_SESSION` | recommandé |
| `ezkey.api-key.rate-limit.enabled` | — | `true` | optionnel |
| `ezkey.api-key.rate-limit.create-auth-attempt.requests` | — | `100` | optionnel |
| `ezkey.api-key.rate-limit.create-auth-attempt.window-minutes` | — | `1` | optionnel |
| `ezkey.api-key.rate-limit.wait-auth-attempt.requests` | — | `200` | optionnel |
| `ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes` | — | `1` | optionnel |
| `ezkey.trusted-proxies.required` | — | `false` | optionnel [prod] |
| `ezkey.trusted-proxies.cidrs` | — | *(empty list)* | optionnel |

---

## Properties by Functional Group

### 0. Servlet session cookie

| Property | Default | Obligation | Description |
|---|---|---|---|
| `server.servlet.session.cookie.name` | `EZKEY_INTEGRATION_API_SESSION` | recommandé | Distinct from other localhost servlet apps so browsers do not share `JSESSIONID` across ports (#799). Lives in `config/application.properties` (API external config); demos set the same key in the JAR instead. **#816:** Integration API is `STATELESS` + `NullRequestCache` (no servlet cookie on 401). Also `http-only` / `same-site=strict` / `secure=${EZKEY_SERVLET_SESSION_COOKIE_SECURE:false}`. |

### 1. API Key Usage Rate Limiting (`ezkey.api-key.rate-limit.*`)

**Description:** protects the two API-key-authenticated endpoints — create auth attempt and
wait/long-poll auth attempt — keyed by integration ID (API key bearer). High default limits
suit integration server use cases. Policy map:
[rate-limit-baseline-policy.md](../product-docs/global/rate-limit-baseline-policy.md).

**Defined in:** `ApiKeyRateLimitProperties` (in `org.ezkey.integration.api.config`)

> The same prefix (`ezkey.api-key.rate-limit.*`) also appears in
> [ezkey-admin-api](../ezkey-admin-api/CONFIGURATION.md#7-api-key-usage-rate-limiting-ezkeyapi-keyrate-limit)
> with different default limits.

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.api-key.rate-limit.enabled` | `boolean` | `true` | optionnel | Global on/off. When `false`, a no-op implementation is used and no requests are blocked. |
| `ezkey.api-key.rate-limit.create-auth-attempt.requests` | `int` | `100` | optionnel | Max `POST /auth-attempts` requests per window per API key. |
| `ezkey.api-key.rate-limit.create-auth-attempt.window-minutes` | `int` | `1` | optionnel | Window for `create-auth-attempt` (minutes). |
| `ezkey.api-key.rate-limit.wait-auth-attempt.requests` | `int` | `200` | optionnel | Max `GET /auth-attempts/{id}/wait` requests per window per API key. |
| `ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes` | `int` | `1` | optionnel | Window for `wait-auth-attempt` (minutes). |

**`docker` profile defaults (from `application-docker.properties`):**

```properties
ezkey.api-key.rate-limit.enabled=true
ezkey.api-key.rate-limit.create-auth-attempt.requests=100
ezkey.api-key.rate-limit.create-auth-attempt.window-minutes=1
ezkey.api-key.rate-limit.wait-auth-attempt.requests=200
ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes=1
```

**`docker-test` profile:** API-key rate limiting disabled (`ezkey.api-key.rate-limit.enabled=false`). Peripheral heartbeat supervision (`ezkey.audit.chain.heartbeat.*`) follows docker defaults (`enabled=true`, `required=true`) so Integration API stays fail-closed when checkpoints stall — alignment with `AuditChainHeartbeatGuardService`.

---

### 2. Trusted Proxies (`ezkey.trusted-proxies.*`)

**Description:** trusted proxy CIDR list for client IP resolution from forwarded headers. See
[Admin API §10](../ezkey-admin-api/CONFIGURATION.md#10-trusted-proxies-ezkeytrusted-proxies)
for the full description.

**Defined in:** `TrustedProxyProperties` (copy in `org.ezkey.integration.api.config`)

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.trusted-proxies.required` | `boolean` | `false` | optionnel [prod] | When `true`, fail startup if `cidrs` is empty or invalid (SEC-011). Set via **`EZKEY_TRUSTED_PROXIES_REQUIRED=true`** on Docker / Lightsail / `--with-proxy`. |
| `ezkey.trusted-proxies.cidrs` | `List<String>` | *(empty)* | optionnel | CIDR ranges of trusted reverse proxies. |

---

## Shared Properties (ezkey-core)

| Prefix | Active in Integration API | Notes |
|---|---|---|
| `ezkey.encryption.*` | ✓ | Rotation and re-encryption **disabled** (`enabled=false`). Keyset loaded from DATABASE, synchronized by Admin API. |
| `ezkey.audit.integrity.*` | ✓ | HMAC signing of audit events. |
| `ezkey.audit.chain.*` | disabled | Scheduler off (`enabled=false`). **`window-minutes`** still set explicitly in Docker profiles — must match Admin API (`AuditChainHeartbeatGuardService`). See [ezkey-core §audit-chain](../ezkey-core/CONFIGURATION.md#audit-log-chain-ezkeyauditchain). |

---

## Profile Matrix

| Property | default | docker | docker-test |
|---|---|---|---|
| `ezkey.api-key.rate-limit.enabled` | `true` | `true` | `false` |
| `ezkey.api-key.rate-limit.create-auth-attempt.requests` | `100` | `100` | `100` |
| `ezkey.api-key.rate-limit.create-auth-attempt.window-minutes` | `1` | `1` | `1` |
| `ezkey.encryption.rotation.enabled` | `true` | `false` | `false` |
| `ezkey.encryption.reencryption.enabled` | `true` | `false` | `false` |
| `ezkey.audit.chain.enabled` | `true` | `false` | `false` |
| `ezkey.audit.chain.window-minutes` | `5` | `5` | `5` |

---

## Docker Environment Variable Reference

| Spring property | Docker env var | Notes |
|---|---|---|
| `ezkey.audit.integrity.instance-id` | `EZKEY_INSTANCE_ID` | Default `integration-api` in docker profile. |

> No `EZKEY_*` env vars are surfaced in `docker-compose.yml` for Integration API beyond
> standard infra variables (`SPRING_DATASOURCE_*`, `MANAGEMENT_SERVER_PORT`).

---

## Management port (Actuator)

| Property | Default (IDE) | Docker | Description |
|---|---|---|---|
| `management.server.port` | `7081` | `7081` | Dedicated management port (not on `7080`). |
| `management.server.address` | `127.0.0.1` | `0.0.0.0` | Loopback for IDE; Docker profile sets `0.0.0.0` so PAM/HAProxy peers reach `integration-api:7081` (#905); local compose publishes `127.0.0.1:7081:7081`. |

See [`docs/LOCAL_STACK_PORTS.md`](../docs/LOCAL_STACK_PORTS.md) and [ADR-0015](../product-docs/global/architecture-decisions.md#adr-0015-management-port-separation-actuator-javamelody).

---

## JavaMelody (opt-in Docker diagnostics)

Disabled by default. Enable with `./ezkey-tests/clean-start.sh --with-java-melody`, or on Lightsail
via `experimental-hybrid/lightsail/docker-compose.javamelody.yml` (`EZKEY_ENABLE_JAVA_MELODY=true`).
Management-port reports: `/actuator/monitoring` only. Startup refuses JavaMelody without
`management-endpoint-monitoring-enabled=true`, without `management.server.port`, or with equal
business/management ports. Community binds management to `127.0.0.1`; JM overlay reopens `0.0.0.0`.
Collector UI: `http://127.0.0.1:8088` (SSH tunnel on community — see
[`docs/lightsail/community-host.md`](../docs/lightsail/community-host.md) § Observability,
[#846](https://github.com/mgagp/ezkey/issues/846)). See [`docker/README.md`](../docker/README.md)
§ JavaMelody collector and [ADR-0015](../product-docs/global/architecture-decisions.md#adr-0015-management-port-separation-actuator-javamelody).
