# Ezkey PAM Module

**Experimental.** Personal lab for SSH MFA against a running Ezkey stack. Not on the
September 2026 operable-release roadmap. Keep PAM facts in this folder; do not add PAM to
`docs/` or `docker/README.md`.

Internal lab evaluation only — experimental SSH MFA dogfood via ezkey-pam; not a product
feature, not on the public alpha/community path, and not operable roadmap for Sept 2026.

Current status, product geometry, and what “operable” would mean (Lightsail dogfood vs generic
Linux extract): [`STATUS.md`](STATUS.md).

Linux PAM module that calls the **Integration API** (port 7080, Docker service
`integration-api`). When a user connects over SSH, the module:

1. Reads runtime settings from `/etc/security/pam_ezkey.conf` (written at container start).
2. Creates an auth attempt (`POST /api/v1/auth-attempts`) using the Linux username as
   `userIdentifier`, with `challengeRequested=false` and optional `ttlSeconds` equal to the
   effective wait (servers that ignore the field remain compatible).
3. Waits for approve or reject (`GET /api/v1/auth-attempts/{id}/wait?timeout=N`), aligning `N`
   on the applied TTL duration (monotonic budget) — never on host clock vs server `expiresAt`.
4. Grants SSH access when wait `status` is `ACCEPTED`. On any non-accepted exit after create,
   best-effort `POST …/cancel` (failure is logged; PAM outcome unchanged). Cancel is not
   guaranteed when the wait timeout is reached; the real bound is the applied TTL.

The Linux username must match a **verified** enrollment `userIdentifier` on the integration
that owns the API key.

## What changed in 2.2 / 2.2.1 (M1)

On top of 2.1 slices 1–3 ([`STATUS.md`](STATUS.md) § M1):

- **One attempt line** in `authpriv` / `/var/log/secure` per authenticate:
  `pam_ezkey: attempt user=… authAttemptId=… result=… pam_code=… latency_ms=… lab_http=…`
  where `result` distinguishes `SUCCESS` / `EXPIRED` / `TIMEOUT` / `DENIED` / `UNAVAIL` / …
  (never secrets / proof / bodies). Debug chatter stays in `/var/log/ezkey-pam/` only.
- **Conf credentials** (`integration_api_url`, `integration_key`, `secret_key`) only from
  the conf file; must be `root:root` with mode exactly `0600` or `0400` or the module
  returns `PAM_AUTHINFO_UNAVAIL`. No compiled-in URL default (empty URL → `UNAVAIL`).
- **No process-ENV overrides** inside the `.so` (conf path / debug / timeouts). Docker
  compose ENV is applied only by `docker-entrypoint.sh` when writing the conf file.
- **Demo rsyslog:** entrypoint enables `imuxsock` so authpriv reaches `/var/log/secure`.
- Tests: `./scripts/test-observability.sh`, `./scripts/test-conf-security.sh`.

## What changed in 2.1

Slices 1–3 from [`STATUS.md`](STATUS.md) §2.8 (lab eval):

- **Hygiene:** syslog/`LOG_AUTHPRIV` only for host path; no wait JSON / proof tokens /
  secrets in logs; debug file transcripts under `/var/log/ezkey-pam/` only when
  `debug=true` (#818, not `/tmp`); `debug` defaults **false** in sample conf; explicit
  conf-open / missing-key errors; curl init/cleanup + secret wipe.
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
| `integration_api_url` | *(required; no default)* | Integration API base URL |
| `integration_key` | *(required)* | API integration key |
| `secret_key` | *(required)* | API secret key |
| `wait_timeout` | `90` | Seconds to wait for device approval (clamped **15–90**). Sent as create `ttlSeconds` and as `/wait?timeout=` after applied-TTL align |
| `wait_polling` | `2` | Wait poll interval |
| `api_timeout` | `10` | Timeout for create/cancel calls (max 10) |
| `challenge_requested` | `false` | Two-digit number-matching; `true` shows code via SSH `pam_prompt` before wait |
| `lab_http` | `false` | Allow `http://` Integration API URL (Docker entrypoint sets `true`) |
| `context_title` | `SSH login` | Title shown on the device |
| `context_message` | *(generated)* | Message shown on the device |
| `debug` | `false` | Extra filelog `/var/log/ezkey-pam/debug.{out,err}` (Docker lab entrypoint sets `true`) |

**Default `wait_timeout` is 90 s** (`EZKEY_DEFAULT_WAIT_TIMEOUT` in `ezkey_config.h`). Override it via:

- `pam.d` module argument `wait_timeout=N` (overrides conf),
- `/etc/security/pam_ezkey.conf` key `wait_timeout=…`, or
- Docker/lab env `EZKEY_WAIT_TIMEOUT` (applied only when `docker-entrypoint.sh` writes the conf — the `.so` does not read process ENV).

**Docker entrypoint only** (not read by `pam_ezkey.so`): `EZKEY_INTEGRATION_API_URL`
(legacy `EZKEY_M2M_API_URL`), `EZKEY_INTEGRATION_KEY`, `EZKEY_SECRET_KEY`,
`EZKEY_WAIT_TIMEOUT`, `EZKEY_WAIT_POLLING`, `EZKEY_API_TIMEOUT`, `EZKEY_DEBUG`,
optional `EZKEY_CONFIG_FILE` (path the entrypoint writes). The module ignores process
ENV so `su`/`sudo` cannot redirect conf path, debug, or timeouts.

PAM arguments (root-owned `pam.d`): `debug`, `conf=/path/to/file`, and optional `wait_timeout=N`.

**Stack note:** `PAM_AUTHINFO_UNAVAIL` is fail-closed inside the module. A host line
marked `sufficient` or `[default=ignore]` can still make MFA fail-open — keep
`required` / `requisite` unless that is intentional (see [`STATUS.md`](STATUS.md)).

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

Every authenticate emits **exactly one** `authpriv` line (see [`STATUS.md`](STATUS.md) § M1):

```bash
docker exec ezkey-pam-ssh grep 'pam_ezkey: attempt ' /var/log/secure
```

**Amazon Linux 2023 hosts:** authpriv lines go to **journald**, not `/var/log/secure`:

```bash
journalctl -t pam_ezkey
# or
journalctl -t pam_ezkey -u sshd --since '10 min ago'
```

Demo Docker images (Rocky / AL2023 SSH VMs) still use rsyslog → `/var/log/secure` because
containers have no systemd journal.

When `debug=true` (Docker lab default via entrypoint), the module also appends to root-owned
files under `/var/log/ezkey-pam/` opened with `O_NOFOLLOW|O_CLOEXEC` mode `0600` — not `/tmp`
(#818). Debug chatter does **not** go to syslog.

```bash
docker exec ezkey-pam-ssh cat /var/log/ezkey-pam/debug.out
docker exec ezkey-pam-ssh cat /var/log/ezkey-pam/debug.err
docker exec ezkey-pam-ssh tail -n 50 /var/log/secure
```

Logs never include wait JSON bodies, proof tokens, secret keys, or Basic auth headers.

## Runbook notes (Lightsail / host SSH)

- **`LoginGraceTime` ≥ 120 s.** Demo `sshd_config` sets `LoginGraceTime 120`. The module’s
  monotonic budget is 105 s; a lower grace time cuts the session before PAM can finish (often
  with **no** attempt line). Keep host sshd grace ≥ 120 when using this module.
- **Auth API `/pending` rate limit: 10/min** (`ezkey.rate-limit.pending.requests=10`,
  `window-minutes=1`). Rapid SSH retries plus Demo Device / mobile pending checks share that
  budget — back off when testing EXPIRER / abandon loops.
- **SSH client keepalive may give up before `wait_timeout`.** Clients with aggressive
  `ServerAliveInterval` / `ServerAliveCountMax` (or GUI wrappers) can drop the TCP session
  while PAM is still waiting for device approval. Prefer keepalive that covers at least
  `wait_timeout` + margin, or disable client keepalive for MFA login tests.
- After abandon (Ctrl-C, wait timeout, Enter over-limit), PAM **best-effort** cancels the
  attempt so a new create is not blocked by a stale PENDING/READ card. Cancel is **not
  guaranteed when the wait timeout is reached** (remaining monotonic budget may be too
  small for the cancel HTTP call). The real hard bound on late approval is the **applied
  TTL** from create (`ttlSeconds` / server `expiresAt`), not the cancel.

## Troubleshooting

### Immediate deny / `PAM_AUTHINFO_UNAVAIL`
- Confirm `/etc/security/pam_ezkey.conf` inside the container has both keys.
- Conf open failures are syslog’d explicitly; missing keys return `PAM_AUTHINFO_UNAVAIL`.
- `curl http://integration-api:7080/actuator/health` from the SSH VM.
- Confirm a **VERIFIED** enrollment exists with `userIdentifier=testuser` on that integration.

### Reject / expire / timeout (`PAM_AUTH_ERR`)
- Approve on Demo Device within `wait_timeout` (default **90 s**).
- Attempt summary `result=`: `DENIED` (reject), `EXPIRED` (server expiry), `TIMEOUT` (wait
  budget elapsed without ACCEPTED) — check `/var/log/secure` or `journalctl -t pam_ezkey`.
- Open the PAM enrollment card, not the bootstrap `admin.docker` card.

### API key 401 / IP whitelist
The demo key is created without an IP whitelist. If you add one, include the Docker bridge
range used by the SSH VM (see your host’s Docker bridge network).

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
└── sshd.host-sketch     Host-oriented stack sketch (not applied live)
```
