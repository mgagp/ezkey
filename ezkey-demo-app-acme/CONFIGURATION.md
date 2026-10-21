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

## Tenant-alias mapping file (`ezkey.tenant-aliases.*`) — #958

External hot-reloaded mappings for community evaluator links (`GET /t/{alias}`). The file lives on
the `demo-app-acme-config` volume (outside the image and the git repo). `application.properties`
remains the base config; optional `ezkey.access-codes.*` slots there still work but need a restart.

| Property | Default | Obligation | Description |
|----------|---------|------------|-------------|
| `ezkey.tenant-aliases.file` | `/app/config/tenant-aliases.properties` | recommandé (community) | Path to the line-oriented mapping file. Empty disables external loading. |
| `ezkey.tenant-aliases.reload-throttle-seconds` | `5` | optionnel | Minimum seconds between mtime checks on URL access. Reread only when mtime changed. No periodic polling. |

Line format: `alias|integration-key|secret-key|label` (alias = 16 lowercase hex for community). Mode
**0600**, owner **spring** (uid **100** / gid **101**). Unreadable or unusable content keeps the last
valid mappings; WARN logs never include the secret or full alias. Operator runbook:
[`docs/lightsail/community/acme-tenant-alias-runbook.md`](../docs/lightsail/community/acme-tenant-alias-runbook.md).

Env overrides: `EZKEY_TENANT_ALIASES_FILE`, `EZKEY_TENANT_ALIASES_RELOAD_THROTTLE_SECONDS`.

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
