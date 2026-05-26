# EZKey Demo App ACME - Agent Notes

## Purpose

Demo application for EZKey passwordless login. Demonstrates backend-side authentication using API key (machine-to-machine) against the **Integration API** via the Ezkey Java SDK (`EzkeyClient`).

## Key Files

| File | Description |
|------|-------------|
| `controller/HomeController.java` | Routes: `/dashboard`, `/logout` |
| `controller/LoginController.java` | Handles POST `/login`, coordinates auth flow |
| `config/EzkeyClientProvider.java` | Supplies EzkeyClient from current credentials (config or runtime override) |
| `service/DemoApiKeyConfigService.java` | Holds API key credentials; supports runtime override via "Apply API Key" dialog |
| `service/UserMappingService.java` | Loads/reloads users.json mapping (username -> enrollmentId) |
| `config/EzkeyClientConfig.java` | Spring configuration for AcmeProperties |
| `config/AcmeProperties.java` | Type-safe configuration properties |
| `templates/login.html` | Login page with form POST, "Configure API Key" dialog |
| `templates/dashboard.html` | Post-login dashboard with user info |

## Architecture

- **Mode**: Backend-side (server calls Integration API via Ezkey SDK; base URL `ezkey.admin-api-url`)
- **Port**: 8082
- **Auth**: HTTP session (server-side)
- **API Key**: API key authentication via EzkeyClient (from EzkeyClientProvider); credentials from config or "Apply API Key" dialog
- **User Mapping**: External JSON file (`data/acme-users.json`) with hot-reload

## Login Flow

1. User enters username on `/login` (form POST)
2. Backend looks up `enrollmentId` from `users.json` mapping
3. Backend calls `POST /api/v1/auth-attempts` (with API Key)
4. Backend calls `GET /api/v1/auth-attempts/{id}/wait` (polls until approved)
5. On approval, create HTTP session with `AuthenticatedUser`
6. Redirect to `/dashboard` (protected route)

## Configuration

### External Configuration Override

Spring Boot supports external configuration files that override JAR-internal properties:

**Docker**: Place `application.properties` in `/app/config/` (mounted volume `demo-app-acme-config`)
**Local**: Place `application.properties` in `./config/` directory next to the JAR

Properties can be hot-reloaded via Actuator `/refresh` endpoint (see Hot-Reload section below).

### Configuration Properties

```properties
# Ezkey SDK base URL — Integration API (Docker internal hostname). Env override:
# EZKEY_ADMIN_API_URL=http://integration-api:7080  (legacy variable name).
ezkey.admin-api-url=http://integration-api:7080  # Docker
ezkey.admin-api-url=http://localhost:7080          # IDE — Integration API on localhost:7080

# API Key credentials for Integration API (machine-to-machine) authentication
# Format: Authorization: Basic base64(integrationKey:secretKey)
ezkey.integration.key=${EZKEY_INTEGRATION_KEY:}
ezkey.secret.key=${EZKEY_SECRET_KEY:}

# Users mapping file path
ezkey.users.file=${EZKEY_USERS_FILE:data/acme-users.json}

# File hot-reload check interval (seconds, 0 to disable)
ezkey.users.file.check-interval=${EZKEY_USERS_CHECK_INTERVAL:5}
```

### Hot-Reload Configuration

1. **API Key (runtime, no restart)**:
   - Click "Configure API Key" in the How it Works section on the login page
   - Enter Integration Key and Secret Key, then click "Apply API Key"
   - Credentials take effect immediately for subsequent logins
   - Demo only — credentials held in memory, not persisted

2. **Users Mapping File** (manual trigger or automatic):
   - Click "Reload Users" button (or wait for automatic reload every 5 seconds)
   - Manually triggers reload of `acme-users.json` file
   - Edit `/app/data/acme-users.json` in Docker Desktop

3. **Application Properties** (requires container restart):
   - API keys and Admin API URL can also be set in `/app/config/application.properties` at startup
   - Changes to this file require container restart: `docker restart ezkey-demo-app-acme`

## Users Mapping File

File: `data/acme-users.json`

```json
{
  "users": [
    {
      "username": "alice",
      "enrollmentId": 1,
      "displayName": "Alice Demo"
    }
  ]
}
```

- Hot-reloadable via timestamp check
- Populated by bootstrap-init or external test tooling (future milestone)
- Simple flat structure for demo purposes

## Watchouts

- **API Key Credentials Required**: Must be configured via `EZKEY_INTEGRATION_KEY` and `EZKEY_SECRET_KEY` environment variables or in external config file
- **Users File**: Must exist and contain valid username -> enrollmentId mappings
- **Challenge Code**: 2 digits for auth attempts (not 6 - that's enrollment)
- **Session Security**: Dashboard route validates session server-side
- **Hot Reload**: File changes detected every N seconds (configurable, default 5s)

## Common Tasks

### Test Login
1. Start Docker stack: `./docker/start.sh`
2. Bootstrap-init automatically creates `acme-users.json` with `admin.docker` user
3. Ensure API key credentials are configured:
   - `EZKEY_INTEGRATION_KEY=ezkey_ikey_xxx` (environment variable or config file)
   - `EZKEY_SECRET_KEY=ezkey_skey_xxx` (environment variable or config file)
4. Open http://localhost:8082
5. Enter username: `admin.docker`
6. Approve on demo-device (http://localhost:8083)

### Configure API Key (No Restart)
1. Open http://localhost:8082/login
2. In the "How it Works" section, click "Configure API Key"
3. Enter Integration Key and Secret Key from your Admin API
4. Click "Apply API Key" — credentials take effect immediately

### Edit External Configuration (Docker Desktop)
1. Open Docker Desktop → Containers → `ezkey-demo-app-acme`
2. Go to "Files" tab
3. Navigate to `/app/config/`
4. Create/edit `application.properties` (add your API key credentials)
5. Restart container: `docker restart ezkey-demo-app-acme`

**Alternative**: Use the "Configure API Key" dialog on the login page for immediate application without restart.

### Edit Users Mapping File (Docker Desktop)
1. Open Docker Desktop → Containers → `ezkey-demo-app-acme`
2. Go to "Files" tab
3. Navigate to `/app/data/`
4. Edit `acme-users.json`
5. File is automatically reloaded every 5 seconds OR click "Reload Users" for immediate reload

### Debug Login Issues

#### 401 UNAUTHORIZED Errors

If you see `401 UNAUTHORIZED` errors when creating auth attempts, check the following:

1. **API Key Credentials**:
   - Use "Configure API Key" dialog on login page for immediate application without restart
   - Or set via `/app/config/application.properties` and restart container
   - Check logs for: `Demo API key loaded from config` or `API key applied via demo UI`

2. **IP Whitelist**:
   - API keys have an IP whitelist that restricts which IPs can use them
   - Default whitelist for new API keys: `172.0.0.0/8` (Docker network range)
   - Check Admin API logs for: `❌ API key authentication failed - IP: ...`
   - **Docker Network**: The container IP should be in `172.x.x.x` range (check with `docker inspect ezkey-demo-app-acme | grep IPAddress`)
   - **Solution**: Update API key whitelist to include container IP or use `0.0.0.0/0` for testing (not recommended for production)

3. **API Key Status**:
   - Verify API key is active (not revoked)
   - Check API key expiration date
   - Verify API key belongs to the correct integration

4. **Enrollment Ownership**:
   - Verify `enrollmentId` in `acme-users.json` belongs to the integration associated with the API key
   - Check Admin API logs for: `API key from integration X attempted to create auth attempt for enrollment Y belonging to integration Z`

5. **Check Integration API Logs** (Ezkey SDK targets Integration API for auth attempts):
   ```bash
   docker compose logs integration-api | grep -i "api key\|authentication\|401"
   ```
   Look for:
   - `✅ API key authentication successful` (success)
   - `❌ API key authentication failed` (failure with reason)
   - `IP address X not whitelisted` (IP whitelist issue)

#### General Debugging
1. Check application logs for API errors
2. Verify Integration API is reachable: `curl http://localhost:7081/actuator/health` (management port)
3. Verify API Key credentials are configured: Check `ezkey.integration.key` and `ezkey.secret.key` properties
4. Verify users file exists and is readable
5. Check enrollmentId in users file matches actual enrollment

### Create Users Mapping File
1. Create `data/acme-users.json` with structure above
2. Map usernames to enrollment IDs from Admin API
3. File will be hot-reloaded if changed (if check-interval > 0)