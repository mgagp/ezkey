# EZKey Demo App ACME - Agent Notes

## Purpose

Demo application for EZKey passwordless login. Demonstrates backend-side authentication using API Key M2M communication with Admin API.

## Key Files

| File | Description |
|------|-------------|
| `controller/HomeController.java` | Routes: `/dashboard`, `/logout` |
| `controller/LoginController.java` | Handles POST `/login`, coordinates auth flow |
| `service/EzkeyAuthService.java` | Calls Admin API: create auth-attempt, wait for completion |
| `service/UserMappingService.java` | Loads/reloads users.json mapping (username -> enrollmentId) |
| `config/EzkeyClientConfig.java` | RestTemplate configuration with API Key auth |
| `config/AcmeProperties.java` | Type-safe configuration properties |
| `templates/login.html` | Login page with form POST |
| `templates/dashboard.html` | Post-login dashboard with user info |

## Architecture

- **Mode**: Backend-side (server calls Admin API via RestTemplate)
- **Port**: 8082
- **Auth**: HTTP session (server-side)
- **API Key**: M2M authentication for Admin API calls (HTTP Basic Auth: `base64(integrationKey:secretKey)`)
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
# Admin API URL (internal Docker network or localhost)
ezkey.admin.api.url=http://admin-api:9080  # Docker
ezkey.admin.api.url=http://localhost:9080  # IDE

# API Key credentials for M2M authentication
# Format: Authorization: Basic base64(integrationKey:secretKey)
ezkey.integration.key=${EZKEY_INTEGRATION_KEY:}
ezkey.secret.key=${EZKEY_SECRET_KEY:}

# Users mapping file path
ezkey.users.file=${EZKEY_USERS_FILE:data/acme-users.json}

# File hot-reload check interval (seconds, 0 to disable)
ezkey.users.file.check-interval=${EZKEY_USERS_CHECK_INTERVAL:5}
```

### Hot-Reload Configuration

The "Reload Config" button (or `/api/reload-config` endpoint) performs **two types of reload**:

1. **Application Properties Reload** (via Spring Cloud ContextRefresher):
   - Reloads `/app/config/application.properties` external configuration file
   - Refreshes `@RefreshScope` beans (`AcmeProperties`, `RestTemplate`)
   - Updates API key credentials (`ezkey.integration.key`, `ezkey.secret.key`)
   - Updates Admin API URL (`ezkey.admin.api.url`)
   - **Important**: The `RestTemplate` bean is recreated with new credentials, ensuring API calls use updated keys

2. **Users Mapping File Reload** (manual trigger):
   - Manually triggers reload of `acme-users.json` file
   - Complements the automatic `@Scheduled` reload (every 5 seconds by default)
   - Allows immediate refresh on demand without waiting for scheduled check

**Usage:**
1. Edit external config file: Modify `/app/config/application.properties` in Docker Desktop
2. Edit users mapping: Modify `/app/data/acme-users.json` in Docker Desktop
3. Click "Reload Config" button on login page OR call: `curl -X POST http://localhost:8082/api/reload-config`
4. Both configuration files are reloaded immediately

**Note**: The users file is also automatically reloaded every 5 seconds via `@Scheduled`, but the button allows immediate refresh.

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
- Populated by bootstrap-init or external test tooling (future phase)
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

### Edit External Configuration (Docker Desktop)
1. Open Docker Desktop → Containers → `ezkey-demo-app-acme`
2. Go to "Files" tab
3. Navigate to `/app/config/`
4. Create/edit `application.properties` (add your API key credentials)
5. Click "Reload Config" button on login page OR call: `curl -X POST http://localhost:8082/api/reload-config`
6. Changes are applied without restart

**Important**: If you perform a clean-start (remove volumes), the `/app/config/application.properties` file will be automatically recreated from template. If the volume persists, you may need to manually delete the file or edit it directly.

### Edit Users Mapping File (Docker Desktop)
1. Open Docker Desktop → Containers → `ezkey-demo-app-acme`
2. Go to "Files" tab
3. Navigate to `/app/data/`
4. Edit `acme-users.json`
5. File is automatically reloaded every 5 seconds (configurable) OR click "Reload Config" for immediate reload

### Debug Login Issues

#### 401 UNAUTHORIZED Errors

If you see `401 UNAUTHORIZED` errors when creating auth attempts, check the following:

1. **API Key Credentials**:
   - Verify `ezkey.integration.key` and `ezkey.secret.key` are set correctly in `/app/config/application.properties`
   - Check logs for: `✅ API Key credentials configured` (should appear at startup)
   - After editing config, click "Reload Config" button or restart container

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

5. **Check Admin API Logs**:
   ```bash
   docker compose logs admin-api | grep -i "api key\|authentication\|401"
   ```
   Look for:
   - `✅ API key authentication successful` (success)
   - `❌ API key authentication failed` (failure with reason)
   - `IP address X not whitelisted` (IP whitelist issue)

#### General Debugging
1. Check application logs for API errors
2. Verify Admin API is accessible: `curl http://localhost:9080/actuator/health`
3. Verify API Key credentials are configured: Check `ezkey.integration.key` and `ezkey.secret.key` properties
4. Verify users file exists and is readable
5. Check enrollmentId in users file matches actual enrollment

### Create Users Mapping File
1. Create `data/acme-users.json` with structure above
2. Map usernames to enrollment IDs from Admin API
3. File will be hot-reloaded if changed (if check-interval > 0)