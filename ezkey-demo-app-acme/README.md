# Ezkey Demo App ACME

Demo application showcasing Ezkey passwordless login integration with backend-side authentication.

## Quick Start

### Docker (Recommended)

```bash
# From repo root
./docker/start.sh
# Access: http://localhost:8082

# API key credentials: set via config or use "Configure API Key" dialog on login page
export EZKEY_INTEGRATION_KEY=ezkey_ikey_xxx
export EZKEY_SECRET_KEY=ezkey_skey_xxx

# Ensure users mapping file exists:
# data/acme-users.json
```

For the internet-facing demo mode, the login page also supports entering the integration key and
secret interactively. Those credentials are stored only for the current browser session and can be
reused across multiple login/logout cycles in that same session.

### IDE / Local

```bash
cd ezkey-demo-app-acme
mvn spring-boot:run
# Requires Integration API reachable (default http://localhost:7080 — map ezkey.admin-api-url if different)
# Requires EZKEY_INTEGRATION_KEY and EZKEY_SECRET_KEY environment variables
# Requires data/acme-users.json file
```

## URLs

| Service | URL |
|---------|-----|
| Demo App | http://localhost:8082 |
| Login Page | http://localhost:8082/login |
| Dashboard | http://localhost:8082/dashboard |

## Login Flow

```mermaid
sequenceDiagram
    participant Browser
    participant AcmeBackend
    participant IntegrationAPI
    participant Device

    Browser->>AcmeBackend: POST /login (username)
    AcmeBackend->>AcmeBackend: Lookup enrollmentId from users.json
    AcmeBackend->>IntegrationAPI: POST /api/v1/auth-attempts (HTTP Basic Auth: integrationKey:secretKey)
    IntegrationAPI-->>AcmeBackend: authAttemptId
    AcmeBackend->>IntegrationAPI: GET /auth-attempts/{id}/wait
    Note over Device: User approves
    IntegrationAPI-->>AcmeBackend: status ACCEPTED
    AcmeBackend->>AcmeBackend: Create HTTP session
    AcmeBackend-->>Browser: Redirect to /dashboard
```

## Configuration

### External Configuration Override

Spring Boot supports external configuration files that override JAR-internal properties:

- **Docker**: Place `application.properties` in `/app/config/` (mounted volume)
- **Local**: Place `application.properties` in `./config/` directory next to the JAR

Properties can be hot-reloaded via Actuator `/refresh` endpoint:
```bash
curl -X POST http://localhost:8082/actuator/refresh
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `ezkey.admin-api-url` | `http://localhost:7080` | Ezkey SDK base URL — **Integration API** (`http://integration-api:7080` in Docker Compose). Legacy env: `EZKEY_ADMIN_API_URL`. |
| `ezkey.integration.key` | (required) | Integration key for API key authentication (e.g., `ezkey_ikey_xxx`) |
| `ezkey.secret.key` | (required) | Secret key for API key authentication (e.g., `ezkey_skey_xxx`) |
| `ezkey.users.file` | `data/acme-users.json` | Path to users mapping file |
| `ezkey.users.file.check-interval` | `5` | Hot-reload check interval in seconds (0 to disable) |

**Authentication Format**: `Authorization: Basic base64(integrationKey:secretKey)`

## Temporary Access Codes (Play closed testing)

Optional multi-tenant slots for evaluator links. Operator steps (no personal data in the repo):

1. **Generate a code** (32 lowercase hex chars):

   ```bash
   openssl rand -hex 16
   ```

2. **Edit** `/app/config/application.properties` (Docker volume) or the local external config file:

   ```properties
   ezkey.access-codes.northwind.code=<openssl-rand-hex-16>
   ezkey.access-codes.northwind.integration-key=ezkey_ikey_…
   ezkey.access-codes.northwind.secret-key=ezkey_skey_…
   ezkey.access-codes.northwind.label=Northwind Portal
   ```

3. **Restart** the demo container so slots load (fail-fast if a code is malformed or duplicated).

4. **Tester URL:** `https://<host>/t/{code}` (local: `http://localhost:8082/t/{code}`). Unknown codes show the generic sign-in error.

5. **Revoke:** remove or rotate the slot (delete the `ezkey.access-codes.<slot>.*` block, or change `.code`) and restart.

6. **File permissions:** the image runs as `spring` (uid `100` / gid `101`). Config should be mode `0600`. The entrypoint applies `chmod 600` on every start; if that fails (e.g. root-owned volume file), it prints a WARN — fix ownership on the host volume.

Never commit real access codes or personal tester data. See `config/application.properties.example` for the slot shape.

## Rate limiting (QA notes)

Acme enforces its own per-IP Bucket4j limits from `application.properties` (`ezkey.rate-limit.*`).
Defaults: **login and `/t/{code}` share one bucket of 10 attempts per 5 minutes per client IP**;
apply-api-key is a separate bucket (5 / 10 min). These limits are **on by default** on a normal
clean-start / Docker stack — they do **not** require `./ezkey-tests/clean-start.sh --prod-safe`
(`--prod-safe` only changes Spring API profiles for Admin/Auth/Integration, not Acme’s demo
limiter).

Testers (or Walk agents) behind the same NAT / egress IP share the login+/t bucket. Exhausting it
does **not** return HTTP 429 from these browser entry points: `GET /t/{code}` responds **200** with
the generic rate-limit message on the login page, and `POST /login` responds **302** to
`/login?error=ratelimited` (same message in flash). Wait for the window or use another IP.

## Demo Session Model

- API key credentials entered in the UI are scoped to the current browser session only.
- Logging out clears the authenticated demo user and pending auth flow state, but keeps the demo
  API key available for the next login attempt in that same browser session.
- A different browser or browser profile does not inherit another session's demo API key.
- Session expiry or losing the browser session can require re-entering the API key.
- Access-code activation and pasted API keys each invalidate the previous session before storing the
  new credential source.

## Users Mapping File

Create `data/acme-users.json`:

```json
{
  "users": [
    {
      "username": "alice",
      "enrollmentId": 1,
      "displayName": "Alice Demo"
    },
    {
      "username": "bob",
      "enrollmentId": 2,
      "displayName": "Bob Test"
    }
  ]
}
```

The file is hot-reloadable - changes are detected and reloaded automatically (if `check-interval > 0`).

## Key Files

- `controller/LoginController.java` - Handles POST `/login`, coordinates auth flow
- `controller/HomeController.java` - Routes for dashboard/logout
- `config/EzkeyClientProvider.java` - Supplies EzkeyClient from current credentials
- `service/DemoApiKeyConfigService.java` - API key holder with runtime override support
- `service/UserMappingService.java` - Users file loader with hot-reload
- `config/EzkeyClientConfig.java` - Spring configuration
- `templates/login.html` - Login page with "Configure API Key" dialog
- `templates/dashboard.html` - Post-login dashboard

## Architecture

- **Backend-side authentication**: Server calls Admin API using API key (machine-to-machine) authentication
- **HTTP session**: Secure server-side session management
- **Session-scoped demo credentials**: API keys entered in the UI are isolated per browser session
- **User mapping**: External JSON file maps usernames to enrollment IDs
- **Hot-reload**: Users file changes detected and reloaded automatically

## Prerequisites

1. **API Key Credentials**: Must be created via Admin API for the ACME integration:
   - `integrationKey`: Public key (e.g., `ezkey_ikey_xxx`) - used as HTTP Basic Auth username
   - `secretKey`: Secret key (e.g., `ezkey_skey_xxx`) - used as HTTP Basic Auth password
   - Set via `EZKEY_INTEGRATION_KEY` and `EZKEY_SECRET_KEY` env vars, config file, or **"Configure API Key" dialog** on login page (no restart)
2. **Users File**: Automatically created by bootstrap-init with `admin.docker` user (enrollmentId: 1)
3. **Enrollments**: Users must have active enrollments in the Ezkey system

## Docker Setup

The Docker stack automatically:
- Creates `acme-users.json` with `admin.docker` user via bootstrap-init
- Mounts `/app/config/` volume for external `application.properties` override
- Mounts `/app/data/` volume for `acme-users.json` file

### Editing Configuration in Docker Desktop

1. **API Keys**: Use "Configure API Key" dialog on login page (no restart), or edit `/app/config/application.properties` and restart
2. **Users Mapping**: Edit `/app/data/acme-users.json` → Auto-reloaded every 5 seconds or click "Reload Users"

## Future Enhancements

- Bootstrap-init integration to automatically create API key and seed users file
- Multiple integration support
- User self-registration flow