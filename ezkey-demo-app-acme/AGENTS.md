# Ezkey Demo App ACME - Agent Notes

## Purpose

Demo application for Ezkey passwordless login. Demonstrates backend-side authentication using API key (machine-to-machine) against the **Integration API** via the Ezkey Java SDK (`EzkeyClient`).

## Key Files

| File | Description |
|------|-------------|
| `controller/HomeController.java` | Routes: `/dashboard`, `/logout` |
| `controller/LoginController.java` | Handles POST `/login`, coordinates auth flow |
| `controller/AccessCodeController.java` | `GET /t/{code}` temporary evaluator entry |
| `config/EzkeyClientProvider.java` | Supplies EzkeyClient from current credentials (config or runtime override) |
| `service/DemoApiKeyConfigService.java` | Per-`HttpSession` API key credentials (not JVM-global); "Apply API Key" override |
| `service/AccessCodeService.java` | Access-code slots from `ezkey.access-codes.*` (startup fail-fast, digest lookup) |
| `config/EzkeyClientConfig.java` | Spring configuration for AcmeProperties |
| `config/AcmeProperties.java` | Type-safe configuration properties |
| `templates/login.html` | Login page with form POST, "Configure API Key" dialog |
| `templates/dashboard.html` | Post-login dashboard with user info |

## Architecture

- **Mode**: Backend-side (server calls Integration API via Ezkey SDK; base URL `ezkey.admin-api-url`)
- **Port**: 8082 (management/Actuator on **8086**, health only — **no** `/refresh` for config)
- **Auth**: HTTP session (server-side). Logout **invalidates** the session (clears auth, pasted keys,
  and access-code slot).
- **API Key**: Resolution order — access-code slot → pasted session keys → legacy single config —
  via `DemoApiKeyConfigService` + `AccessCodeService` + `EzkeyClientProvider`. Temporary access
  links use `GET /t/{code}`. Do not store a process-wide key.
- **Username**: `createAuthAttemptByUserIdentifier` — no local `UserMappingService`.
- **Internet / community host:** `experimental-hybrid/` compose; community container name
  `ezkey-exp-demo-app-acme`. Local Docker compose uses `ezkey-demo-app-acme`. Do not fold
  Lightsail-only facts into `docs/` or `docker/README.md` beyond the linked runbooks.

## Login Flow

1. User enters username on `/login` (form POST) — or arrives via `/t/{code}` then `/login`
2. Backend calls `POST /api/v1/auth-attempts` via SDK `createAuthAttemptByUserIdentifier`
3. Backend polls auth-attempt status until approved / rejected / expired
4. On approval, create HTTP session with `AuthenticatedUser`
5. Redirect to `/dashboard` (protected route)

## Configuration

### External Configuration Override

Spring Boot loads external config that overrides JAR-internal properties:

**Docker**: Place `application.properties` in `/app/config/` (mounted volume `demo-app-acme-config`)
**Local**: Place `application.properties` in `./config/` directory next to the JAR

**There is no Actuator `/refresh` hot-reload for these properties.** Changes to
`/app/config/application.properties` (including `ezkey.access-codes.*`) require a **container
restart**. Entrypoint applies `chmod 600` on the config file every start (owner `spring`).

Community add/revoke procedure:
[`docs/lightsail/acme-access-code-runbook.md`](../docs/lightsail/acme-access-code-runbook.md).

### Configuration Properties

```properties
# Ezkey SDK base URL — Integration API (Docker internal hostname). Env override:
# EZKEY_ADMIN_API_URL=http://integration-api:7080  (legacy variable name).
ezkey.admin-api-url=http://integration-api:7080  # Docker
ezkey.admin-api-url=http://localhost:7080          # IDE — Integration API on localhost:7080

# API Key credentials for Integration API (machine-to-machine) authentication
# Format: Authorization: Basic base64(integrationKey:secretKey)
ezkey.integration-key=${EZKEY_INTEGRATION_KEY:}
ezkey.secret-key=${EZKEY_SECRET_KEY:}

# Optional evaluator slots (restart required) — see CONFIGURATION.md
# ezkey.access-codes.<slot>.code=
# ezkey.access-codes.<slot>.integration-key=
# ezkey.access-codes.<slot>.secret-key=
# ezkey.access-codes.<slot>.label=
```

### What reloads without restart

1. **API Key (runtime, session only)**:
   - "Configure API Key" on the login page → credentials in the current HTTP session only
2. **Access-code slots / persisted keys / admin-api-url**:
   - Edit `/app/config/application.properties` → **restart** container
   - Local: `docker restart ezkey-demo-app-acme`
   - Community: `docker restart ezkey-exp-demo-app-acme`

## Watchouts

- **API Key Credentials Required**: via env, config file, access-code slot, or session dialog
- **Challenge Code**: 2 digits for auth attempts (not 6 - that's enrollment)
- **Business Approval:** when Integration API returns `authAttemptChallenge`, show the dashboard
  `biz-challenge-*` strip. Do not hide a required challenge.
- **DOM XSS (business status):** `showRowStatus` / challenge strip use `textContent` via
  [`static/js/biz-status.mjs`](src/main/resources/static/js/biz-status.mjs) — never interpolate
  `approverIdentifier` (or other caller-controlled strings) into `innerHTML` (Lot B / CodeQL #1,
  2026-10-13). Runtime proof: `node --test ezkey-demo-app-acme/src/test/js/biz-status.test.mjs`.
- **Session Security**: Dashboard route validates session server-side
- **Link-lost `/t` pages**: must not mint a session cookie (CSRF meta omitted when `showLoginForm`
  is false)
- **Do not invent Actuator `/refresh`** for Acme config — it is not exposed and not the operator path

## Common Tasks

### Test Login

1. Start Docker stack: `./docker/start.sh` (or clean-start under `ezkey-tests/`)
2. Ensure API key credentials are configured (`EZKEY_INTEGRATION_KEY` / `EZKEY_SECRET_KEY` or dialog)
3. Open http://localhost:8082
4. Enter a real `user_identifier` for an enrollment under that integration
5. Approve on demo-device (http://localhost:8083)

### Configure API Key (No Restart)

1. Open http://localhost:8082/login
2. Click "Configure API Key"
3. Enter Integration Key and Secret Key
4. Apply — session-scoped only

### Edit External Configuration (restart required)

1. Edit `/app/config/application.properties` as user `spring` (temp + `mv`, umask 077)
2. Restart: `docker restart ezkey-demo-app-acme` (local) or `ezkey-exp-demo-app-acme` (community)
3. Confirm logs: `Access-code slot loaded` when adding slots; fail-fast on bad/duplicate codes

### Debug Login Issues

#### 401 UNAUTHORIZED Errors

1. **API Key Credentials** — dialog, env, or config + restart; check logs for key-loaded messages
2. **IP Whitelist** on the API key (Admin API) vs container source IP
3. **API Key Status** — active, not expired, correct integration
4. **Enrollment ownership** — username/`user_identifier` must belong to that integration
5. **Integration API logs** — management port health / auth failures

#### General Debugging

1. Check application logs for API errors
2. Verify Integration API health on its management port
3. Verify property names are `ezkey.integration-key` / `ezkey.secret-key` (not `ezkey.integration.key`)
4. For slots: confirm four-line block + restart; codes must be 32–64 lowercase hex
