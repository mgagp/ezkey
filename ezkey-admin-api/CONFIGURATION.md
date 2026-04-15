# Configuration Reference — ezkey-admin-api

Admin API is the management plane for Ezkey: initial bootstrap, admin authentication (with MFA),
tenant and integration management, enrollment lifecycle, and audit log chain. It runs the
**only** scheduled jobs in the cluster (key rotation, re-encryption, audit chain, token cleanup).

> **Shared properties** (encryption, audit integrity/chain, organization, QR, enrollment, core)
> are documented in [ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md).

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
| `ezkey.api-key.rate-limit.enabled` | — | `true` | optionnel |
| `ezkey.security.admin.max-global-admins` | — | `3` | optionnel |
| `ezkey.admin.bootstrap.export.enabled` | — | `false` | optionnel |
| `ezkey.trusted-proxies.cidrs` | — | *(empty list)* | optionnel |

---

## Properties by Functional Group

### 1. Bootstrap — Initial Global Admin (`ezkey.admin.initial.*`)

**Description:** seeds the first Global Admin account on first startup. The system bootstraps
only if no Global Admin exists yet. SOC 2 compliance requires a real individual's identity
(not `admin` or `root`).

**Defined in:** `InitialGlobalAdminProperties`

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.admin.initial.username` | `String` | *(none)* | requis | Username for the initial Global Admin. Must identify an individual. Forbidden values: `admin`, `administrator`, `root`, `superuser`, `super`, `user`, `test`, `demo`. |
| `ezkey.admin.initial.email` | `String` | *(none)* | requis | Email for the initial Global Admin (SOC 2 CC6.1, CC7.2). Must be valid format. |
| `ezkey.admin.initial.first-name` | `String` | *(none)* | requis | First name (SOC 2 CC6.1, CC7.2). |
| `ezkey.admin.initial.last-name` | `String` | *(none)* | requis | Last name (SOC 2 CC6.1, CC7.2). |

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

**`docker` profile values:**

```properties
ezkey.admin.rate-limit.enabled=true
ezkey.admin.rate-limit.login.requests=5
ezkey.admin.rate-limit.login.window-minutes=1
ezkey.admin.rate-limit.login.key-strategy=client-ip
ezkey.admin.rate-limit.login.block-after-failures=10
ezkey.admin.rate-limit.login.block-duration-minutes=30
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

### 7. API Key Usage Rate Limiting (`ezkey.api-key.rate-limit.*`)

**Description:** rate limiting for Integration API key operations when called through the Admin
API. Keyed by integration ID (API key bearer).

**Defined in:** `ApiKeyRateLimitProperties`

> The same prefix (`ezkey.api-key.rate-limit.*`) is also used by [ezkey-integration-api](../ezkey-integration-api/CONFIGURATION.md).

| Property | Type | Default | Obligation | Description |
|---|---|---|---|---|
| `ezkey.api-key.rate-limit.enabled` | `boolean` | `true` | optionnel | Global on/off. |
| `ezkey.api-key.rate-limit.create-auth-attempt.requests` | `int` | `100` | optionnel | Max `POST /auth-attempts` requests per window per API key. Docker uses `10`. |
| `ezkey.api-key.rate-limit.create-auth-attempt.window-minutes` | `int` | `15` | optionnel | Window for create-auth-attempt (minutes). Docker uses `1`. |
| `ezkey.api-key.rate-limit.wait-auth-attempt.requests` | `int` | `200` | optionnel | Max `GET /auth-attempts/{id}/wait` requests per window. Docker uses `20`. |
| `ezkey.api-key.rate-limit.wait-auth-attempt.window-minutes` | `int` | `15` | optionnel | Window for wait-auth-attempt (minutes). Docker uses `1`. |

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
| `ezkey.trusted-proxies.cidrs` | `List<String>` | *(empty)* | optionnel | CIDR ranges of trusted reverse proxies (e.g. `10.0.0.0/8`, `172.16.0.0/12`). |

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

## Shared Properties (ezkey-core)

The following ezkey-core prefixes are also active in Admin API. See
[ezkey-core/CONFIGURATION.md](../ezkey-core/CONFIGURATION.md) for full documentation.

| Prefix | Active in Admin API | Notes |
|---|---|---|
| `ezkey.encryption.*` | ✓ | Rotation and re-encryption jobs run **only** in Admin API. |
| `ezkey.audit.integrity.*` | ✓ | HMAC signing of all audit events. |
| `ezkey.audit.chain.*` | ✓ | Chain checkpoint job runs **only** in Admin API. |
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
| `ezkey.api-key.rate-limit.enabled` | `true` | `true` | `false` |
| `ezkey.admin-operations.rate-limit.enabled` | `true` | `true` | `false` |
| `ezkey.admin.bootstrap.export.enabled` | `false` | `true` | `true` |
| `ezkey.admin.token.expiration-hours` | `2` | `2` | `2` |

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

---

## Security Checklist (Production)

1. Set `ezkey.admin.initial.username` to a real individual's identity — not `admin`.
2. Set `ezkey.admin.mfa.mode=prod` to enforce MFA for all admin logins.
3. Set `ezkey.admin.mfa.bootstrap.auto-enrollment=false` or `credentials-output-mode=RECOVERY_PRIMARY`.
4. Set `ezkey.admin.bootstrap.export.enabled=false` unless Docker clean-start automation is needed.
5. Configure `ezkey.trusted-proxies.cidrs` when Admin API is behind a reverse proxy.
6. Configure `ezkey.encryption.master-key-file` and `ezkey.audit.integrity.hmac-key-file` via volume mounts.
