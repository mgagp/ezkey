# Ezkey Demo App ACME — Configuration

Demo-only browser app (business port **8082**). Properties bind from JAR `application.properties`,
optional external `/app/config/` (volume `demo-app-acme-config`), and environment variables.

## Management / Actuator

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `management.server.port` | `8086` | recommandé | Dedicated management port. Actuator (`/actuator/health`) is **not** on 8082. |
| `management.endpoints.web.exposure.include` | `health` | recommandé | Minimal exposure; `show-details=never`. |
| `javamelody.enabled` | `false` | recommandé | Starter not on classpath today; flag kept fail-closed if added later. |

Community compose sets `MANAGEMENT_SERVER_PORT=8086` and `MANAGEMENT_SERVER_ADDRESS=127.0.0.1`
(healthchecks use `http://127.0.0.1:8086/...`). Caddy still reverse-proxies only **8082** and keeps
the observability deny block. Deploy image + compose together: a healthcheck on 8086 with an old
image that only listens on 8082 blocks Caddy (`depends_on: service_healthy`).

## Session cookie

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `server.servlet.session.cookie.name` | `EZKEY_ACME_SESSION` | recommandé | Distinct from demo-device / APIs on localhost so browsers do not share one `JSESSIONID` across ports (#799). Set in the JAR `application.properties` (demos); API modules set the same key under external `config/` instead. |

Redeploying Acme drops any in-memory sessions that still used the previous cookie name. Do not
override this to `JSESSIONID` in the optional `/app/config/` volume.

## Access-code slots (`ezkey.access-codes.*`)

Optional multi-tenant evaluator links (`GET /t/{code}`). Bind from external
`/app/config/application.properties` on volume `demo-app-acme-config` (or the JAR defaults). Loaded
**once at JVM start** — edit then **restart** the Acme container. Fail-fast if a code is missing,
not `^[0-9a-f]{32,64}$`, or duplicated across slots.

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `ezkey.access-codes.<slot>.code` | (none) | requis per slot | Capability URL token; 32–64 lowercase hex (`openssl rand -hex 16`). Never log. |
| `ezkey.access-codes.<slot>.integration-key` | (none) | requis per slot | Tenant Integration API integration key for that slot. |
| `ezkey.access-codes.<slot>.secret-key` | (none) | requis per slot | Matching secret key. Plaintext on the volume (see `docs/SECURITY_POSTURE.md`). |
| `ezkey.access-codes.<slot>.label` | (none) | requis per slot | Operator-facing label in logs / login heading (never the code). |

Legacy single-tenant demo keys (optional when not using slots):

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `ezkey.integration-key` | empty | optionnel | Env `EZKEY_INTEGRATION_KEY`. |
| `ezkey.secret-key` | empty | optionnel | Env `EZKEY_SECRET_KEY`. |

Community operator steps: [`docs/lightsail/acme-access-code-runbook.md`](../docs/lightsail/acme-access-code-runbook.md).
There is no Actuator `/refresh` for these properties and no second hot-reloaded mapping file.

## Rate limiting (`ezkey.rate-limit.*`)

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `ezkey.rate-limit.enabled` | `true` | optionnel | Global on/off for demo Bucket4j limits. |
| `ezkey.rate-limit.login.requests` | `20` | optionnel | Ceiling for IP-only `/t/{code}` (all codes share one budget per IP), IP-only self-service login, and per slot+IP after a valid code / slot-mode login. |
| `ezkey.rate-limit.login.window-minutes` | `5` | optionnel | Window for `login.*`. |
| `ezkey.rate-limit.login-ip-bound.requests` | `60` | optionnel | Wider per-IP bound consumed on every `POST /login` **in slot mode**, in addition to slot+IP. Caps aggregate attempts across many slots from one NAT IP. |
| `ezkey.rate-limit.login-ip-bound.window-minutes` | `5` | optionnel | Window for `login-ip-bound.*`. |
| `ezkey.rate-limit.apply-api-key.requests` | `5` | optionnel | Separate bucket for Apply API Key. |
| `ezkey.rate-limit.apply-api-key.window-minutes` | `10` | optionnel | Window for apply-api-key. |

Env overrides (Spring relaxed binding): `EZKEY_RATE_LIMIT_LOGIN_REQUESTS`,
`EZKEY_RATE_LIMIT_LOGIN_IP_BOUND_REQUESTS`, etc.

### Semantics (deliberate)

- **`GET /t/{code}`:** always consumes the IP-only `login` bucket first (anti-enumeration / no
  bucket-state oracle). Valid codes also consume slot+IP. So `/t` is bounded at **20 / 5 min per
  IP across all codes**, not isolated per slot.
- **`POST /login` with slot in session:** slot+IP (`login.*`) **plus** per-IP `login-ip-bound.*`.
- **`POST /login` without slot:** IP-only `login.*` only.
- **`GET /api/auth-status`:** does not consume these buckets.

See `README.md` § Rate limiting (QA notes).
