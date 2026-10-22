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
2. Shows a **pre-create** SSH confirm (`PAM_PROMPT_ECHO_ON`, #982): “SSH login
   for user… / Type ok … to request device approval”. Trimmed **`ok`** is required
   before any Integration API call (confirm-before-create — #975 / Patrick
   [`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md) §5). Empty / EOF /
   Ctrl-C → `UNAVAIL` with **zero** auth attempts (no phone noise).
3. Creates an auth attempt (`POST /api/v1/auth-attempts`) using the Linux username as
   `userIdentifier`, with `challengeRequested` from conf and optional `ttlSeconds`.
4. If create returns a valid `authAttemptChallenge` (host requested or enrollment-
   required — #975), shows a **second** prompt with the digits and
   “Approve on your phone, then press Enter” (Enter only — #982 tier 1). Digits
   never go to authpriv (#750). Host `challenge_requested` but missing code →
   fail-closed `UNAVAIL` + cancel.
5. Waits for approve or reject (`GET …/wait?timeout=N`), aligning `N` on the applied
   TTL (monotonic budget) — never on host clock vs `expiresAt`.
6. Grants SSH access when wait `status` is `ACCEPTED`. On any non-accepted exit after
   create, best-effort `POST …/cancel`. Cancel is not guaranteed when the wait
   timeout is reached; the real bound is the applied TTL.

The Linux username must match a **verified** enrollment `userIdentifier` on the integration
that owns the API key.

## What changed in 2.2.14 (#995 — challenge requested by default)

- **`challenge_requested=true`** is the connector default in
  `config/pam_ezkey.conf`, `docker-entrypoint.sh`, and
  `EZKEY_DEFAULT_CHALLENGE_REQUESTED` (compiled-in when the key is absent).
- **Decision (Alex, confirmed by Marc on 2026-10-22):** the challenge code is
  the safer default for SSH logins; the **connector** owns the default — no
  Admin UI / Integration API “PAM type”, no server-side default, no migration
  (refs [#995](https://github.com/mgagp/ezkey/issues/995)).
- **Precedence:** when the **enrollment** has `authAttemptChallengeRequired`,
  the server enforces a code whatever the connector requests; connector `true`
  asks for a code otherwise; `false` only means “not requested by the
  connector.” No minimum server version (OR since `e5711d4d`, 2025-06-28). See
  [`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md).
- Pre-create **`ok`** prompt unchanged (anti-accident filter; **25 s** Enter
  cap). Challenge-code prompt timeout unchanged from #982 (rem-budget only).
- Experimental / never released: no product release note and no rollback
  procedure for this default. Case C (operator “require a code” beyond conf /
  enrollment) is future work ([#997](https://github.com/mgagp/ezkey/issues/997)).
- Unknown/empty `challenge_requested` values keep the prior default and log an
  authpriv warning (do not silently disable the code).

## What changed in 2.2.13 (#982 — fluidity tiers 0–1)

- Pre-create confirm uses **`PAM_PROMPT_ECHO_ON`** (typed `ok` is visible) with
  “SSH login for user…” wording (no phone-approve copy before create).
- Challenge path: the prompt that shows the code is the **only** wait; after
  **Enter** the module polls `/wait` until approve / deny / TTL. **No second
  “Type ok”.** Challenge Enter has no 25 s cap (rem-budget only; Enter after
  rem exhaustion → UNAVAIL + cancel — if already ACCEPTED, cancel is HTTP 409 /
  `cancel=final` and login is still refused). Confirm-before-create and batch →
  0 creates are unchanged.
- Canonical detail: [`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md).

## What changed in 2.2.12 (#975 — enrollment challenge + confirm-before-create)

- **Confirm-before-create (Patrick interaction model §5):** require trimmed
  **`ok`** on a pre-create prompt **before** `POST /auth-attempts`. Batch /
  empty / `ssh -T </dev/null` / Ctrl-C at that prompt → `UNAVAIL` with
  **zero** creates (no phone noise). Challenge path: same pre-create `ok`,
  then a **second** prompt shows the code after create. Still one authenticate
  call → at most one create under `Match … MaxAuthTries 1`. See
  [`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md).
- **Server-provided challenge always displayed:** when create returns a valid
  `authAttemptChallenge` (0..99), the module shows `Ezkey challenge code: NN…`
  **even if** host `challenge_requested=false` (enrollment
  `authAttemptChallengeRequired`). Pre-2.2.12 dropped the code on the default
  conf path (Isabelle / #975).
- **D4 fail-closed:** OpenSSH empty EOF is indistinguishable from bare Enter;
  requiring `ok` (and doing it before create) closes the “approve with no
  visible prompt” path. Lab autoenter helpers send `ok`.
- **Fail-closed unchanged** when `challenge_requested=true` and no/out-of-range
  challenge → `PAM_AUTHINFO_UNAVAIL` + cancel. **#750:** digits never in
  authpriv. **#726** wait/budget/cancel behaviour kept.
- Proof: harness + stub `enrollment_challenge`; D4 zero-creates;
  `#941` Approve visible with creates==0; exactly one create after `ok`
  (`test-timeouts.sh` CI).

## What changed in 2.2.11 (#941 F1 — waiting message before device wait)

- **Client-visible waiting line (no-challenge):** after create and before
  `/wait`, the module shows
  `Approve this SSH login on your Ezkey device for user <user>.` plus
  `Press Enter to wait for Ezkey device approval:` via
  **`pam_prompt(PAM_PROMPT_ECHO_OFF)`** (same OpenSSH flush technique as the
  challenge path). Bare `pam_info` / `PAM_TEXT_INFO` is buffered by OpenSSH
  keyboard-interactive until the next prompt or the end of the round, so the
  old line only appeared with the verdict (Edgar F1 / RETEST-908 / QA Sphere
  EZK-12/13).
- **With challenge (`challenge_requested=true`) — historical 2.2.11 (2026-10,
  superseded):** the two-digit code was shown in a `PAM_PROMPT_ECHO_OFF`
  prompt before `/wait`. **2.2.12 (2026-10, #975 / #976)** then required
  typed `ok` after that line (and showed the code when the **enrollment**
  requires a challenge even if the host left `challenge_requested=false`).
  **2.2.13 (2026-10, #982)** switched pre-create confirm to `ECHO_ON` and made
  the challenge prompt Enter-only (no second `ok`). Prefer the 2.2.13 / 2.2.14
  sections above for current behaviour.
- **Constraints held:** one create per login under `Match … MaxAuthTries 1`
  (#934 / #907); 75 s-class wait budget / applied TTL / cancel / EXPIRED
  behavior (#895) unchanged; fail-closed unchanged; no secrets or proof tokens
  in logs or argv.
- **No TTY / non-interactive SSH (#956 M2, corrected in 2.2.12 D4):**
  - Missing PAM conversation (`PAM_CONV` null / `PAM_CONV_ERR`) →
    `PAM_AUTHINFO_UNAVAIL` (unchanged).
  - Keyboard-interactive clients that **auto-answer** the prompt with an empty
    string (`ssh -T </dev/null`) also → `UNAVAIL` once the prompt requires
    explicit `ok` (2.2.12). Pre-2.2.12 README claimed device approval alone was
    never enough; that was false for this client type.
  - **D1 (known):** some Windows OpenSSH clients without a TTY hang on
    keyboard-interactive until sshd `LoginGraceTime` (demo **120 s**) and still
    create an Integration API auth attempt; PAM cannot unblock the client hang.
    Prefer a real TTY (`ssh -tt`) or avoid MFA Match users for non-interactive
    automation (`BatchMode` + pubkey outside the MFA Match).
- Proof: harness `ezkey_format_waiting_prompt` + username control sanitization;
  CI stub suites (`test-conf-security.sh`, `test-timeouts.sh`,
  `test-f9-retry.sh` via `ci-m1-conf-security.sh`) assert Approve **before**
  `/wait` and no-challenge failing-conv → UNAVAIL; `ezkey_ssh_kbdint_autoenter`
  in `scripts/common.sh` drives `ok` for demos.

## What changed in 2.2.10 (#907 F9 — Patrick/Christophe review, option A)

- **OpenSSH reality (mandatory sshd step):** each keyboard-interactive **prompt
  round** runs in a forked PAM child that `_exit`s — a `pam_set_data` marker does
  **not** span rounds, and each round creates **one** Integration API auth
  request. Do not treat `PAM_MAXTRIES` as effective under sshd. Host and demo
  installs **must** set `MaxAuthTries 1` inside a dedicated `Match User …` /
  `Match Group …` block (host dogfood: `Match Group ezkey-mfa`). Prefer that over
  a **global** `MaxAuthTries 1`, which also counts refused public keys and would
  affect non-MFA users (e.g. `ec2-user`). Without that Match, one SSH login can
  create multiple orphan pending attempts (Edgar retest 2026-10-20 / #908 F9).
  `install.sh` / entrypoint **warn** when effective `maxauthtries` is not 1;
  they do not rewrite `sshd_config`.
- Authpriv **`start`** line at call begin (before create); summary includes
  `timeoutSeconds=`.
- **R5 (best-effort):** chunked `/wait` + wait-scoped `SIGTERM` (absorbed only
  during wait — flag then restore; Christophe security GO). When the signal is
  delivered, curl aborts and cancel runs within `min(api_timeout, 5)`. On OpenSSH
  9.9 pre-auth client kill, SIGTERM often never arrives — cancel is then skipped
  and the **applied TTL** from create (≤ 90 s) is the residual bound. Do not rely
  on `pam_end` under OpenSSH.
- Proof: `scripts/test-f9-retry.sh` A/B (multitry vs Match) gate CI. Case C
  (kill-client) documents best-effort cancel and asserts requested TTL + single
  create; it does **not** require cancel ≤5 s and does not fail CI when cancel is
  absent.
- In-process final marker kept as fail-closed for same-handle callers only.
- Harness: `make test-session` (interrupt cancel cap + final-verdict helpers).

## What changed in 2.2 / 2.2.1 (M1)

On top of 2.1 slices 1–3 ([`STATUS.md`](STATUS.md) § M1):

- **Start + attempt lines** in `authpriv` / `/var/log/secure` (stable format):

  ```text
  pam_ezkey: start user=… authAttemptId=- timeoutSeconds=…   (before create)
  pam_ezkey: attempt user=… authAttemptId=… result=… pam_code=… latency_ms=… lab_http=… cancel=… timeoutSeconds=…
  ```

  - `result`: `SUCCESS` \| `EXPIRED` \| `TIMEOUT` \| `DENIED` \| `MAXTRIES` \| `UNAVAIL` \| …
    (`MAXTRIES` is same-handle only — not effective across OpenSSH kbdint forks)
  - `cancel` (only these four literals — `EZKEY_CANCEL_*`):
    | Value | Meaning |
    |---|---|
    | `ok` | Cancel HTTP 2xx |
    | `fail` | Cancel transport error or non-2xx (except 409) |
    | `skipped` | Cancel never attempted (SUCCESS / pre-create / no budget) |
    | `final` | Cancel HTTP **409** already-final (possible late approve; **no** `pam_rc` change) |

  Never secrets / proof / bodies. Debug chatter stays in `/var/log/ezkey-pam/` only.
  Full field table: [`STATUS.md`](STATUS.md) § M1 observability.
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
  -o NumberOfPasswordPrompts=1 \
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
`/etc/security/pam_ezkey.conf`, add a PAM line, and **mandatory** sshd
`Match Group ezkey-mfa` / `Match User …` with `MaxAuthTries 1` (see
`sshd.host-sketch` and Runbook below). **Do not** copy a Rocky-built `.so` onto
AL2023.

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
| `wait_timeout` | `90` | Seconds to wait for device approval (clamped **15–90**). Sent as create `ttlSeconds`; `/wait?timeout=` aligns on create response `timeoutSeconds` (PR-A) when present |
| `wait_polling` | `2` | Wait poll interval |
| `api_timeout` | `10` | Timeout for create/cancel calls (max 10) |
| `challenge_requested` | `true` | Ask the Integration API for a challenge on create (**default `true` since #995** — safer SSH default; connector owns the default). Operator always confirms with **`ok` before create** (confirm-before-create; anti-accident filter; 25 s Enter cap). When the response includes a valid challenge (requested here, **or** the **enrollment** has `authAttemptChallengeRequired` — server enforces regardless of this flag; OR since 2025-06-28), a **second** `pam_prompt` shows the digits (#975). Set `false` only to opt out of *connector-requested* challenge. Unknown/empty values keep the prior default + authpriv warning. `true` + missing challenge → UNAVAIL. Precedence: [`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md) |
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

- **Interaction model:** one SSH login → pre-create `ok` → at most one create →
  optional challenge prompt → `/wait`. Batch / empty clients create nothing.
  See [`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md) (scenario catalogue
  + sshd / client inventory).
- **`LoginGraceTime` ≥ 120 s.** Demo `sshd_config` sets `LoginGraceTime 120`. The module’s
  monotonic budget is 105 s; a lower grace time cuts the session before PAM can finish (often
  with **no** attempt line). Keep host sshd grace ≥ 120 when using this module.
- **MANDATORY — single try via `Match` (sshd), not a global `MaxAuthTries 1`.**
  After Deny or expiration, OpenSSH portable retries **keyboard-interactive in the
  same TCP connection**. Each **prompt round** is a **new forked PAM child**
  (`auth-pam.c` `_exit`) and therefore **one new** `POST /auth-attempts` — in-process
  PAM markers cannot stop a second create (#907 F9 / option A). Edgar’s 2026-10-20
  retest of #908 showed F9 (one SSH login → three auth requests) is fixed **only**
  when `MaxAuthTries 1` is inside the MFA `Match` (other users such as `ec2-user`
  stay on the global try budget). This step is **mandatory** for every host install
  — `install.sh` / entrypoint **warn** when it is missing or when a global
  `MaxAuthTries 1` appears before `Match`; they do **not** rewrite `sshd_config`.

  Create the group and add MFA Linux users so `Match Group` applies:

  ```bash
  groupadd ezkey-mfa
  usermod -aG ezkey-mfa <mfa-user>
  ```

  ```text
  # Host dogfood (recommended): MFA accounts in group ezkey-mfa only
  Match Group ezkey-mfa
      MaxAuthTries 1
      AuthenticationMethods keyboard-interactive
      PubkeyAuthentication no

  # Demo / single-user lab equivalent:
  Match User testuser
      MaxAuthTries 1
      AuthenticationMethods keyboard-interactive
      PubkeyAuthentication no
  ```

  Keep `PubkeyAuthentication no` inside the Match (security GO): with
  `MaxAuthTries 1`, a client-offered pubkey that is refused consumes the single
  try before keyboard-interactive and locks out the MFA account;
  `AuthenticationMethods keyboard-interactive` makes pubkey unused anyway.
  Combining `publickey,keyboard-interactive` requires raising `MaxAuthTries`
  (out of scope here; document separately if funded). Prefer pinning
  `AuthenticationMethods keyboard-interactive` **inside the Match** (not only
  globally). **Avoid global `MaxAuthTries 1`:** refused public keys also consume
  tries and can lock out mixed auth methods for non-MFA users. **Match limits:**
  applies until EOF or the next `Match`; first matching value wins; protection is
  **per TCP connection**, not a cross-connection rate limit. **Forgetting the
  Match is an easy miss** — OpenSSH then recreates auth attempts after Deny/Expire
  in the same session (F9; proven by `scripts/test-f9-retry.sh` case A).
  `install.sh` / entrypoint check effective `maxauthtries 1` with
  `sshd -T -C user=…` and run the shared helper
  `scripts/sshd-warn-global-maxauthtries-1.sh` (sibling
  `sshd-warn-global-maxauthtries-1.awk`) for a global `MaxAuthTries 1` before
  any `Match` — unit-tested by invoking that same helper (not a name-only
  grep). Client helper:
  `-o NumberOfPasswordPrompts=1`. Demo `sshd_config` uses `Match User testuser`
  with global `MaxAuthTries 6`.
- **HTTP 429 during `/wait`:** fail-closed — PAM returns `UNAVAIL` and the SSH
  approval fails (no silent retry inside the wait loop).
- **Auth API `/pending` rate limit: 10/min** (`ezkey.rate-limit.pending.requests=10`,
  `window-minutes=1`). Rapid SSH retries plus Demo Device / mobile pending checks share that
  budget — back off when testing EXPIRER / abandon loops.
- **SSH client keepalive / disconnect (R5 — best-effort).** Prefer keepalive that
  covers at least `wait_timeout` + margin for MFA tests. The module installs a
  wait-scoped `SIGTERM` handler (`auth-pam.c:164-168` path when delivered): absorb
  → flag → abort chunked curl → best-effort cancel within `min(api_timeout, 5)`.
  On OpenSSH 9.9 pre-auth kill, that SIGTERM often never arrives; cancel is then
  skipped and the **applied TTL** from create is the residual bound — not via
  `pam_end`. Residual: hard `SIGKILL` also skips cancel.
- After abandon (Ctrl-C, wait timeout, Enter over-limit), PAM **best-effort** cancels the
  attempt so a new create is not blocked by a stale PENDING/READ card. Cancel is **not
  guaranteed when the wait timeout is reached** (remaining monotonic budget may be too
  small for the cancel HTTP call). The real hard bound on late approval is the **applied
  TTL** from create (`timeoutSeconds` in the create response, ≤ 90 s for PAM), not the
  cancel. A late approval after a failed cancel can leave an orphan `ACCEPTED` attempt
  (nothing granted on SSH); residual risk is approval fatigue.
- **Never version origin IPs** in docs or conf samples — use the placeholder
  `<static-ip ezkey-online-community>` for community-host notes.

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
