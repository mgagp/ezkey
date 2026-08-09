# Ezkey Spring Profiles - Configuration Modes

## Overview

Ezkey supports two distinct operational modes via Spring Profiles:

1. **Docker Mode** (`docker`) - Production-like containerized deployment
2. **Windows Mode** (`windows`) - Windows native development

Each mode has optimized configuration for its environment, particularly for encryption key paths.

---

## Quick Start

### Docker Mode (Default)

```bash
# Start with Docker Compose (automatically uses docker profile)
./docker/start.sh

# Or manually with docker profile
docker run -e SPRING_PROFILES_ACTIVE=docker ezkey-admin-api
```

**Encryption Keys Location:**
- Master key: `/etc/ezkey/secrets/master.key` (Docker volume)
- Keyset: `/etc/ezkey/keysets/keyset.json.encrypted` (Docker volume)

**Generate keys:**
```bash
./docker/generate-encryption-keys.sh
```

---

### Windows Mode

```powershell
# Run locally on Windows
$env:SPRING_PROFILES_ACTIVE = "windows"
java -jar ezkey-admin-api.jar

# Or via IDE (IntelliJ IDEA)
# Run → Edit Configurations → VM options: -Dspring.profiles.active=windows
```

**Encryption Keys Location:**
- Master key: `C:\ProgramData\ezkey\secrets\master.key` (Windows filesystem)
- Keyset: `C:\ProgramData\ezkey\keysets\keyset.json.encrypted` (Windows filesystem)

**Generate keys (Git Bash, Windows native profile — dev only):**
```bash
mkdir -p /c/ProgramData/ezkey/secrets /c/ProgramData/ezkey/keysets
openssl rand -base64 32 > /c/ProgramData/ezkey/secrets/master.key
chmod 600 /c/ProgramData/ezkey/secrets/master.key
```

For Linux/macOS host installs, use [`scripts/generate-master-key.sh`](../scripts/generate-master-key.sh). For Docker, prefer [`docker/generate-encryption-keys.sh`](../docker/generate-encryption-keys.sh).

---

## Profile-Specific Configurations

### Admin API (`ezkey-admin-api`)

#### Docker Profile (`application-docker.properties`)
- **Port:** 9080
- **Database:** `jdbc:postgresql://postgres:5432/ezkey_db`
- **Admin Username:** `admin.docker`
- **Encryption Keys:** `/etc/ezkey/secrets/master.key`
- **Keyset Storage:** DATABASE (distributed, multi-instance)
- **Bootstrap Export:** Enabled (`/var/lib/ezkey/bootstrap/bootstrap-credentials.json`)
- **Rate Limiting:** Production defaults (restrictive)
- **MFA Mode:** `prod` (required)
- **Logging:** INFO level (production-friendly)

#### Windows Profile (`application-windows.properties`)
- **Port:** 9080
- **Database:** `jdbc:postgresql://localhost:5432/ezkey_db`
- **Admin Username:** `admin.windows`
- **Encryption Keys:** `C:\ProgramData\ezkey\secrets\master.key`
- **Keyset Storage:** FILE (single-instance, local cache)
- **Bootstrap Export:** Disabled
- **Rate Limiting:** Development defaults (permissive)
- **MFA Mode:** `dev` (optional)
- **Logging:** DEBUG level (development-friendly)

---

### Auth API (`ezkey-auth-api`)

#### Docker Profile (`application-docker.properties`)
- **Port:** 8080
- **Database:** `jdbc:postgresql://postgres:5432/ezkey_db`
- **Encryption Keys:** `/etc/ezkey/secrets/master.key`
- **Keyset Storage:** DATABASE (synchronized with Admin API)
- **Rate Limiting:** Production defaults
- **Logging:** INFO level

#### Windows Profile (`application-windows.properties`)
- **Port:** 8080
- **Database:** `jdbc:postgresql://localhost:5432/ezkey_db`
- **Encryption Keys:** `C:\ProgramData\ezkey\secrets\master.key`
- **Keyset Storage:** FILE (local)
- **Rate Limiting:** Development defaults
- **Logging:** DEBUG level

---

## Setting Spring Profiles

### Method 1: Environment Variable (Recommended)

```bash
# Linux/Mac
export SPRING_PROFILES_ACTIVE=windows
java -jar ezkey-admin-api.jar

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE = "windows"
java -jar ezkey-admin-api.jar

# Windows Command Prompt
set SPRING_PROFILES_ACTIVE=windows
java -jar ezkey-admin-api.jar
```

### Method 2: Command-Line Argument

```bash
java -jar ezkey-admin-api.jar --spring.profiles.active=windows
```

### Method 3: Application Properties

Add to `application.properties`:
```properties
spring.profiles.active=windows
```

### Method 4: IDE Configuration (IntelliJ IDEA)

1. Run → Edit Configurations
2. Select the application configuration
3. VM options: `-Dspring.profiles.active=windows`

---

## Docker Compose Integration

Docker Compose automatically sets the `docker` profile via environment variables:

```yaml
services:
  admin-api:
    environment:
      SPRING_PROFILES_ACTIVE: docker,docker-dev,docker-test
```

This means:
- `docker` profile is always active in Docker Compose
- `docker-dev` adds development diagnostics (Actuator exposure)
- `docker-test` disables HTTP/API-key rate limiting for testing — peripheral audit-chain heartbeat supervision stays enabled (`ezkey.audit.chain.heartbeat.enabled=true`) unless explicitly overridden

---

## Master Key Generation

### Docker Mode

```bash
# Generate master key in Docker volume
./docker/generate-encryption-keys.sh

# For HA mode
./docker/generate-encryption-keys.sh --ha

# For native image mode
./docker/generate-encryption-keys.sh --native
```

The key is created at `/etc/ezkey/secrets/master.key` inside the Docker volume `ezkey_encryption-secrets`.

### Windows Mode

```bash
# Git Bash — default Windows native paths (dev only)
mkdir -p /c/ProgramData/ezkey/secrets /c/ProgramData/ezkey/keysets
openssl rand -base64 32 > /c/ProgramData/ezkey/secrets/master.key
chmod 600 /c/ProgramData/ezkey/secrets/master.key
```

The key is created at `C:\ProgramData\ezkey\secrets\master.key` by default.

---

## Encryption Keyset Storage

### Docker Mode: DATABASE
- **Source of Truth:** PostgreSQL database (`ezkey_keyset_blob` table)
- **Blob Format:** Tink encrypted-keyset JSON envelope protected by the master key
- **Local Cache:** File-based fallback (`/etc/ezkey/keysets/keyset.json.encrypted`)
- **Synchronization:** Admin API updates database, other instances reload from DB
- **Use Case:** Multi-instance deployments, distributed systems

### Windows Mode: FILE
- **Storage:** Local filesystem only
- **Location:** `C:\ProgramData\ezkey\keysets\keyset.json.encrypted`
- **Use Case:** Single-instance local development

---

## Clean Start for Each Mode

### Docker Clean Start

```bash
cd ezkey-tests
./clean-start.sh

# Or with specific mode
./clean-start.sh --native    # Native images
./clean-start.sh --ha        # High Availability
```

This automatically:
1. Stops and removes Docker containers
2. Removes volumes (including encryption keys)
3. Generates new master key
4. Starts fresh Docker stack
5. Bootstraps admin enrollment

### Windows Clean Start

```bash
# 1. Generate master key (Git Bash)
mkdir -p /c/ProgramData/ezkey/secrets /c/ProgramData/ezkey/keysets
openssl rand -base64 32 > /c/ProgramData/ezkey/secrets/master.key

# 2. Start PostgreSQL locally (if using docker compose for DB)
# OR ensure PostgreSQL is running on localhost:5432

# 3. Run the application
export SPRING_PROFILES_ACTIVE=windows
java -jar ezkey-admin-api.jar
```

---

## Important Notes

### Key Sharing Between APIs

⚠️ **CRITICAL:** Admin API and Auth API **MUST** use the same master key:

```bash
# Both need access to the same key file
admin-api:  /etc/ezkey/secrets/master.key    (Docker)
auth-api:   /etc/ezkey/secrets/master.key    (Docker)

# OR

admin-api:  C:\ProgramData\ezkey\secrets\master.key    (Windows)
auth-api:   C:\ProgramData\ezkey\secrets\master.key    (Windows)
```

### File Permissions

#### Linux/Mac
```bash
# Master key must have 600 permissions (owner read/write only)
chmod 600 /etc/ezkey/secrets/master.key

# Owned by spring user (UID 100)
chown spring:spring /etc/ezkey/secrets/master.key
```

#### Windows
- Current user must have Full Control
- Default permissions are set automatically by PowerShell script

### Database Requirements

Both Docker and Windows modes require PostgreSQL:

#### Docker Mode
- Automatically provided by `docker-compose.yml`
- Connection: `postgres:5432` (internal Docker network)

#### Windows Mode
- Must be running locally or on accessible host
- Connection: `localhost:5432` (default)
- Credentials: `postgres:ezkey` (default)

---

## Troubleshooting

### "Master key file not found"

**Docker:**
```bash
# Regenerate master key
./docker/generate-encryption-keys.sh
```

**Windows (Git Bash, native profile):**
```bash
openssl rand -base64 32 > /c/ProgramData/ezkey/secrets/master.key
```

### "Encryption paths mismatch"

Ensure you're using the correct profile:

```bash
# Check which profile is active
curl http://localhost:9080/actuator/env | grep "spring.profiles"

# Explicitly set the profile
export SPRING_PROFILES_ACTIVE=docker  # or windows
```

### Database connection errors

**Windows:**
```powershell
# Ensure PostgreSQL is running
Get-Process postgres

# Test connection
psql -U postgres -d ezkey_db -h localhost
```

---

## Migration Between Modes

To migrate from Windows mode to Docker mode:

1. **Backup current encryption state:**
   ```powershell
   # Backup master key
   Copy-Item "C:\ProgramData\ezkey\secrets\master.key" "C:\Backup\master.key.backup"
   ```

2. **Export database (optional):**
   ```bash
   pg_dump ezkey_db > backup.sql
   ```

3. **Start Docker with Docker Compose:**
   ```bash
   ./docker/start.sh
   ```

4. **Restore encryption key to Docker volume:**
   ```bash
   # Copy backed-up master key to Docker volume
   docker cp C:\Backup\master.key.backup ezkey-postgres:/etc/ezkey/secrets/master.key
   ```

5. **Restart API services:**
   ```bash
   docker-compose restart admin-api auth-api
   ```

---

## Reference Files

- **Admin API Docker Profile:** [ezkey-admin-api/config/application-docker.properties](../ezkey-admin-api/config/application-docker.properties)
- **Admin API Windows Profile:** [ezkey-admin-api/config/application-windows.properties](../ezkey-admin-api/config/application-windows.properties)
- **Auth API Docker Profile:** [ezkey-auth-api/config/application-docker.properties](../ezkey-auth-api/config/application-docker.properties)
- **Auth API Windows Profile:** [ezkey-auth-api/config/application-windows.properties](../ezkey-auth-api/config/application-windows.properties)
- **Master Key Generation Script (Linux/Mac):** [scripts/generate-master-key.sh](../scripts/generate-master-key.sh)
- **Docker Key Generation Script:** [docker/generate-encryption-keys.sh](../docker/generate-encryption-keys.sh)
