# Ezkey Docker Deployment

This directory contains Docker configuration files and scripts to run the complete Ezkey stack in containers.

If you are following the main project quick start, use `./ezkey-tests/clean-start.sh` from the repository root. This document covers the lower-level Docker entrypoints for developers who want direct control over the stack.

## Quick Start (5-Minute Setup)

### Prerequisites

- **Docker Desktop** (or Docker Engine + Docker Compose)
  - Windows/Mac: [Docker Desktop](https://www.docker.com/products/docker-desktop)
  - Linux: Docker Engine + Docker Compose plugin

### Start the Stack

**Default Mode (Spring Boot JVM):**
```bash
./docker/start.sh
```

On Windows, use Bash as well, for example through Git Bash.

**Native Mode (GraalVM Native Images):**
```bash
./docker/start.sh --native
```

**Note**: Native mode requires pre-built native images for Auth API and Integration API. Admin API stays on the JVM in native mode. Build the native images first:
```bash
# Build native images
mvn spring-boot:build-image -pl ezkey-auth-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-auth-api-native -DskipTests
mvn spring-boot:build-image -pl ezkey-integration-api -Pnative \
   -Dspring-boot.build-image.imageName=ezkey-integration-api-native -DskipTests

# Then start with --native flag
./docker/start.sh --native
```

That's it! The script will:
1. Build all Docker images (or use native images if --native flag is used)
2. Start PostgreSQL database
3. Run database migrations
4. Start all API services
5. Wait for services to be healthy
6. Automatically bootstrap enrollment and seed demo-device

**Note**: For first-time setup, generate encryption keys before starting:
```bash
./docker/generate-encryption-keys.sh
```

### Access the Services

Once started, you can access:

- **Admin API**: http://localhost:9080
- **Auth API**: http://localhost:8080
- **Crypto API**: http://localhost:9090
- **Demo Device**: http://localhost:8083

**API Documentation (Swagger UI):**
- Admin API: http://localhost:9080/swagger-ui/index.html
- Auth API: http://localhost:8080/swagger-ui/index.html
- Crypto API: http://localhost:9090/swagger-ui/index.html

## Container timezone and log timestamps

Docker images use **UTC** for their default timezone unless you configure otherwise. The JVM (Spring Boot) and PostgreSQL read the standard **`TZ`** environment variable. If your host clock shows 14:45 in Montréal (Eastern Daylight, UTC−4) while `docker logs` show **18:45**, that is the same instant expressed in **UTC** (14:45 + 4h).

**Configure locally (recommended for developers):**

1. Copy `docker/.env.example` to `docker/.env` (gitignored).
2. Set a single IANA name, for example:
   - `TZ=America/Toronto` (Eastern — includes DST)
   - `TZ=Europe/Paris`
3. Recreate containers so they pick up the new env (`docker compose up -d` after `down` or `up --force-recreate` as needed).

Compose passes `TZ: ${TZ:-UTC}` into services; if `TZ` is unset, behavior stays **UTC** (good for CI and reproducible traces).

**Why not “match the host automatically”?** Docker Compose does not read the host OS timezone. On **Linux**, you can export a zone before starting the stack, for example `export TZ=$(timedatectl show -p Timezone --value)` when `timedatectl` exists. On **Windows** with Docker Desktop, set `TZ` explicitly in `docker/.env` (one line per machine or team).

**Separate concern — API JSON datetimes:** Ezkey APIs return timestamps in **UTC** with a `Z` suffix by design (`docs/ENDPOINT.md`, `docs/DATETIME_TIMEZONE_DECISION.md`). Changing `TZ` affects **container logs and local log formatting**, not that API contract.

## Architecture

```
┌─────────────────────────────────────────────────┐
│           Docker Compose Stack                  │
├─────────────────────────────────────────────────┤
│                                                 │
│  ┌──────────────┐    ┌──────────────┐         │
│  │  PostgreSQL  │    │  Migration   │         │
│  │     17       │◄───│    Job       │         │
│  └──────┬───────┘    └──────────────┘         │
│         │                                       │
│         ├──► ┌──────────────┐                 │
│         │    │  Admin API   │                 │
│         │    │   (9080)     │                 │
│         │    └──────────────┘                 │
│         │                                       │
│         ├──► ┌──────────────┐                 │
│         │    │  Auth API    │                 │
│         │    │   (8080)     │                 │
│         │    └──────────────┘                 │
│         │                                       │
│         ├──► ┌──────────────┐                 │
│         │    │ Crypto API   │                 │
│         │    │   (9090)     │                 │
│         │    └──────────────┘                 │
│         │                                       │
│         └──► ┌──────────────┐                 │
│              │ Demo Device  │                 │
│              │   (8083)     │                 │
│              └──────────────┘                 │
│                                                 │
└─────────────────────────────────────────────────┘
```

## Services

### PostgreSQL (postgres)
- **Image**: `postgres:18-alpine`
- **Port**: `5432` (internal only)
- **Database**: `ezkey_db`
- **Bootstrap superuser**: `postgres` / `${POSTGRES_PASSWORD:-ezkey}` (init and `db-grants` only)
- **App roles** (created on empty volume via `postgres/init/`): `ezkey_migrate`, `ezkey_admin`, `ezkey_auth`, `ezkey_integration` — see [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md) and `docker/.env.example`
- **Data Persistence**: Volume `postgres-data` (recreate on clean-start when changing role bootstrap)

### Migration (migration)
- **Type**: One-time job
- **Purpose**: Runs Flyway database migrations as `ezkey_migrate`
- **Depends on**: PostgreSQL (healthy)
- **Runs**: Before `db-grants` and all API services

### DB grants (db-grants)
- **Type**: One-time job
- **Purpose**: Applies DML grants from `scripts/db/apply-grants.sql` (as `postgres`)
- **Depends on**: Migration (completed successfully)

### Admin API (admin-api)
- **Port**: `9080`
- **Purpose**: Administration interface for integrations, enrollments, and auth attempts
- **DB role**: `ezkey_admin`
- **Depends on**: PostgreSQL (healthy), Migration + db-grants (completed)
- **Health Check**: http://localhost:9080/actuator/health
- **Encryption**: Uses shared encryption keys from `encryption-secrets` volume

### Auth API (auth-api)
- **Port**: `8080`
- **Purpose**: Mobile authentication API for enrollment and authentication flows
- **DB role**: `ezkey_auth`
- **Depends on**: PostgreSQL (healthy), Migration + db-grants (completed)
- **Health Check**: http://localhost:8080/actuator/health
- **Encryption**: Uses shared encryption keys from `encryption-secrets` volume

### Crypto API (crypto-api)
- **Port**: `9090`
- **Purpose**: Cryptographic services for testing (key generation, signing, validation)
- **Depends on**: PostgreSQL (healthy), Migration (completed)
- **Health Check**: http://localhost:9090/actuator/health
- **Note**: No database required, but included in stack for consistency

### Demo Device (demo-device)
- **Port**: `8083`
- **Purpose**: Simulated mobile device for testing enrollment and authentication flows
- **Depends on**: Auth API (healthy)
- **Health Check**: http://localhost:8083/actuator/health
- **Bootstrap**: Pre-seeded with global admin enrollment on first startup

### CLI Test Container (cli-test)
- **Container**: `ezkey-cli-test`
- **Purpose**: Interactive Ezkey CLI in a container for QA, demos, and functional tests
- **Depends on**: Admin API, Auth API, Crypto API (healthy)
- **Config Volume**: `/root/.ezkey` (persisted)
- **Working Dir**: `/work` (persisted)

**Quick Usage:**

```bash
# Run CLI commands
docker compose exec cli-test ezkey --help

# Open an interactive shell in the CLI container
docker compose exec cli-test bash
```

### Bootstrap Init (bootstrap-init)
- **Type**: One-time job
- **Purpose**: Automatically performs enrollment bind+verify and seeds demo-device
- **Depends on**: Admin API, Auth API, Crypto API, Demo Device (all healthy)
- **Runs**: After all services are healthy
- **Output**: Creates bootstrap artifacts and demo-device enrollment file
- **Skip**: Set `EZKEY_BOOTSTRAP_INIT_ENABLED=false` when Admin API uses `recovery_primary` bootstrap (no `bootstrap-credentials.json`).
- **Note**: This service makes Docker stack fully self-contained (no Maven/JDK required)

## Bootstrap and Initialization

The Docker stack automatically initializes the global admin enrollment and seeds the demo-device for immediate use:

1. **Admin API** creates the global admin enrollment and exports bootstrap credentials to a file
2. **Bootstrap Init** container reads the credentials, performs bind+verify, and seeds demo-device
3. **Demo-device** is immediately ready for authentication flows

### Credentials output mode (full vs recovery-primary)

- **Default (`full`)** — Admin API logs enrollment material (proof token, challenge, ASCII QR, recovery codes) and writes `bootstrap-credentials.json` when export is enabled. **`bootstrap-init` requires this file** for unattended bind+verify (clean-start / demo).
- **Production-style (`recovery_primary`)** — Set `EZKEY_ADMIN_MFA_BOOTSTRAP_CREDENTIALS_OUTPUT_MODE=recovery_primary` on **admin-api**. Logs contain **recovery codes and operator instructions only**; enrollment secrets are not printed; **JSON export is skipped**, so `bootstrap-credentials.json` is **not** created.
- When using `recovery_primary`, set **`EZKEY_BOOTSTRAP_INIT_ENABLED=false`** on the **bootstrap-init** service (or omit `bootstrap-init` from the compose stack) so the init container does not wait for a missing file. Operators enroll the global admin via **Admin UI → account recovery** (recover → reset enrollment → bind).

**No manual steps required** (with default `full`)! After `docker/start.sh` completes:
- Demo-device is pre-seeded and ready
- Login via `POST /api/v1/admin/auth/login` and approve on demo-device

### Retrieving Bootstrap Artifacts

Bootstrap artifacts are stored in the `bootstrap-artifacts` Docker volume:

```bash
# View bootstrap credentials
docker run --rm -v ezkey_bootstrap-artifacts:/data alpine cat /data/bootstrap-credentials.json

# View device credentials
docker run --rm -v ezkey_bootstrap-artifacts:/data alpine cat /data/device-credentials.json
```

**Note**: Recovery codes are NOT exported to files (logs only for security).

## Management Commands

### Start Services
```bash
# Linux/Mac
./docker/manage.sh start

# Windows
docker\manage.bat start
```

### Stop Services
```bash
# Linux/Mac
./docker/manage.sh stop

# Windows
docker\manage.bat stop
```

### Restart Services
```bash
# Linux/Mac
./docker/manage.sh restart

# Windows
docker\manage.bat restart
```

### View Logs
```bash
# All services
./docker/manage.sh logs

# Specific service
./docker/manage.sh logs admin-api
./docker/manage.sh logs auth-api
./docker/manage.sh logs postgres
```

### Check Status
```bash
# Linux/Mac
./docker/manage.sh status

# Windows
docker\manage.bat status
```

### Build Images
```bash
# Linux/Mac
./docker/manage.sh build

# Windows
docker\manage.bat build
```

### Clean Everything
```bash
# Linux/Mac
./docker/manage.sh clean

# Windows
docker\manage.bat clean
```

**Warning**: The `clean` command removes all containers, networks, and volumes, including database data.

## Maven cache and Docker-only reactor build

Service image builds use **BuildKit** cache mounts in [`Dockerfile`](Dockerfile) (`RUN --mount=type=cache,target=/root/.m2,...`). That cache is **internal to BuildKit** and is not the same as a Docker named volume.

To validate the Java reactor without installing JDK/Maven on the host (equivalent intent to `./scripts/build.sh`):

```bash
./scripts/build-docker.sh
```

See also [`docs/DEVELOPMENT.md`](../docs/DEVELOPMENT.md) (Docker-only validation). The formatter step uses a named volume for container `~/.m2` (default `ezkey-maven-spotless-cache`) because `spotless:apply` must write back to the Git checkout via a bind mount.

## Direct Docker Compose Usage

You can also use Docker Compose directly:

```bash
cd docker

# Start services
docker-compose up -d

# Stop services
docker-compose stop

# View logs
docker-compose logs -f

# View logs for specific service
docker-compose logs -f admin-api

# Restart a service
docker-compose restart admin-api

# Remove everything
docker-compose down -v
```

## Native Image Mode

### Overview

The Docker stack supports two execution modes:
- **Default Mode (JVM)**: Uses standard Spring Boot JAR files running on JVM
- **Native Mode**: Uses GraalVM native images for faster startup and lower memory usage

### Building Native Images

Before using native mode, you must build the native images:

```bash
# Build admin-api native image
mvn spring-boot:build-image -pl ezkey-admin-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-admin-api-native -DskipTests

# Build auth-api native image
mvn spring-boot:build-image -pl ezkey-auth-api -Pnative \
    -Dspring-boot.build-image.imageName=ezkey-auth-api-native -DskipTests
```

**Note**: Native image builds require significant time (10-30 minutes) and resources. Consider building in CI/CD or during off-hours.

### Using Native Mode

Once native images are built, start the stack with the `--native` flag:

```bash
# Linux/Mac
./docker/start.sh --native

# Windows (PowerShell)
# From a PowerShell prompt in the repo root (recommended):
powershell -NoProfile -ExecutionPolicy Bypass -File .\docker\start.ps1 --native
# Or run directly from PowerShell:
.\docker\start.ps1 --native
```

This uses `docker-compose.native.yml` which references the pre-built native images instead of building from source.

### Performance Comparison

**Startup Time:**
- JVM Mode: ~20-30 seconds (admin-api), ~15-20 seconds (auth-api)
- Native Mode: ~3-5 seconds (admin-api), ~2-3 seconds (auth-api)

**Memory Usage:**
- JVM Mode: ~300-400MB (admin-api), ~200-300MB (auth-api)
- Native Mode: ~80-120MB (admin-api), ~50-100MB (auth-api)

**Cold Start (AWS Lambda):**
- JVM Mode: ~8-12 seconds (admin-api), ~5-10 seconds (auth-api)
- Native Mode: ~200-800ms (admin-api), ~100-500ms (auth-api)

### Native Mode Limitations

1. **Build Time**: Native images take significantly longer to build (10-30 minutes vs 2-5 minutes)
2. **Build Resources**: Requires more CPU and memory during build
3. **Debugging**: Native images are harder to debug than JVM images
4. **Compatibility**: Some Java features may not work in native mode (check GraalVM documentation)

### Switching Between Modes

You can switch between modes by:
1. Stopping the current stack: `./docker/manage.sh stop`
2. Starting with desired mode: `./docker/start.sh` (JVM) or `./docker/start.sh --native` (Native)

**Note**: Both modes use the same PostgreSQL database and volumes, so data persists across mode switches.

## Environment Variables

### Database Configuration

Default values (can be overridden via environment variables):

- `POSTGRES_DB`: `ezkey_db`
- `POSTGRES_USER`: `postgres`
- `POSTGRES_PASSWORD`: `ezkey`

To override, set environment variables before running `start.sh`:

```bash
export POSTGRES_PASSWORD=mysecurepassword
./docker/start.sh
```

### Spring Profiles

All services use the `docker` Spring profile by default, which loads configuration from:
- `application-docker.properties` files in each module's `config/` directory

#### Available Profiles

**Default: `docker` (Production Mode)**
- Rate limiting enabled with production values
- Tests must handle rate limits (synchronization + retry mechanisms)
- Validates production-like behavior

**Optional: `docker-dev` (Local Docker Diagnostics)**
- Enables a richer (but still reasonable) Actuator surface for local analysis
- Intended for local Docker usage only (never expose publicly)
- Recommended for profiling memory/heap via `/actuator/metrics` while iterating on native build tradeoffs
 - Uses a dedicated management port: `8085` (not published by default)

**Optional: `docker-test` (Test Mode)**
- Rate limiting disabled or very permissive (HTTP/API-key limits)
- Peripheral audit-chain heartbeat supervision stays enabled on Auth API and Integration API unless explicitly overridden — stack integrity semantics match production docker profiles for checkpoint staleness gates (default thresholds align with **`latest.window_end + 9 minutes`** for five-minute checkpoints; see **`docs/AUDIT_LOG_INTEGRITY.md`**).
- Allows unrestricted churn testing without 429 noise from rate limits
- Useful for development and debugging

#### Using Test Mode

To start the stack in test mode (permissive rate limiting):

```bash
# Linux/Mac
SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="docker,docker-test"; .\docker\start.ps1
```

**Note:** The profile is set at stack startup and persists for the lifetime of the Docker stack. To change modes, restart the stack with the desired profile.

#### Using Local Docker Diagnostics Mode

To start the stack with richer Actuator diagnostics for local development:

```bash
# Linux/Mac
SPRING_PROFILES_ACTIVE=docker-dev ./docker/start.sh

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="docker-dev"; .\docker\start.ps1
```

To access Actuator from the host on the separate management port (local only), use the compose override:

```bash
# Linux/Mac
SPRING_PROFILES_ACTIVE=docker-dev docker compose -f docker/docker-compose.yml -f docker/docker-compose.docker-dev.yml up -d

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="docker-dev"; docker compose -f docker\docker-compose.yml -f docker\docker-compose.docker-dev.yml up -d
```

#### VisualVM (JMX) - Local Docker Only

For deeper JVM diagnostics (heap, threads, CPU sampling) you can connect VisualVM to the Admin API
and Auth API JVMs via JMX. This is DEV ONLY and intentionally unauthenticated / non-SSL.

**Ports (host):**
- Auth API JMX: `localhost:9010`
- Admin API JMX: `localhost:9011`

**Start with JMX enabled (Docker dev):**

```bash
# Linux/Mac
SPRING_PROFILES_ACTIVE=docker,docker-dev EZKEY_ENABLE_JMX=true ./docker/start.sh

# Windows PowerShell
$env:SPRING_PROFILES_ACTIVE="docker,docker-dev"; $env:EZKEY_ENABLE_JMX="true"; .\docker\start.ps1
```

**VisualVM connection:**
- Add JMX connection to `localhost:9010` (auth-api)
- Add JMX connection to `localhost:9011` (admin-api)

## Data Persistence

### Database Data

PostgreSQL data is persisted in a Docker volume named `postgres-data`. This means:
- Data persists across container restarts
- Data persists when containers are stopped
- Data is removed only when using `clean` command or `docker-compose down -v`

### Viewing Volume Data

```bash
# List volumes
docker volume ls

# Inspect postgres-data volume
docker volume inspect ezkey_postgres-data

# Backup database (from host)
docker exec ezkey-postgres pg_dump -U postgres ezkey_db > backup.sql

# Restore database (from host)
docker exec -i ezkey-postgres psql -U postgres ezkey_db < backup.sql
```

### Encryption Keys (Tink)

**Encryption is enabled by default** in Docker to match production behavior. Sensitive data (private keys) is encrypted at rest using Google Tink.

#### Encryption Setup

Encryption keys are stored in a persistent Docker volume named `encryption-secrets`:
- **Master Key**: `/etc/ezkey/secrets/master.key` - Base64-encoded 256-bit key
- **Keyset**: `/etc/ezkey/keysets/keyset.json.encrypted` - Tink keyset encrypted with master key

Both `admin-api` and `auth-api` share the same encryption keys for data compatibility.

#### First-Time Setup

Before first startup, generate the master key:

```bash
# Linux/Mac
./docker/generate-encryption-keys.sh

# Windows (Git Bash)
bash docker/generate-encryption-keys.sh
```

This script:
1. Creates the `encryption-secrets` volume if it doesn't exist (Docker Compose will prefix it as `ezkey_encryption-secrets`)
2. Generates a cryptographically secure master key
3. Sets proper file permissions (600)

**Note**: The keyset file will be automatically generated on first startup by `TinkKeyManager` if it doesn't exist.

#### Encryption Behavior

- **First Boot** (master key exists, keyset missing):
  - `TinkKeyManager` automatically generates a new keyset
  - Keyset is encrypted with the master key and saved to the volume

- **Subsequent Starts** (both files exist):
  - `TinkKeyManager` loads the existing keyset
  - Encryption continues seamlessly

- **Master Key Missing**:
  - Application logs a warning
  - Encryption is disabled (backward compatible mode)
  - Data is stored in plaintext

#### Volume Persistence

The encryption keys volume persists across:
- Container restarts (`docker-compose restart`)
- Container stops (`docker-compose stop`)
- Stack shutdown (`docker-compose down`)

**Warning**: The volume is removed when using `clean` command or `docker-compose down -v`. **Backup the master key before removing volumes!**

**Note**: The `encryption-secrets` volume is managed by Docker Compose and will be automatically created if it doesn't exist. It will be removed with `docker-compose down -v` along with other volumes.

#### Backup Encryption Keys

```bash
# Backup master key (from host)
# Note: Use the actual volume name with Docker Compose prefix: ezkey_encryption-secrets
docker run --rm -v ezkey_encryption-secrets:/data alpine tar czf - /data/secrets/master.key | gzip > master-key-backup.tar.gz

# Restore master key (from host)
gunzip -c master-key-backup.tar.gz | docker run --rm -i -v ezkey_encryption-secrets:/data alpine tar xzf - -C /data
```

#### Security Notes

- Master key file has 600 permissions (owner read/write only)
- Keyset is encrypted with the master key using AES-256-GCM
- Both APIs use the same keys for shared data decryption
- Keys persist in Docker volume (backup recommended for production)

## Network Configuration

All services run on a bridge network named `ezkey-network`. Services can communicate using their service names:

- `postgres` - Database hostname
- `admin-api` - Admin API hostname
- `auth-api` - Auth API hostname
- `crypto-api` - Crypto API hostname
- `demo-device` - Demo Device hostname

## Troubleshooting

### Services Won't Start

1. **Check Docker is running:**
   ```bash
   docker info
   ```

2. **Check for port conflicts:**
   ```bash
   # Linux/Mac
   lsof -i :9080 -i :8080 -i :9090 -i :8083 -i :5432

   # Windows
   netstat -ano | findstr "9080 8080 9090 8083 5432"
   ```

3. **View service logs:**
   ```bash
   ./docker/manage.sh logs
   ```

### Database Connection Issues

1. **Check PostgreSQL is healthy:**
   ```bash
   docker exec ezkey-postgres pg_isready -U postgres
   ```

2. **Check migration completed:**
   ```bash
   docker logs ezkey-migration
   ```

3. **Verify network connectivity:**
   ```bash
   docker exec ezkey-admin-api ping postgres
   ```

### Build Failures

1. **Clean build cache:**
   ```bash
   docker system prune -a
   ```

2. **Rebuild without cache:**
   ```bash
   ./docker/manage.sh build
   ```

### Health Checks Failing

1. **Check service is running:**
   ```bash
   docker ps
   ```

2. **Check service logs:**
   ```bash
   ./docker/manage.sh logs <service-name>
   ```

3. **Manually test health endpoint:**
   ```bash
   curl http://localhost:9080/actuator/health
   ```

### Port Already in Use

If a port is already in use, you can modify `docker-compose.yml` to use different ports:

```yaml
ports:
  - "9081:9080"  # Use 9081 on host instead of 9080
```

## Development Workflow

### Making Code Changes

1. **Stop services:**
   ```bash
   ./docker/manage.sh stop
   ```

2. **Make code changes**

3. **Rebuild and restart:**
   ```bash
   ./docker/manage.sh build
   ./docker/manage.sh start
   ```

### Testing Changes

1. **View logs in real-time:**
   ```bash
   ./docker/manage.sh logs admin-api
   ```

2. **Test API endpoints:**
   ```bash
   curl http://localhost:9080/actuator/health
   ```

3. **Access Swagger UI:**
   - Open http://localhost:9080/swagger-ui/index.html in browser

## Production Considerations

⚠️ **This Docker setup is designed for development, QA, and demonstrations.**

For production deployment, consider:

1. **Security:**
   - Change default passwords
   - Use secrets management (Docker secrets, Kubernetes secrets)
   - Enable TLS/HTTPS
   - Configure firewall rules

2. **Performance:**
   - Use production-grade PostgreSQL configuration
   - Configure JVM memory limits
   - Set appropriate resource limits

3. **Monitoring:**
   - Add monitoring and logging solutions
   - Configure health check endpoints
   - Set up alerting

4. **High Availability:**
   - Use orchestration platform (Kubernetes, Docker Swarm)
   - Configure database replication
   - Set up load balancing

5. **Backup:**
   - Implement automated database backups
   - Test restore procedures
   - Store backups securely

## Additional Resources

- [Main README](../README.md) - Project overview and primary quick start
- [Functional Tests README](../ezkey-tests/README.md) - Recommended clean-start workflow
- [API Endpoints](../docs/ENDPOINT.md) - Detailed API reference
- [Architecture](../docs/ARCHITECTURE.md) - System architecture documentation

## Support

For issues or questions:
- Check the troubleshooting section above
- Review service logs: `./docker/manage.sh logs`
- Check the main project documentation
- Open an issue on GitHub

