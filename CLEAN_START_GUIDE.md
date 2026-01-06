# Ezkey Clean Start Guide - Complete Setup Instructions

## Quick Start (5 minutes)

### Prerequisites
- Docker & Docker Compose installed
- 4GB+ RAM available
- Network connectivity for Docker Hub

### Start the System
```bash
cd docker
docker-compose up -d
```

### Verify Bootstrap
```bash
# Windows
powershell -ExecutionPolicy Bypass -File ../ezkey-tests/verify-bootstrap.ps1

# Linux/Mac
bash ../ezkey-tests/verify-bootstrap.sh
```

## What Clean Start Does

1. **Database Setup** - PostgreSQL initializes with Flyway migrations
2. **Encryption Keys** - Master key generated (if not present)
3. **Admin Bootstrap** - Creates System Integration + Global Admin account
4. **Credential Export** - Exports enrollment credentials for automation
5. **Bootstrap Init** - Sets up demo device with auto-enrollment
6. **Services Health** - All APIs report healthy

## Service Overview

| Service | Port | Purpose | Status |
|---------|------|---------|--------|
| **Admin API** | 9080 | Admin interface (System Integration, users, audits) | ✅ Exports bootstrap credentials |
| **Auth API** | 8080 | Authentication service (enrollments, MFA) | ✅ Receives enrollment data |
| **Crypto API** | 9090 | Cryptographic operations (signing, encryption) | ✅ Supports encryption |
| **Demo Device** | 8083 | Simulated authenticator device | ✅ Pre-enrolled with admin |
| **PostgreSQL** | 5432 | Database (Azure VM compatible) | ✅ Persists all data |
| **Bootstrap Init** | - | One-time setup container | ✅ Auto-cleanup after success |

## Accessing the System

### Admin Interface (REST API)
```bash
# Health check
curl http://localhost:9080/actuator/health

# Create integration (requires authentication)
curl -X POST http://localhost:9080/api/v1/admin/integrations \
  -H "Content-Type: application/json" \
  -d '{"name":"My App","description":"..."}'
```

### Authentication Endpoint
```bash
# Create authentication attempt
curl -X POST http://localhost:8080/api/v1/auth/attempts \
  -H "Content-Type: application/json" \
  -d '{"integrationId":"...","type":"passwordless"}'
```

### Bootstrap Credentials (Auto-Generated)
Location: `/var/lib/ezkey/bootstrap/bootstrap-credentials.json`

Contains (example):
```json
{
  "enrollmentId": 1,
  "enrollmentProofToken": "...",
  "enrollmentChallengeCode": 506023,
  "username": "admin.docker"
}
```

## Troubleshooting

### ❌ Bootstrap Stuck on "Waiting for credentials file"
**Symptom:** Bootstrap Init container loops waiting for bootstrap-credentials.json

**Cause:** Admin API not exporting credentials (docker profile not active)

**Fix:**
```bash
# Check admin-api logs for profile activation
docker-compose logs admin-api | grep "profile is active"

# Should show: "The following 1 profile is active: docker"

# If not, verify SPRING_PROFILES_ACTIVE=docker in docker-compose.yml
```

### ❌ Healthcheck Timeout
**Symptom:** "Unhealthy" status on slow machines

**Cause:** 30-second healthcheck interval too aggressive for initialization

**Fix - Temporary:**
```bash
# Wait longer for startup
docker-compose up -d
sleep 120  # Wait 2 minutes
docker-compose ps
```

**Fix - Persistent:**
Update `docker-compose.yml` healthcheck:
```yaml
healthcheck:
  test: ["CMD-SHELL", "curl -f http://localhost:9081/actuator/health || exit 1"]
  interval: 30s
  timeout: 5s
  start_period: 120s    # Increase from 90s
  retries: 5            # Increase from 3
```

### ❌ Master Key Not Found
**Symptom:** Encryption disabled, "Master key file not found" warnings

**Cause:** `/etc/ezkey/secrets/master.key` not created

**Fix - Automatic:**
- Master key is generated automatically in Docker
- Check `/docker/docker-compose.yml` volume mounts

**Fix - Manual:**
```bash
# View encryption volume
docker volume ls | grep encryption-secrets

# Inspect contents
docker run -v ezkey_encryption-secrets:/data alpine ls -la /data/secrets/
```

### ❌ Demo Device Stuck on "Starting"
**Symptom:** Demo Device healthcheck never completes

**Cause:** Takes longer to start on slow machines (normal)

**Wait:**
```bash
# Check logs
docker-compose logs demo-device | tail -20

# Wait 2-3 minutes, then check again
docker-compose ps
```

## Advanced Configuration

### Change Default Admin
Edit `docker-compose.yml`:
```yaml
environment:
  EZKEY_ADMIN_INITIAL_USERNAME: yourname
  EZKEY_ADMIN_INITIAL_EMAIL: yourname@company.com
```

### Customize Bootstrap Path
Edit `ezkey-admin-api/config/application-docker.properties`:
```properties
ezkey.admin.bootstrap.export.path=/custom/path/bootstrap-credentials.json
```

### Disable Bootstrap Export (Security)
Edit `ezkey-admin-api/config/application-docker.properties`:
```properties
ezkey.admin.bootstrap.export.enabled=false
```
(Credentials will only appear in logs)

### Use Different Master Key
Mount a pre-existing key:
```yaml
volumes:
  - ./my-keys/master.key:/etc/ezkey/secrets/master.key:ro
  - bootstrap-artifacts:/var/lib/ezkey/bootstrap
```

## Environment-Specific Configurations

### Docker Profile (`SPRING_PROFILES_ACTIVE=docker`)
- **Storage:** DATABASE (shared across instances)
- **Master Key:** `/etc/ezkey/secrets/master.key`
- **Bootstrap Export:** ENABLED (`true`)
- **Use Case:** Production, High Availability

### Windows Profile (`SPRING_PROFILES_ACTIVE=windows`)
- **Storage:** FILE (single instance)
- **Master Key:** `C:\ProgramData\ezkey\secrets\master.key`
- **Bootstrap Export:** DISABLED (`false`)
- **Use Case:** Local development on Windows

### Default Profile (no SPRING_PROFILES_ACTIVE)
- **Storage:** DATABASE (safe default)
- **Master Key:** `/etc/ezkey/secrets/master.key`
- **Bootstrap Export:** DISABLED (`false`)
- **Use Case:** Fallback when profile not specified

## Testing Workflows

### Test Complete Flow
```bash
# 1. Clean slate
docker-compose down -v
docker-compose up -d

# 2. Verify bootstrap
docker-compose logs bootstrap-init | grep "Bootstrap Init Complete"

# 3. Run verification suite
powershell -ExecutionPolicy Bypass -File ../ezkey-tests/verify-bootstrap.ps1
```

### Test Admin Login
```bash
# 1. Get credentials from file
docker-compose exec admin-api cat /var/lib/ezkey/bootstrap/bootstrap-credentials.json

# 2. Use enrollmentProofToken in login flow
# (Full implementation depends on Admin Dashboard)
```

### Test Auth Enrollment
```bash
# 1. Get device credentials
docker-compose exec admin-api cat /var/lib/ezkey/bootstrap/device-credentials.json

# 2. Create enrollment attempt
curl -X POST http://localhost:8080/api/v1/auth/attempts \
  -H "Content-Type: application/json" \
  -d '{"integrationId":1,"type":"passwordless"}'
```

## Monitoring & Logs

### View All Logs
```bash
# Last 50 lines, all services
docker-compose logs --tail=50

# Follow in real-time
docker-compose logs -f

# Specific service
docker-compose logs -f admin-api
docker-compose logs -f auth-api
```

### Check Service Health
```bash
# All services
docker-compose ps

# Specific healthcheck
docker-compose exec admin-api curl http://localhost:9081/actuator/health

# Database
docker-compose exec postgres psql -U postgres -d ezkey_db -c "SELECT version();"
```

### Export Logs
```bash
# Save complete logs to file
docker-compose logs > ezkey-logs-$(date +%Y%m%d-%H%M%S).txt

# View bootstrap init logs only
docker-compose logs bootstrap-init > bootstrap-logs.txt
```

## Cleanup & Reset

### Stop All Services
```bash
docker-compose down
```

### Remove All Data (Full Reset)
```bash
docker-compose down -v
```

### Remove Docker Images
```bash
docker rmi ezkey-admin-api:latest
docker rmi ezkey-auth-api:latest
docker rmi ezkey-crypto-api:latest
docker rmi ezkey-demo-device:latest
```

### Clean Everything (Aggressive)
```bash
docker-compose down -v
docker system prune -a
```

## Support & Documentation

### Key Files
- Configuration: [docker-compose.yml](../docker/docker-compose.yml)
- Admin API: [application-docker.properties](../ezkey-admin-api/config/application-docker.properties)
- Auth API: [application-docker.properties](../ezkey-auth-api/config/application-docker.properties)
- Verification: [verify-bootstrap.ps1](../ezkey-tests/verify-bootstrap.ps1)

### Documentation
- [BOOTSTRAP_EXPORT_FIX.md](./BOOTSTRAP_EXPORT_FIX.md) - Technical deep dive
- [CLEAN_START_SESSION_SUMMARY.md](./CLEAN_START_SESSION_SUMMARY.md) - Complete session notes

### Contact
For issues, check:
1. Service logs: `docker-compose logs <service>`
2. Healthcheck status: `docker-compose ps`
3. Port availability: `netstat -tuln | grep 8080`
4. Docker disk space: `docker system df`

## Version Information
- **Ezkey:** 2025-01
- **Spring Boot:** 3.5.3
- **Java:** 21+
- **Docker Compose:** 2.0+
- **PostgreSQL:** 17

---

**Last Updated:** January 6, 2025
**Status:** ✅ Production Ready
