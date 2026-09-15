# Configuration Reference — ezkey-admin-api

Admin API is the management plane for Ezkey: initial bootstrap, admin authentication (with MFA),
tenant and integration management, enrollment lifecycle, and audit log chain. It runs the
**only** scheduled jobs in the cluster (key rotation, re-encryption, audit chain, token cleanup).

> **Shared properties** (encryption, audit integrity/chain, organization, QR, enrollment, core)
> are documented in [ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md).

> **Datasource:** Docker/default role is `ezkey_admin` (`SPRING_DATASOURCE_USERNAME` /
> `SPRING_DATASOURCE_PASSWORD`). See
> [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md).

---

## Quick Overview

| Property | Docker env var | Default | Obligation |
|---|---|---|---|
| `ezkey.admin.initial.username` | `EZKEY_ADMIN_INITIAL_USERNAME` | `admin.docker` | requis |
| `ezkey.admin.initial.email` | `EZKEY_ADMIN_INITIAL_EMAIL` | `admin@ezkey.local` | requis |
| `ezkey.admin.initial.first-name` | `EZKEY_ADMIN_INITIAL_FIRST_NAME` | `Admin` | requis |
| `ezkey.admin.initial.last-name` | `EZKEY_ADMIN_INITIAL_LAST_NAME` | `Docker` | requis |
| `ezkey.admin.mfa.mode` | — | `dev` | requis [docker] |
| `ezkey.admin.mfa.bootstrap.credentials-output-mode` | `EZKEY_ADMIN_MFA_BOOTSTRAP_CREDENTIALS_OUTPUT_MODE` | `FULL` | optionnel |
| `ezkey.admin.token.rotation-on-login` | — | `true` | optionnel |
| `ezkey.admin.token.expiration-hours` | — | `2` | optionnel |
| `ezkey.admin.token.cleanup.enabled` | — | `true` | optionnel |
| `ezkey.admin.recovery.codes-count` | — | `5` | optionnel |
| `ezkey.admin.rate-limit.enabled` | — | `true` | optionnel |
| `ezkey.admin-operations.rate-limit.enabled` | — | `true` | optionnel |
| `ezkey.security.admin.max-global-admins` | — | `3` | optionnel |
| `ezkey.admin.bootstrap.export.enabled` | — | `false` | optionnel |
| `ezkey.trusted-proxies.required` | — | `false` | optionnel [prod] |
| `ezkey.trusted-proxies.cidrs` | — | *(empty list)* | optionnel |
| `ezkey.admin.cors.allowed-origins` | `EZKEY_ADMIN_CORS_ALLOWED_ORIGINS` | *(empty list)* | optionnel |
| `ezkey.evaluator.self-registration.enabled` | `EZKEY_EVALUATOR_SELF_REGISTRATION_ENABLED` | `false` | optionnel |
| `ezkey.admin.auth.browser-session-cookie-enabled` | `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED` | `false` | optionnel |
| `ezkey.admin.auth.browser-session-cookie-name` | `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_NAME` | `EZKEY_ADMIN_SESSION` | optionnel |
| `ezkey.admin.auth.browser-session-cookie-secure` | `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_SECURE` | `true` | optionnel |
| `ezkey.auth-attempt.expiry-scheduler.enabled` | — | `true` | optionnel |
| `ezkey.auth-attempt.expiry-scheduler.fixed-delay-ms` | — | `60000` | optionnel |

---

## Properties by Functional Group

### 1. Bootstrap — Initial Global Admin (`ezkey.admin.initial.*`)

**Description:** seeds the first Global Admin account on first startup. The system bootstraps
only if no Global Admin exists yet. Identifiable operator identity is required
(not `admin` or `root`).

**Defined in:** `InitialGlobalAdminProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.initial.username` | `String` | *(none)* | requis | Username for the initial Global Admin. Must identify an individual. Forbidden values: `admin`, `administrator`, `root`, `superuser`, `super`, `user`, `test`, `demo`. |
| `ezkey.admin.initial.email` | `String` | *(none)* | requis | Email for the initial Global Admin (operator-visible audit trail). Must be valid format. |
| `ezkey.admin.initial.first-name` | `String` | *(none)* | requis | First name (identifiable Global Admin identity). |
| `ezkey.admin.initial.last-name` | `String` | *(none)* | requis | Last name (identifiable Global Admin identity). |

**Docker values (docker-compose.yml defaults):**

```properties
ezkey.admin.initial.username=${EZKEY_ADMIN_INITIAL_USERNAME:admin.docker}
ezkey.admin.initial.email=${EZKEY_ADMIN_INITIAL_EMAIL:admin@ezkey.local}
ezkey.admin.initial.first-name=${EZKEY_ADMIN_INITIAL_FIRST_NAME:Admin}
ezkey.admin.initial.last-name=${EZKEY_ADMIN_INITIAL_LAST_NAME:Docker}
```

---

### 2. Admin MFA (`ezkey.admin.mfa.*`)

**Description:** controls MFA enforcement mode for admin logins and the bootstrap that creates
Integration Zero and Enrollment Zero at first startup ("Eat Your Own Dog Food").

**Defined in:** `AdminMfaProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.mfa.mode` | `String` | `dev` | requis [docker] | `dev` = MFA optional; `prod` = MFA required for all admins. Always `prod` in docker profile. |
| `ezkey.admin.mfa.bootstrap.enabled` | `boolean` | `true` | optionnel | Create Integration Zero and Enrollment Zero at startup if absent. |
| `ezkey.admin.mfa.bootstrap.auto-enrollment` | `boolean` | `true` | optionnel | Automatically create Enrollment Zero for the Global Admin. Convenient in dev; consider `false` in production. |
| `ezkey.admin.mfa.bootstrap.credentials-output-mode` | `BootstrapCredentialsOutputMode` | `FULL` | optionnel | `FULL` = enrollment token, challenge, ASCII QR, recovery codes, CLI hints, JSON export. `RECOVERY_PRIMARY` = recovery codes + instructions only; no enrollment secrets in logs, no JSON export. |

**Enum values for `credentials-output-mode`:**

| Value | Description |
|---|---|
| `FULL` | Maximum output for dev/demo and Docker clean-start automation. |
| `RECOVERY_PRIMARY` | Production-oriented: only recovery codes and operator instructions. |

---

### 3. Admin Tokens (`ezkey.admin.token.*`)

**Description:** admin session token lifetime and rotation policy. Sliding expiration extends the
token on each validated request within the window.

**Defined in:** `AdminTokenRotationProperties`, `AdminTokenCleanupProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.token.rotation-on-login` | `boolean` | `true` | optionnel | Deactivate all previous tokens on new login. Limits to one active token per admin; stolen tokens expire on the next legitimate login. |
| `ezkey.admin.token.expiration-hours` | `int` | `2` | optionnel | Token TTL in hours, also used as the sliding window: each validated request extends expiry by this amount. |
| `ezkey.admin.token.cleanup.enabled` | `boolean` | `true` | optionnel | Enable the scheduled job that removes expired/inactive tokens. |
| `ezkey.admin.token.cleanup.schedule` | `String` | `0 0 * * * *` | optionnel | Cron schedule for token cleanup (default: every hour). |

---

### 4. Recovery Codes (`ezkey.admin.recovery.*`)

**Description:** emergency admin access when a device is lost. Recovery codes are single-use,
BCrypt-hashed, 106-bit entropy (format: `XXXX-XXXX-…`). They grant a short-lived temporary
token for enrollment re-binding only.

**Defined in:** `AdminRecoveryProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.recovery.codes-count` | `int` | `5` | optionnel | Number of recovery codes generated per admin. |
| `ezkey.admin.recovery.temp-token-duration-minutes` | `int` | `30` | optionnel | Temporary recovery token validity in minutes. |

---

### Admin / Integration rate-limit policy map

Cross-cutting families (device vs integration throughput vs admin sensitive ops vs login):
[rate-limit-baseline-policy.md](../product-docs/global/rate-limit-baseline-policy.md). Sections 5–7 below
are the Admin API property detail.

### 5. Admin Login Rate Limiting (`ezkey.admin.rate-limit.*`)

**Description:** protects the admin login endpoint against brute-force. Applied by IP. Includes
a blocking mechanism that locks out IPs after repeated failures.

**Defined in:** `AdminRateLimitProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.rate-limit.enabled` | `boolean` | `true` | optionnel | Global on/off for admin login rate limiting. |
| `ezkey.admin.rate-limit.login.requests` | `int` | `5` | optionnel | Maximum login requests per window. |
| `ezkey.admin.rate-limit.login.window-minutes` | `int` | `5` | optionnel | Rate-limit window in minutes. Docker uses `1`. |
| `ezkey.admin.rate-limit.login.key-strategy` | `String` | `client-ip` | optionnel | Key strategy. Only `client-ip` is implemented. |
| `ezkey.admin.rate-limit.login.block-after-failures` | `int` | `10` | optionnel | Consecutive failures before IP block. `0` disables blocking. |
| `ezkey.admin.rate-limit.login.block-duration-minutes` | `int` | `30` | optionnel | Duration in minutes for an IP block. |
| `ezkey.admin.rate-limit.backstop.enabled` | `boolean` | `true` | optionnel | Unkeyed per-process cap on login / passwordless-wait. Follows ADR-0010 (not distributed). |
| `ezkey.admin.rate-limit.backstop.login.requests` | `int` | `50` | optionnel | Process-wide admin-auth budget. |
| `ezkey.admin.rate-limit.backstop.login.window-minutes` | `int` | `1` | optionnel | Window for the login backstop. |

N Admin replicas imply N times the backstop headroom; edge rate limiting remains the public-host control.

**`docker` profile values:**

```properties
ezkey.admin.rate-limit.enabled=true
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=1
ezkey.admin.rate-limit.login.key-strategy=client-ip
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30
ezkey.admin.rate-limit.backstop.enabled=true
ezkey.admin.rate-limit.backstop.login.requests=50
ezkey.admin.rate-limit.backstop.login.window-minutes=1
```

**`docker-test` profile:** rate limiting disabled (`ezkey.admin.rate-limit.enabled=false`).

---

### 6. Admin Operations Rate Limiting (`ezkey.admin-operations.rate-limit.*`)

**Description:** rate limiting for authenticated admin operations (API key creation, revocation,
update, enrollment reset). Keyed per admin session, not by IP.

**Defined in:** `AdminOperationsRateLimitProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin-operations.rate-limit.enabled` | `boolean` | `true` | optionnel | Global on/off for admin operations rate limiting. |
| `ezkey.admin-operations.rate-limit.api-key-create.requests` | `int` | `5` | optionnel | API key creation limit per window. |
| `ezkey.admin-operations.rate-limit.api-key-create.window-minutes` | `int` | `15` | optionnel | Window for `api-key-create` (minutes). |
| `ezkey.admin-operations.rate-limit.api-key-revoke.requests` | `int` | `10` | optionnel | API key revocation limit per window. |
| `ezkey.admin-operations.rate-limit.api-key-revoke.window-minutes` | `int` | `15` | optionnel | Window for `api-key-revoke` (minutes). |
| `ezkey.admin-operations.rate-limit.api-key-update.requests` | `int` | `20` | optionnel | API key update limit per window. |
| `ezkey.admin-operations.rate-limit.api-key-update.window-minutes` | `int` | `15` | optionnel | Window for `api-key-update` (minutes). |
| `ezkey.admin-operations.rate-limit.enrollment-reset.requests` | `int` | `3` | optionnel | Enrollment reset limit per window. |
| `ezkey.admin-operations.rate-limit.enrollment-reset.window-minutes` | `int` | `30` | optionnel | Window for `enrollment-reset` (minutes). |

---

### 7. API-key usage rate limiting (Integration API only)

Admin API no longer authenticates API keys and does not apply `ezkey.api-key.rate-limit.*`.
M2M create/wait limits live on [ezkey-integration-api](../ezkey-integration-api/CONFIGURATION.md).

---

### 8. Admin Security Limits (`ezkey.security.admin.*`)

**Description:** hard caps on Global Admin and Tenant Admin count. Prevent accidental lockout
(min floor) and excessive privilege proliferation (max cap).

**Defined in:** `AdminSecurityProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.security.admin.max-global-admins` | `int` | `3` | optionnel | Maximum Global Admins in the system. |
| `ezkey.security.admin.min-global-admins` | `int` | `1` | optionnel | Minimum active Global Admins; deactivation blocked below this floor. |
| `ezkey.security.admin.max-tenant-admins-per-tenant` | `int` | `3` | optionnel | Maximum Tenant Admins per tenant. |
| `ezkey.security.admin.min-tenant-admins-per-tenant` | `int` | `1` | optionnel | Minimum Tenant Admins per tenant; deactivation blocked below this floor. |

---

### 9. Bootstrap Credentials Export (`ezkey.admin.bootstrap.export.*`)

**Description:** exports enrollment credentials (enrollmentId, proof token, challenge, username)
to a JSON file for Docker automation (clean-start). Recovery codes are **never exported**
(logs only). Disable in production.

**Defined in:** `BootstrapExportProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.bootstrap.export.enabled` | `boolean` | `false` | optionnel | Enable/disable file export. Enabled by default in `docker` and `docker-test` profiles. |
| `ezkey.admin.bootstrap.export.path` | `String` | `/var/lib/ezkey/bootstrap/bootstrap-credentials.json` | optionnel | Absolute path for the JSON export file. Directory must exist and be writable. |

---

### 10. Trusted Proxies (`ezkey.trusted-proxies.*`)

**Description:** list of trusted proxy CIDR ranges. Client IP extraction for rate limiting and
audit uses `X-Forwarded-For` / `X-Real-IP` / `CF-Connecting-IP` headers **only** when the
direct TCP connection originates from an IP in this list.

**Defined in:** `TrustedProxyProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.trusted-proxies.required` | `boolean` | `false` | optionnel [prod] | When `true`, fail startup if `cidrs` is empty or invalid (SEC-011). Set via **`EZKEY_TRUSTED_PROXIES_REQUIRED=true`** on EXP1 / `--with-proxy` (not on direct-port clean-start). |
| `ezkey.trusted-proxies.cidrs` | `List<String>` | *(empty)* | optionnel | CIDR ranges of trusted reverse proxies (e.g. `10.0.0.0/8`, `172.16.0.0/12`). In Docker, set comma-separated **`EZKEY_TRUSTED_PROXIES_CIDRS`**. |

**YAML example:**

```yaml
ezkey:
  trusted-proxies:
    cidrs:
      - 10.0.0.0/8
      - 172.16.0.0/12
      - 192.168.1.5/32
```

---

### 11. Admin CORS — browser cross-origin (`ezkey.admin.cors.*`)

**Description:** when the Admin UI is served from a **different origin** than the Admin API (for
example static hosting on Cloudflare Pages calling `https://exp1-admin-api.ezkey.org`), browsers
enforce CORS. If `allowed-origins` is **empty**, the API does **not** emit CORS response headers
(unchanged behavior for same-origin setups such as the Admin UI behind Caddy in Docker).

**Defined in:** `AdminCorsProperties`, `AdminCorsConfig`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.cors.allowed-origins` | `List<String>` | *(empty)* | optionnel | Exact browser origins (scheme + host + port), e.g. `https://my-app.pages.dev`. Comma-separated in env. |
| `ezkey.admin.cors.allowed-methods` | `List<String>` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` | optionnel | Methods allowed for CORS. |
| `ezkey.admin.cors.allowed-headers` | `List<String>` | `Authorization`, `Content-Type`, `Accept`, `Origin`, `X-CSRF-TOKEN`, `Access-Control-Request-Method`, `Access-Control-Request-Headers` | optionnel | Request headers the browser may send on cross-origin requests. |
| `ezkey.admin.cors.allow-credentials` | `boolean` | `false` | optionnel | Set `true` only if you use credentialed requests (e.g. cookies); requires explicit origins (not `*`). |

**Split deployment notes:**

- List each production UI origin (and preview URLs if you allow them); wildcard `*` is not used for origins.
- The Admin UI still needs an API base URL (e.g. `VITE_API_BASE_URL`); **CSP** `connect-src` on the edge must allow the API origin separately from CORS.

---

### 12. Browser HttpOnly session cookie

**Prefix:** `ezkey.admin.auth.*`

**Description:** optional mode for **browser** sessions when the Admin UI and Admin API are on different **HTTPS** origins (e.g. `https://exp1-admin-ui.ezkey.org` → `https://exp1-admin-api.ezkey.org`). When enabled, successful login and passwordless-wait responses set an **HttpOnly** cookie on the API host with the same opaque value as today’s bearer token; the JSON body **omits** `token` so JavaScript cannot read the secret. The authentication filter accepts **either** `Authorization: Bearer` (priority if present) **or** the session cookie. **Postman and scripts** can keep using Bearer only.

**Defined in:** `AdminBrowserSessionCookieProperties`, `AdminBrowserSessionCookieConfig`, `AdminSessionCookieService`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.auth.browser-session-cookie-enabled` | `boolean` | `false` | optionnel | Enable HttpOnly cookie + strip token from login JSON. |
| `ezkey.admin.auth.browser-session-cookie-name` | `String` | `EZKEY_ADMIN_SESSION` | optionnel | Cookie name (host-only on API). |
| `ezkey.admin.auth.browser-session-cookie-secure` | `boolean` | `true` | optionnel | `Secure` flag; set `false` only for special local TLS tests. |
| `ezkey.admin.auth.browser-session-cookie-same-site` | `String` | `Strict` | optionnel | SameSite policy for Admin browser session and CSRF cookies. Prefer `Strict` for production split deployments. |
| `ezkey.admin.auth.browser-csrf-cookie-name` | `String` | `EZKEY_ADMIN_CSRF` | optionnel | Readable non-secret CSRF cookie name used by the Admin UI in cookie mode. |
| `ezkey.admin.auth.browser-csrf-header-name` | `String` | `X-CSRF-TOKEN` | optionnel | Header name required on unsafe cookie-authenticated browser requests. |

**Operational pairing:** set `ezkey.admin.cors.allow-credentials=true` and explicit `allowed-origins` for the UI. Build the Admin UI with `VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE=true` and `fetch` credentials (see [docs/admin-ui-security.md](../docs/admin-ui-security.md)). If you override the CSRF cookie or header names, mirror them in the Admin UI build variables `VITE_ADMIN_AUTH_CSRF_COOKIE_NAME` and `VITE_ADMIN_AUTH_CSRF_HEADER_NAME`.

**Session rehydration and CSRF:** `GET /api/v1/admin/auth/me` returns non-secret session metadata for a valid cookie or Bearer session. In browser cookie mode, login/passwordless-wait and `/me` also issue a signed double-submit CSRF token. The Admin UI sends that token in `X-CSRF-TOKEN` for unsafe methods (`POST`, `PUT`, `PATCH`, `DELETE`). CSRF validation applies only to requests authenticated by the browser session cookie; explicit Bearer requests remain compatible for tools and recovery flows.

**Cookie `Max-Age` vs token in the database:** On successful login or passwordless-wait, `Set-Cookie` uses a `Max-Age` derived from the response **`expiresAt`** (same instant as for Bearer mode). That initial window comes from **`ezkey.admin.token.expiration-hours`** (see §9 — sliding expiration also **extends the token row** on each validated request). The HttpOnly cookie is **not** re-issued on every API call today, so the browser’s cookie lifetime stays tied to **`expiresAt` at authentication success**. If the cookie expires, the browser stops sending it even though the server might still have considered an extended token valid in edge cases — operators usually fix perceived “short sessions” by increasing **`expiration-hours`** or by planning a future enhancement to refresh `Set-Cookie` when the token slides.

**Concrete example (default `expiration-hours=2`):** Suppose login succeeds at **14:00** UTC. The API creates a token with **`expiresAt` = 16:00** UTC. The `Set-Cookie` header sets **`Max-Age`** to the number of seconds from 14:00 to 16:00 (7200 seconds). The browser keeps sending that cookie on API requests until about **16:00** — then the cookie is gone and the next call behaves as **unauthenticated** unless the user logs in again. If you change **`ezkey.admin.token.expiration-hours`** to `8`, the same login at 14:00 would yield **`expiresAt`** 22:00 and a longer **`Max-Age`** (~8 hours) for that cookie.

---

### 13. Auth-attempt TTL persistence (`ezkey.auth-attempt.expiry-scheduler.*`)

**Prefix:** `ezkey.auth-attempt.expiry-scheduler.*`

**Description:** scheduled job (runs in the **Admin API** process) that bulk-updates `PENDING` /
`READ` auth attempts whose `expires_at` has passed to terminal **`EXPIRED`**, and emits
`AUTH_ATTEMPT_EXPIRED` audit (`event_action` `auth_attempt_expired_scheduler`). Distinct from
explicit cancel (`AUTH_ATTEMPT_CANCELLED`). Device clients do not cancel attempts — Approve/Deny
only; ignore/close relies on this scheduler. See `docs/ENDPOINT.md` and
`AuthAttemptExpiryScheduler`.

**Defined in:** `org.ezkey.authattempt.service.AuthAttemptExpiryScheduler` (`@Value` / `@ConditionalOnProperty`)

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.auth-attempt.expiry-scheduler.enabled` | `boolean` | `true` | optionnel | Enable the TTL persistence job. |
| `ezkey.auth-attempt.expiry-scheduler.fixed-delay-ms` | `long` | `60000` | optionnel | Delay between job runs (ms). |
| `ezkey.auth-attempt.expiry-scheduler.initial-delay-ms` | `long` | `60000` | optionnel | Delay before the first run (ms). |

---

## Shared Properties (ezkey-core)

The following ezkey-core prefixes are also active in Admin API. See
[ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md) for full documentation.

| Prefix | Active in Admin API | Notes |
|---|---|---|
| `ezkey.encryption.*` | ✓ | Rotation and re-encryption jobs run **only** in Admin API. |
| `ezkey.audit.integrity.*` | ✓ | HMAC signing of all audit events. |
| `ezkey.audit.chain.*` | ✓ | Chain checkpoint job runs **only** in Admin API. **`window-minutes`** must stay aligned with Auth API and Integration API profiles — peripherals use it for heartbeat math even though they disable `enabled`. |
| `ezkey.organization.*` | ✓ | Exposed via `GET /api/v1/public/instance-info`. |
| `ezkey.qr.*` | ✓ | Auth URL embedded in enrollment QR codes. |
| `ezkey.enrollment.*` | ✓ | Enrollment expiration and cleanup job. |
| `ezkey.core.*` | ✓ | Crypto algorithm params and auth-attempt settings. |

---

## Profile Matrix

| Property | default | docker | docker-test |
|---|---|---|---|
| `ezkey.admin.mfa.mode` | `dev` | `prod` | `prod` |
| `ezkey.admin.mfa.bootstrap.auto-enrollment` | `true` | `true` | `true` |
| `ezkey.admin.mfa.bootstrap.credentials-output-mode` | `FULL` | `${EZKEY_ADMIN_MFA_BOOTSTRAP_CREDENTIALS_OUTPUT_MODE:full}` | `${EZKEY_ADMIN_MFA_BOOTSTRAP_CREDENTIALS_OUTPUT_MODE:full}` |
| `ezkey.admin.rate-limit.enabled` | `true` | `true` | `false` |
| `ezkey.admin-operations.rate-limit.enabled` | `true` | `true` | `false` |
| `ezkey.admin.bootstrap.export.enabled` | `false` | `true` | `true` |
| `ezkey.admin.token.expiration-hours` | `2` | `2` | `2` |
| `ezkey.audit.chain.window-minutes` | `5` | `5` | `5` |

---

## Docker Environment Variable Reference

| Spring property | Docker env var | docker-compose default |
|---|---|---|
| `ezkey.admin.initial.username` | `EZKEY_ADMIN_INITIAL_USERNAME` | `admin.docker` |
| `ezkey.admin.initial.email` | `EZKEY_ADMIN_INITIAL_EMAIL` | `admin@ezkey.local` |
| `ezkey.admin.initial.first-name` | `EZKEY_ADMIN_INITIAL_FIRST_NAME` | `Admin` |
| `ezkey.admin.initial.last-name` | `EZKEY_ADMIN_INITIAL_LAST_NAME` | `Docker` |
| `ezkey.admin.mfa.bootstrap.credentials-output-mode` | `EZKEY_ADMIN_MFA_BOOTSTRAP_CREDENTIALS_OUTPUT_MODE` | `full` |
| `ezkey.organization.about-url` | `EZKEY_ORGANIZATION_ABOUT_URL` | *(empty)* |
| `ezkey.qr.auth-base-url` | `EZKEY_QR_AUTH_BASE_URL` | *(empty)* |
| `ezkey.audit.integrity.instance-id` | `EZKEY_INSTANCE_ID` | `admin-api` |
| `ezkey.trusted-proxies.cidrs` | `EZKEY_TRUSTED_PROXIES_CIDRS` | *(see compose; comma-separated CIDRs)* |
| `ezkey.trusted-proxies.required` | `EZKEY_TRUSTED_PROXIES_REQUIRED` | `false` (direct ports); `true` on Lightsail / `--with-proxy` |
| `ezkey.admin.cors.allowed-origins` | `EZKEY_ADMIN_CORS_ALLOWED_ORIGINS` | *(unset)* |
| `ezkey.admin.cors.allow-credentials` | `EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS` | *(unset)* |
| `ezkey.admin.auth.browser-session-cookie-enabled` | `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED` | *(unset)* |
| `ezkey.admin.auth.browser-session-cookie-name` | `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_NAME` | *(unset)* |
| `ezkey.admin.auth.browser-session-cookie-secure` | `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_SECURE` | *(unset)* |
| `ezkey.admin.auth.browser-session-cookie-same-site` | `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_SAME_SITE` | *(unset)* |
| `ezkey.admin.auth.browser-csrf-cookie-name` | `EZKEY_ADMIN_AUTH_BROWSER_CSRF_COOKIE_NAME` | *(unset)* |
| `ezkey.admin.auth.browser-csrf-header-name` | `EZKEY_ADMIN_AUTH_BROWSER_CSRF_HEADER_NAME` | *(unset)* |

---

### 12. Evaluator self-registration (`ezkey.evaluator.self-registration.*`)

**Description:** anonymous EXP1 preview signup — empty tenant + pending Tenant Admin + activation
code. Disabled by default; enable only on experimental preview installations.

**Defined in:** `EvaluatorSelfRegistrationProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.evaluator.self-registration.enabled` | `boolean` | `false` | optionnel | When `false`, `POST /api/v1/public/evaluator-signup` returns HTTP 404. |
| `ezkey.evaluator.self-registration.daily-cap` | `int` | `5` | optionnel | Max successful signups per UTC day (global). |
| `ezkey.evaluator.self-registration.per-ip-window-hours` | `int` | `24` | optionnel | Per-IP success window. |
| `ezkey.evaluator.self-registration.per-ip-max-success` | `int` | `1` | optionnel | Max successful signups per IP within the window. |
| `ezkey.evaluator.self-registration.admin-ui-url` | `String` | `https://exp1-admin-ui.ezkey.org` | optionnel | Returned to clients after signup. |
| `ezkey.evaluator.self-registration.guided-tour-url` | `String` | `https://ezkey.org/exp1-guided-tour.html` | optionnel | Returned to clients after signup. |

**EXP1 operator notes:**

- Set `ezkey.evaluator.self-registration.enabled=true` on the preview Admin API only.
- Add `https://ezkey.org` (and Cloudflare Pages preview origins if needed) to `ezkey.admin.cors.allowed-origins`.
- Signup page: `https://ezkey.org/exp1-signup.html` (calls the Admin API cross-origin).

---

## Security Checklist (Production)

1. Set `ezkey.admin.initial.username` to a real individual's identity — not `admin`.
2. Set `ezkey.admin.mfa.mode=prod` to enforce MFA for all admin logins.
3. Set `ezkey.admin.mfa.bootstrap.auto-enrollment=false` or `credentials-output-mode=RECOVERY_PRIMARY`.
4. Set `ezkey.admin.bootstrap.export.enabled=false` unless Docker clean-start automation is needed.
5. Configure `ezkey.trusted-proxies.cidrs` when Admin API is behind a reverse proxy.
6. For split UI/API hosting, set `ezkey.admin.cors.allowed-origins` to the Admin UI origin(s); align CSP `connect-src` on the static host.
7. If using the HttpOnly browser session cookie, set `ezkey.admin.auth.browser-session-cookie-enabled=true`, `ezkey.admin.cors.allow-credentials=true`, keep `SameSite=Strict` unless a documented deployment requires otherwise, and deploy a matching Admin UI build (`VITE_ADMIN_AUTH_USE_HTTP_ONLY_SESSION_COOKIE=true`).
8. Configure `ezkey.encryption.master-key-file` and `ezkey.audit.integrity.hmac-key-file` via volume mounts.

---

## JavaMelody (opt-in Docker diagnostics)

JavaMelody is **disabled by default** (`javamelody.enabled=false`). Enable it only via
`./ezkey-tests/clean-start.sh --with-java-melody` (combinable with `--ha`). Reports are on the
management port at `/actuator/monitoring`; the collector UI is `http://localhost:8088`. Crypto API
is out of scope. Operator details: [`docker/README.md`](../docker/README.md) § JavaMelody collector.
