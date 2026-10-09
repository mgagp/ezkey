# Ezkey Demo App ACME — Configuration

Demo-only browser app (port 8082). Properties bind from JAR `application.properties`, optional
external `/app/config/` (volume `demo-app-acme-config`), and environment variables.

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
