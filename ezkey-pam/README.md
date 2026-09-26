# Ezkey PAM Module

**Experimental.** Personal lab for SSH MFA against a running Ezkey stack. Not on the
September 2026 operable-release roadmap. Keep PAM facts in this folder; do not add PAM to
`docs/` or `docker/README.md`.

Internal lab evaluation only — experimental SSH MFA dogfood via ezkey-pam; not a product
feature, not on the public alpha/community path, and not operable roadmap for Sept 2026.

Current status, product geometry, and what “operable” would mean (EXP1 dogfood vs generic
Linux extract): [`STATUS.md`](STATUS.md).

Linux PAM module that calls the **Integration API** (port 7080, Docker service
`integration-api`). When a user connects over SSH, the module:

1. Reads runtime settings from `/etc/security/pam_ezkey.conf` (written at container start).
2. Creates an auth attempt (`POST /api/v1/auth-attempts`) using the Linux username as
   `userIdentifier`, with `challengeRequested=false`.
3. Waits for approve or reject (`GET /api/v1/auth-attempts/{id}/wait`).
4. Grants SSH access when wait `status` is `ACCEPTED`.

The Linux username must match a **verified** enrollment `userIdentifier` on the integration
that owns the API key.

## What changed in 2.1

Slices 1–3 from [`STATUS.md`](STATUS.md) §2.8 (lab eval):

- **Hygiene:** syslog/`LOG_AUTHPRIV` only for host path; no wait JSON / proof tokens /
  secrets in logs; `/tmp` transcripts only when `debug=true`; `debug` defaults **false** in
  sample conf; explicit conf-open / missing-key errors; curl init/cleanup + secret wipe.
- **PAM return codes:** fail-closed map (`PAM_AUTHINFO_UNAVAIL` for transport/config,
  `PAM_AUTH_ERR` for reject/expire, never `PAM_IGNORE` / success on API failure). Host stack
  sketch: [`sshd.host-sketch`](sshd.host-sketch).
- **Amazon Linux 2023:** dedicated builder + extract + mirror/smoke path (ABI-matched `.so`;
  do not copy the Rocky binary onto AL2023).

## Prerequisites

- Running Ezkey stack (`cd ezkey-tests && ./clean-start.sh`)
- Docker Compose
- An enrollment whose `userIdentifier` matches the SSH user (default `testuser`)

## Quick start (Docker — Rocky lab demo)

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

## Amazon Linux 2023 builder and mirror

AL2023 has no `cjson` package; the builder compiles a pinned cJSON release, then links
`pam_ezkey.so` for the AL2023 ABI.

### Extract the `.so` only

```bash
cd ezkey-pam
./scripts/build-al2023.sh --extract
# → artifacts/pam_ezkey-al2023.so

# Equivalent manual steps:
docker build -f Dockerfile.al2023 --target builder-al2023 -t ezkey-pam-builder-al2023:local .
docker create --name ezkey-pam-extract-al2023 ezkey-pam-builder-al2023:local
docker cp ezkey-pam-extract-al2023:/src/build/pam_ezkey.so ./pam_ezkey-al2023.so
docker rm ezkey-pam-extract-al2023
```

On an AL2023 host, install under `/usr/lib64/security/`, conf `0600` at
`/etc/security/pam_ezkey.conf`, and add a PAM line (see `sshd.host-sketch`). **Do not**
copy a Rocky-built `.so` onto AL2023.

### Smoke (module load, no Ezkey stack)

```bash
./scripts/smoke-al2023.sh
```

### SSH mirror (approve/reject/timeout against Integration API)

Same provisioned `.env` as the Rocky demo; host port defaults to **2223**:

```bash
./scripts/up-al2023.sh
ssh -p 2223 \
  -o PreferredAuthentications=keyboard-interactive \
  -o PubkeyAuthentication=no \
  testuser@127.0.0.1
```

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
| `debug` | `false` | Extra syslog + `/tmp` transcripts (demo may set `true`) |

Environment overrides (Docker): `EZKEY_INTEGRATION_API_URL` (legacy:
`EZKEY_M2M_API_URL`), `EZKEY_INTEGRATION_KEY`, `EZKEY_SECRET_KEY`,
`EZKEY_WAIT_TIMEOUT`, `EZKEY_DEBUG`.

PAM arguments: `debug` and `conf=/path/to/file`.

## Docker layout

| Service | Role |
|---|---|
| `pam-builder` | Rocky compile-only (`--profile build`) |
| `ezkey-pam-ssh` | Rocky Linux SSH VM with `pam_ezkey.so` (port 2222) |
| `pam-builder-al2023` | AL2023 compile-only (`--profile al2023`) |
| `ezkey-pam-ssh-al2023` | AL2023 SSH mirror (port 2223, `--profile al2023`) |

The SSH VMs join the main stack network (`ezkey_ezkey-network` by default) so they can reach
`http://integration-api:7080`.

## Logging

Host-oriented path: `authpriv` syslog only (`/var/log/secure` in the demo images).

Demo debug (`debug=true` / `EZKEY_DEBUG=true`):

```bash
docker exec ezkey-pam-ssh cat /tmp/pam_ezkey.out
docker exec ezkey-pam-ssh cat /tmp/pam_ezkey.err
docker exec ezkey-pam-ssh tail -n 50 /var/log/secure
```

Logs never include wait JSON bodies, proof tokens, secret keys, or Basic auth headers.

## Troubleshooting

### Immediate deny / `PAM_AUTHINFO_UNAVAIL`
- Confirm `/etc/security/pam_ezkey.conf` inside the container has both keys.
- Conf open failures are syslog’d explicitly; missing keys return `PAM_AUTHINFO_UNAVAIL`.
- `curl http://integration-api:7080/actuator/health` from the SSH VM.
- Confirm a **VERIFIED** enrollment exists with `userIdentifier=testuser` on that integration.

### Reject / expire (`PAM_AUTH_ERR`)
- Approve on Demo Device within `wait_timeout` (default 90s).
- Open the PAM enrollment card, not the bootstrap `admin.docker` card.

### API key 401 / IP whitelist
The demo key is created without an IP whitelist. If you add one, include the Docker bridge
range (`172.16.0.0/12`) used by the SSH VM.

## Directory structure

```
ezkey-pam/
├── src/                 PAM module (C)
├── config/              Reference pam_ezkey.conf (debug=false)
├── scripts/             provision, Docker up, AL2023 build/smoke, SSH demo
├── test/                Container self-checks + AL2023 smoke
├── Dockerfile           Rocky builder + SSH runtime
├── Dockerfile.al2023    AL2023 builder + smoke + SSH mirror
├── docker-compose.yml
├── sshd / sshd_config   PAM + sshd for the demo VMs (Ezkey-only)
└── sshd.host-sketch     EXP1-style stack sketch (not applied live)
```
