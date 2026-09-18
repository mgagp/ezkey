# Ezkey Configuration Index

Central registry for all `ezkey.*` configuration properties across the backend modules.

---

## Module Documentation

| Module | CONFIGURATION.md | Role |
|---|---|---|
| `ezkey-core` | [ezkey-core/CONFIGURATION.md](../../ezkey-core/CONFIGURATION.md) | Shared library — defines all `ezkey.encryption.*`, `ezkey.audit.*`, `ezkey.organization.*`, `ezkey.qr.*`, `ezkey.enrollment.*`, and `ezkey.core.*` prefixes |
| `ezkey-admin-api` | [ezkey-admin-api/CONFIGURATION.md](../../ezkey-admin-api/CONFIGURATION.md) | Defines `ezkey.admin.*` (including `ezkey.admin.cors.*`, `ezkey.admin.auth.*`), `ezkey.admin-operations.*`, `ezkey.security.admin.*`, `ezkey.trusted-proxies.*`, `ezkey.auth-attempt.expiry-scheduler.*` |
| `ezkey-auth-api` | [ezkey-auth-api/CONFIGURATION.md](../../ezkey-auth-api/CONFIGURATION.md) | Defines `ezkey.rate-limit.*`, `ezkey.trusted-proxies.*`; inherits core prefixes |
| `ezkey-integration-api` | [ezkey-integration-api/CONFIGURATION.md](../../ezkey-integration-api/CONFIGURATION.md) | Defines `ezkey.api-key.rate-limit.*`, `ezkey.trusted-proxies.*`; inherits core prefixes |
| `ezkey-crypto-api` | [ezkey-crypto-api/CONFIGURATION.md](../../ezkey-crypto-api/CONFIGURATION.md) | No database; inherits `ezkey.encryption.*` from core |

---

## Complete `ezkey.*` Prefix Registry

| Prefix | Defined in | Used by | Documentation |
|---|---|---|---|
| `ezkey.admin.initial` | `ezkey-admin-api` | admin-api | [admin-api §1](../../ezkey-admin-api/CONFIGURATION.md#1-bootstrap-initial-global-admin-ezkeyadmininitial) |
| `ezkey.admin.mfa` | `ezkey-admin-api` | admin-api | [admin-api §2](../../ezkey-admin-api/CONFIGURATION.md#2-admin-mfa-ezkeyadminmfa) |
| `ezkey.admin.token` | `ezkey-admin-api` | admin-api | [admin-api §3](../../ezkey-admin-api/CONFIGURATION.md#3-admin-tokens-ezkeyadmintoken) |
| `ezkey.admin.token.cleanup` | `ezkey-admin-api` | admin-api | [admin-api §3](../../ezkey-admin-api/CONFIGURATION.md#3-admin-tokens-ezkeyadmintoken) |
| `ezkey.admin.recovery` | `ezkey-admin-api` | admin-api | [admin-api §4](../../ezkey-admin-api/CONFIGURATION.md#4-recovery-codes-ezkeyadminrecovery) |
| `ezkey.admin.rate-limit` | `ezkey-admin-api` | admin-api | [admin-api §5](../../ezkey-admin-api/CONFIGURATION.md#5-admin-login-rate-limiting-ezkeyadminrate-limit) |
| `ezkey.admin-operations.rate-limit` | `ezkey-admin-api` | admin-api | [admin-api §6](../../ezkey-admin-api/CONFIGURATION.md#6-admin-operations-rate-limiting-ezkeyadmin-operationsrate-limit) |
| `ezkey.api-key.rate-limit` | `ezkey-admin-api` & `ezkey-integration-api` | admin-api, integration-api | [admin-api §7](../../ezkey-admin-api/CONFIGURATION.md#7-api-key-usage-rate-limiting-ezkeyapi-keyrate-limit) · [integration-api §1](../../ezkey-integration-api/CONFIGURATION.md#1-api-key-usage-rate-limiting-ezkeyapi-keyrate-limit) |
| `ezkey.security.admin` | `ezkey-admin-api` | admin-api | [admin-api §8](../../ezkey-admin-api/CONFIGURATION.md#8-admin-security-limits-ezkeysecurityadmin) |
| `ezkey.admin.bootstrap.export` | `ezkey-admin-api` | admin-api | [admin-api §9](../../ezkey-admin-api/CONFIGURATION.md#9-bootstrap-credentials-export-ezkeyadminbootstrapexport) |
| `ezkey.admin.cors` | `ezkey-admin-api` | admin-api | [admin-api §11](../../ezkey-admin-api/CONFIGURATION.md#11-admin-cors--browser-cross-origin-ezkeyadmincors) |
| `ezkey.admin.auth` | `ezkey-admin-api` | admin-api | [admin-api §12](../../ezkey-admin-api/CONFIGURATION.md#12-browser-httponly-session-cookie) |
| `ezkey.auth-attempt.expiry-scheduler` | `ezkey-admin-api` (scheduler bean in core) | admin-api | [admin-api §13](../../ezkey-admin-api/CONFIGURATION.md#13-auth-attempt-ttl-persistence-ezkeyauth-attemptexpiry-scheduler) |
| `ezkey.trusted-proxies` | `ezkey-admin-api` (copy ×3) | admin-api, auth-api, integration-api | [admin-api §10](../../ezkey-admin-api/CONFIGURATION.md#10-trusted-proxies-ezkeytrusted-proxies) · [auth-api §2](../../ezkey-auth-api/CONFIGURATION.md#2-trusted-proxies-ezkeytrusted-proxies) · [integration-api §2](../../ezkey-integration-api/CONFIGURATION.md#2-trusted-proxies-ezkeytrusted-proxies) |
| `ezkey.rate-limit` | `ezkey-auth-api` | auth-api | [auth-api §1](../../ezkey-auth-api/CONFIGURATION.md#1-auth-api-rate-limiting-ezkeyrate-limit) |
| `ezkey.encryption` | `ezkey-core` (`TinkProperties`) | admin-api, auth-api, integration-api, crypto-api | [core §encryption](../../ezkey-core/CONFIGURATION.md#encryption-at-rest-ezkeyencryption) |
| `ezkey.qr` | `ezkey-core` (`QrCodeProperties`) | admin-api, auth-api | [core §qr](../../ezkey-core/CONFIGURATION.md#qr-code-ezkeyqr) |
| `ezkey.organization` | `ezkey-core` (`OrganizationProperties`) | admin-api, auth-api | [core §org](../../ezkey-core/CONFIGURATION.md#organization-ezkeyorganization) |
| `ezkey.core` | `ezkey-core` (`EzkeyCoreProperties`) | admin-api, auth-api | [core §core](../../ezkey-core/CONFIGURATION.md#core-crypto--auth-attempt-ezkeycore) |
| `ezkey.enrollment` | `ezkey-core` (`EnrollmentProperties`) | admin-api | [core §enrollment](../../ezkey-core/CONFIGURATION.md#enrollment-ezkeyenrollment) |
| `ezkey.audit.integrity` | `ezkey-core` (`AuditHmacProperties`) | admin-api, auth-api, integration-api | [core §audit-integrity](../../ezkey-core/CONFIGURATION.md#audit-log-integrity-ezkeyauditintegrity) |
| `ezkey.audit.chain` | `ezkey-core` (`AuditChainProperties`) | admin-api | [core §audit-chain](../../ezkey-core/CONFIGURATION.md#audit-log-chain-ezkeyauditchain) |
| `ezkey.audit.chain.heartbeat` | `ezkey-core` (`AuditChainHeartbeatProperties`) | admin-api, auth-api, integration-api | [core §heartbeat](../../ezkey-core/CONFIGURATION.md#peripheral-heartbeat-ezkeyauditchainheartbeat) |
| `ezkey.audit.archive` | `ezkey-core` (`AuditArchiveProperties`) | admin-api | [core §audit-archive](../../ezkey-core/CONFIGURATION.md#audit-log-archive-ezkeyauditarchive) |

---

## Module × Prefix Matrix

> ✓ = prefix used in this module · — = not applicable

| Prefix | admin-api | auth-api | integration-api | crypto-api |
|---|---|---|---|---|
| `ezkey.admin.*` | ✓ | — | — | — |
| `ezkey.admin-operations.*` | ✓ | — | — | — |
| `ezkey.api-key.rate-limit.*` | ✓ | — | ✓ | — |
| `ezkey.security.admin.*` | ✓ | — | — | — |
| `ezkey.rate-limit.*` | — | ✓ | — | — |
| `ezkey.trusted-proxies.*` | ✓ | ✓ | ✓ | — |
| `ezkey.encryption.*` | ✓ | ✓ | ✓ | ✓ |
| `ezkey.audit.integrity.*` | ✓ | ✓ | ✓ | — |
| `ezkey.audit.chain.*` | ✓ | ✓¹ | ✓¹ | — |
| `ezkey.audit.chain.heartbeat.*` | ✓ | ✓ | ✓ | — |
| `ezkey.audit.archive.*` | ✓ | — | — | — |
| `ezkey.organization.*` | ✓ | ✓ | — | — |
| `ezkey.qr.*` | ✓ | ✓ | — | — |
| `ezkey.enrollment.*` | ✓ | — | — | — |
| `ezkey.core.*` | ✓ | ✓ | — | — |

¹ Auth API and Integration API set `ezkey.audit.chain.enabled=false` (no scheduler) but still bind `AuditChainProperties`; **`ezkey.audit.chain.window-minutes` must match Admin API** wherever heartbeat supervision runs — Docker profiles declare it explicitly on all three services.

---

## Obligation Legend

| Level | Meaning |
|---|---|
| `requis` | Application will not start correctly without this property. |
| `requis [docker]` | Has a dev stub or safe default; **must** be explicitly set for any real deployment. |
| `optionnel` | Production-viable default; override only for fine-tuning. |

---

## Key Docker Environment Variables

| Env var | Spring property | Set in |
|---|---|---|
| `EZKEY_ADMIN_INITIAL_USERNAME` | `ezkey.admin.initial.username` | admin-api docker profile |
| `EZKEY_ADMIN_INITIAL_EMAIL` | `ezkey.admin.initial.email` | admin-api docker profile |
| `EZKEY_ADMIN_INITIAL_FIRST_NAME` | `ezkey.admin.initial.first-name` | admin-api docker profile |
| `EZKEY_ADMIN_INITIAL_LAST_NAME` | `ezkey.admin.initial.last-name` | admin-api docker profile |
| `EZKEY_QR_AUTH_BASE_URL` | `ezkey.qr.auth-base-url` | admin-api and auth-api docker profile |
| `EZKEY_ORGANIZATION_NAME` | `ezkey.organization.name` | admin-api and auth-api docker profile |
| `EZKEY_ORGANIZATION_DESCRIPTION` | `ezkey.organization.description` | admin-api and auth-api docker profile |
| `EZKEY_ORGANIZATION_ABOUT_URL` | `ezkey.organization.about-url` | admin-api and auth-api docker profile |
| `EZKEY_RUNTIME_PROFILE` | *(ops key; `eval` appends Spring profile `docker-eval`)* | clean-start / `docker/start.sh` / Lightsail `.env` — [docker/README.md](../../docker/README.md) § Runtime profiles |
| `EZKEY_ADMIN_MFA_BOOTSTRAP_CREDENTIALS_OUTPUT_MODE` | `ezkey.admin.mfa.bootstrap.credentials-output-mode` | admin-api docker profile |
| `EZKEY_ADMIN_CORS_ALLOWED_ORIGINS` | `ezkey.admin.cors.allowed-origins` | admin-api (split UI/API only; unset for clean-start) |
| `EZKEY_ADMIN_CORS_ALLOW_CREDENTIALS` | `ezkey.admin.cors.allow-credentials` | admin-api (with HttpOnly cookie + credentialed `fetch`) |
| `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_ENABLED` | `ezkey.admin.auth.browser-session-cookie-enabled` | admin-api (split HTTPS UI/API optional) |
| `EZKEY_ADMIN_AUTH_BROWSER_SESSION_COOKIE_SAME_SITE` | `ezkey.admin.auth.browser-session-cookie-same-site` | admin-api (optional; default `Strict`) |
| `EZKEY_ADMIN_AUTH_BROWSER_CSRF_COOKIE_NAME` | `ezkey.admin.auth.browser-csrf-cookie-name` | admin-api (optional; mirror in Admin UI build if changed) |
| `EZKEY_ADMIN_AUTH_BROWSER_CSRF_HEADER_NAME` | `ezkey.admin.auth.browser-csrf-header-name` | admin-api (optional; mirror in Admin UI build if changed) |
| `EZKEY_INSTANCE_ID` | `ezkey.audit.integrity.instance-id` | all API modules |

---

## Related Documentation

- [docs/ARCHITECTURE.md](../ARCHITECTURE.md) — system architecture overview
- [docs/API_KEYS_GUIDE.md](../API_KEYS_GUIDE.md) — API key usage guide
- [docker/README.md](../../docker/README.md) — Docker deployment instructions
