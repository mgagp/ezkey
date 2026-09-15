# Ezkey PAM Module

**Experimental.** Personal lab for SSH MFA against a running Ezkey stack. Not on the
September 2026 operable-release roadmap. Keep PAM facts in this folder; do not add PAM to
`docs/` or `docker/README.md`.

Linux PAM module that calls the **Integration API** (port 7080, Docker service
`integration-api`). When a user connects over SSH, the module:

1. Reads runtime settings from `/etc/security/pam_ezkey.conf` (written at container start).
2. Creates an auth attempt (`POST /api/v1/auth-attempts`) using the Linux username as
   `userIdentifier`, with `challengeRequested=false`.
3. Waits for approve or reject (`GET /api/v1/auth-attempts/{id}/wait`).
4. Grants SSH access when wait `status` is `ACCEPTED`.

The Linux username must match a **verified** enrollment `userIdentifier` on the integration
that owns the API key.

## What changed in 2.x

The original lab module (2025) talked to a preliminary API, left `pam_ezkey.conf` unparsed,
and omitted the now-required `challengeRequested` field. This version:

- Loads API URL, keys, and timeouts from a config file at PAM runtime
- Accepts Docker env vars as overrides (entrypoint writes the file, because `sshd` does not
  pass container ENV into PAM)
- Speaks the current Integration API contract
- Builds the `.so` in a dedicated Docker builder stage, then runs a slim SSH demo VM

Configuration stays small on purpose: URL, key pair, wait timeout, optional context title.

## Prerequisites

- Running Ezkey stack (`cd ezkey-tests && ./clean-start.sh`)
- Docker Compose
- An enrollment whose `userIdentifier` matches the SSH user (default `testuser`)

## Quick start (Docker)

```bash
cd ezkey-tests && ./clean-start.sh

cd ../ezkey-pam
./scripts/provision.sh    # integration + API key + Demo Device enrollment
./scripts/up.sh           # compile PAM in Docker and start the SSH VM
./scripts/demo-ssh.sh     # ssh + approve on Demo Device
```

Manual SSH:

```bash
ssh -p 2222 \
  -o PreferredAuthentications=keyboard-interactive \
  -o PubkeyAuthentication=no \
  testuser@127.0.0.1
```

Then open http://localhost:8083/phone/ezkey , open the **SSH testuser** enrollment, and
Approve.

## Runtime configuration

`/etc/security/pam_ezkey.conf` (also `config/pam_ezkey.conf` in this folder):

| Key | Default | Description |
|---|---|---|
| `integration_api_url` | `http://localhost:7080` | Integration API base URL |
| `integration_key` | *(required)* | API integration key |
| `secret_key` | *(required)* | API secret key |
| `wait_timeout` | `90` | Seconds to wait for device approval |
| `wait_polling` | `2` | Wait poll interval |
| `api_timeout` | `10` | Timeout for the create call |
| `challenge_requested` | `false` | Extra numeric challenge (off for SSH demo) |
| `context_title` | `SSH login` | Title shown on the device |
| `context_message` | *(generated)* | Message shown on the device |
| `debug` | `true` | Extra syslog |

Environment overrides (Docker): `EZKEY_INTEGRATION_API_URL` (legacy:
`EZKEY_M2M_API_URL`), `EZKEY_INTEGRATION_KEY`, `EZKEY_SECRET_KEY`,
`EZKEY_WAIT_TIMEOUT`.

PAM arguments: `debug` and `conf=/path/to/file`.

## Docker layout

| Service | Role |
|---|---|
| `pam-builder` | Compile-only image (`docker compose --profile build build pam-builder`) |
| `ezkey-pam-ssh` | Rocky Linux SSH VM with `pam_ezkey.so` |

The SSH VM joins the main stack network (`ezkey_ezkey-network` by default) so it can reach
`http://integration-api:7080`.

## Logging

```bash
docker exec ezkey-pam-ssh cat /tmp/pam_ezkey.out
docker exec ezkey-pam-ssh cat /tmp/pam_ezkey.err
docker exec ezkey-pam-ssh tail -n 50 /var/log/secure
```

## Troubleshooting

### Immediate `PAM_AUTH_ERR`
- Confirm `/etc/security/pam_ezkey.conf` inside the container has both keys.
- `curl http://integration-api:7080/actuator/health` from the SSH VM.
- Confirm a **VERIFIED** enrollment exists with `userIdentifier=testuser` on that integration.

### SSH hangs then fails
- Approve on Demo Device within `wait_timeout` (default 90s).
- Open the PAM enrollment card, not the bootstrap `admin.docker` card.

### API key 401 / IP whitelist
The demo key is created without an IP whitelist. If you add one, include the Docker bridge
range (`172.16.0.0/12`) used by the SSH VM.

## Directory structure

```
ezkey-pam/
├── src/                 PAM module (C)
├── config/              Reference pam_ezkey.conf
├── scripts/             provision, Docker up, SSH demo
├── test/                Container self-checks
├── Dockerfile           Builder + SSH runtime
├── docker-compose.yml
└── sshd / sshd_config   PAM + sshd for the demo VM
```
