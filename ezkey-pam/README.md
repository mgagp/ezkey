# Ezkey PAM Module

PAM module for SSH integration with the Ezkey MFA system via the **M2M API** (port 7080).
When a user connects over SSH, the module:
1. Creates an auth attempt on the M2M API (`POST /api/v1/auth-attempts`) using the Linux username as `userIdentifier`.
2. Waits for the user to approve or reject from the demo-device UI (`GET /api/v1/auth-attempts/{id}/wait`).
3. Grants or denies SSH access based on the response.

## Prerequisites

- Rocky Linux 10 (or compatible RHEL 10 system)
- `gcc`, `make`, `pam-devel`, `libcurl-devel`, `cjson-devel`
- Running Ezkey stack with M2M API accessible
- An active API key pair (integration key + secret key)
- An enrollment with `userIdentifier` matching the Linux username (`testuser`)

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `EZKEY_M2M_API_URL` | `http://localhost:7080` | M2M API base URL |
| `EZKEY_INTEGRATION_KEY` | *(required)* | API integration key |
| `EZKEY_SECRET_KEY` | *(required)* | API secret key |

## Quick Start (Docker)

```bash
# 1. Start the main Ezkey stack
cd docker && ./manage.sh start

# 2. In the Admin UI, create an integration and generate an API key
open http://localhost:9080   # Admin UI
# -> Integrations -> New integration -> Generate API key
# -> Enrollments -> New enrollment -> set userIdentifier=testuser

# 3. Start the PAM container
cd ezkey-pam
EZKEY_INTEGRATION_KEY=<key> EZKEY_SECRET_KEY=<secret> docker-compose up --build

# 4. SSH into the PAM container
ssh -p 2222 testuser@localhost

# 5. Approve the auth attempt in the demo-device UI
open http://localhost:8083/phone/ezkey
```

## E2E Test Procedure

```
1. cd docker && ./manage.sh clean          # Clean start
2. cd docker && ./manage.sh start          # Start the full stack
3. Open the Admin UI at http://localhost:9080
4. Create an integration and generate an API key
   -> Integrations -> New integration -> Generate API key
   -> Note the integration key and secret key
5. Create an enrollment for the test user
   -> Enrollments -> New enrollment -> userIdentifier = testuser
6. cd ezkey-pam
7. EZKEY_INTEGRATION_KEY=<key> EZKEY_SECRET_KEY=<secret> docker-compose up --build
8. ssh -p 2222 testuser@localhost           # SSH triggers PAM -> POST /api/v1/auth-attempts
9. Open http://localhost:8083/phone/ezkey   # Demo-device UI
10. Check pending -> Approve                # Demo-device approves the auth attempt
11. SSH session completes successfully      # PAM received ACCEPTED from wait API
```

## Manual Installation (bare metal)

```bash
# Install dependencies
sudo dnf install -y gcc make pam-devel libcurl-devel epel-release
sudo dnf install -y cjson-devel

# Build and install
export EZKEY_M2M_API_URL=http://your-m2m-api:7080
export EZKEY_INTEGRATION_KEY=your-integration-key
export EZKEY_SECRET_KEY=your-secret-key
sudo ./install.sh
```

## PAM Configuration

The `/etc/pam.d/sshd` auth line:
```
auth  required  pam_ezkey.so debug
```
Only the `debug` argument is supported (enables verbose logging).

## Logging

```bash
# Real-time auth logs
tail -f /var/log/secure

# Short-form log from within the container
docker exec ezkey-pam-test cat /tmp/pam_ezkey.out
docker exec ezkey-pam-test cat /tmp/pam_ezkey.err
```

Example log output:
```
=== EZKEY PAM MODULE STARTED ===
Authentication requested for user: testuser
Auth attempt created, id=42
=== EZKEY PAM MODULE FINISHED ===
Authentication result for testuser: SUCCESS
```

## Testing

```bash
# Run basic tests from within the container
docker exec ezkey-pam-test /opt/pam-ezkey/test/test_pam.sh
```

## Troubleshooting

### `PAM_AUTH_ERR` immediately
- Check that `EZKEY_INTEGRATION_KEY` and `EZKEY_SECRET_KEY` are set in the container environment.
- Verify M2M API is reachable: `curl http://m2m-api:7080/actuator/health`
- Check that an enrollment with `userIdentifier=testuser` exists.

### SSH connection hangs forever
- The wait API has a 30-second timeout by default (`EZKEY_WAIT_TIMEOUT`).
  Make sure you approve/reject in the demo-device UI within that window.

### No logs in `/var/log/secure`
```bash
# Check rsyslog is running
pgrep rsyslogd || rsyslogd
```

### Module not found
```bash
ls -la /lib64/security/pam_ezkey.so
make clean && make all && sudo cp build/pam_ezkey.so /lib64/security/
```

## Directory Structure

```
ezkey-pam/
├── src/
│   ├── pam_ezkey.c          # PAM module source
│   └── ezkey_config.h       # Compile-time defaults and env var names
├── config/
│   └── pam_ezkey.conf       # Reference config (credentials via env vars)
├── test/
│   └── test_pam.sh          # Basic test suite
├── build/                   # Generated build files
├── Makefile
├── install.sh               # Automated installer
├── docker-entrypoint.sh     # Container startup script
├── Dockerfile
├── docker-compose.yml
└── sshd                     # PAM sshd configuration
```

