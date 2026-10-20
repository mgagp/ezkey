# Ezkey PAM — Status and evolution

**Experimental.** This file is the local map for the Linux PAM complement. It is not part of the
September 2026 operable-release roadmap. Keep PAM facts in `ezkey-pam/`. Do not copy this into
`docs/` or `docker/README.md`.

Ezkey is a greenfield MFA platform: serious, opinionated, not requested by a named industry buyer
and not promised to anyone. That is an opportunity to keep this module **small**. The PAM module is
a complement to the Ezkey core, not a second product and not a general-purpose PAM framework.

How-to and demo commands live in [`README.md`](README.md). This file answers: what it is today,
what “operable” would mean, and what not to build.

---

## Product intent (keep this geometry)

Ezkey’s product thesis is backend-first MFA with an explicit Integration API. A Linux PAM module
is one more **integration client**, in the same family as a backend calling
`POST /api/v1/auth-attempts`. The value is: a generic Linux login (SSH first) can wait on the same
device approval path as any other integration, with the same audit trail.

Two adoption paths, in this order:

1. **Dogfood (Lightsail lab VM).** Protect the lab VM that already runs Ezkey. Today that host is
   Amazon Linux 2023 (`ec2-user`) on Lightsail — a personal lab host, not an `ezkey.online`
   cutover. If the module cannot be installed, rolled back, and survived when Integration API is
   down, it is not ready for this path.
2. **Generic Linux.** Same `.so`, dropped onto a host the way the Rocky Linux 10 demo does it.
   Distribution can stay “build in Docker, extract the artifact.” No GitHub Release and no
   `.rpm` / `.deb` until a second real host exists.

Non-goals until dogfood is boring:

- Windows, macOS, or a multi-OS PAM SDK
- sudo / su / polkit as the first target (SSH first)
- A configuration language, encrypted property store, or plugin loader
- Duo-style `failmode=safe` (fail-open MFA) inside the module
- i18n catalogs (English PAM prompts; device copy stays in the config file)
- Packaging for Debian/Ubuntu before an Amazon Linux / RHEL-family host has been dogfooded

---

## 1. Current status (2026-09)

### What it is

Version **2.2.10** (#907 F9 option A: sshd `Match` MaxAuthTries; chunked wait +
best-effort disconnect cancel TTL-bounded; honest OpenSSH fork limit) builds on
**2.2.6**
**working lab demo** plus STATUS §2.8 slices **#1–#3** and **M1**
(observability + conf `root:root` exact `0600`/`0400`, no URL default, no process-ENV
overrides, `lab_http` gate, optional two-digit challenge via `pam_prompt`). It is still
**not** a host-operable MFA agent on a real host (sshd cutover remains slice #4+).

Proven in this repository (clean-start stack + Demo Device + Admin UI):

- Docker **builder** compiles `pam_ezkey.so` on Rocky Linux 10.
- Docker **builder-al2023** compiles an ABI-matched `.so` on Amazon Linux 2023 (cJSON pinned
  from source; not in AL2023 repos).
- Docker **runtime** is a small SSH VM (`ezkey-pam-ssh`, host port 2222).
- Docker **runtime-al2023** mirror (`ezkey-pam-ssh-al2023`, host port 2223) for approve/reject/
  timeout against the same Integration API.
- `scripts/smoke-al2023.sh` verifies the AL2023 `.so` loads without the Ezkey stack.
- `scripts/provision.sh` creates a System Tenant integration, API key, and a VERIFIED enrollment
  whose `userIdentifier` matches the Linux account (`testuser`).
- `scripts/demo-ssh.sh` starts keyboard-interactive SSH, the module creates an auth attempt,
  Demo Device approves, wait returns `ACCEPTED`, SSH session starts (`PAM_SSH_OK`).
- Admin audit logs show `AUTH_ATTEMPT_CREATED` (Integration API) then
  `AUTH_ATTEMPT_RESPOND` / `auth_attempt_approved`.

The Linux username is the Ezkey `userIdentifier`. There is no mapping table.

### How it authenticates

1. Read `/etc/security/pam_ezkey.conf` (optional PAM arg `conf=` only — **no** process ENV).
2. `POST /api/v1/auth-attempts` with HTTP Basic (`integration_key` / `secret_key`).
3. `GET /api/v1/auth-attempts/{id}/wait`.
4. Return codes (fail-closed; never `PAM_IGNORE` / `PAM_SUCCESS` on API failure):

| Situation | Return |
|---|---|
| Wait `ACCEPTED` | `PAM_SUCCESS` |
| Reject, expire, not ACCEPTED | `PAM_AUTH_ERR` |
| Conf unreadable / bad mode/owner / missing or empty URL/keys / DNS / TLS / HTTP unexpected / curl fail | `PAM_AUTHINFO_UNAVAIL` |
| Empty username | `PAM_USER_UNKNOWN` |
| OOM / internal | `PAM_SERVICE_ERR` |

**Operator note — `UNAVAIL` + PAM control flags:** the module is fail-closed
(`PAM_AUTHINFO_UNAVAIL`). If the host stack marks the line `sufficient` or
`[default=ignore]`, that *stack* can still become fail-open for MFA. Keep
`required` / `requisite` (see [`sshd.host-sketch`](sshd.host-sketch)) unless you
intentionally accept MFA bypass when the Integration API / conf is unavailable.

### Configuration today

File format: `key=value` at `/etc/security/pam_ezkey.conf`, mode exactly `0600` or `0400`,
owner `root:root` in the demo image. That path and style match Linux-PAM convention
(`pam_access`, `pam_limits`, `pam_faillock`).

| Key | Role | Demo-adequate? |
|---|---|---|
| `integration_api_url` | Integration API base URL (**required**; no compiled-in default) | Yes for Docker DNS; non-lab / host installs need HTTPS |
| `integration_key` / `secret_key` | Basic auth | Yes; file perms are the control |
| `wait_timeout` / `wait_polling` / `api_timeout` | HTTP waits | Yes |
| `challenge_requested` | Two-digit number-matching challenge | **Default `false`** — SSH MFA without number matching; `true` shows code via `pam_prompt` before `/wait` |
| `lab_http` | Allow `http://` URL | Docker entrypoint sets `true`; host sample default `false` |
| `wait_timeout` | `/wait` client wait + create `ttlSeconds` (seconds) | Default **90**; clamped **15–90** (`EZKEY_MIN_TTL_SECONDS` / `EZKEY_MAX_WAIT_TIMEOUT`); aligned on applied TTL from create response (#895) |
| `context_title` / `context_message` | Device copy | Yes |
| `debug` | Extra filelog under `/var/log/ezkey-pam/` (`O_NOFOLLOW`, 0600) | Docker lab entrypoint sets `true`; sample conf default `false` (#818) |

PAM args (root-owned `pam.d` only): `debug`, `conf=/path`, optional `wait_timeout=N` (overrides conf).

**Process ENV is not read by `pam_ezkey.so`.** Compose may still set
`EZKEY_INTEGRATION_*`, `EZKEY_WAIT_*`, `EZKEY_DEBUG`, and optionally
`EZKEY_CONFIG_FILE` for **`docker-entrypoint.sh` only** — the entrypoint writes the
root-owned conf via `mktemp` under `/etc/security` (never `/tmp`) +
`install -m 600 -o root -g root`. That bridge exists because sshd does not pass
Docker ENV into PAM. A caller-controlled environment (e.g. `su`/`sudo`) cannot
redirect conf path, debug, or timeouts inside the `.so`.

**Verdict:** the current parameter set is **enough for the demo** and is the right *shape* for
v1 operable use. It is not missing a second config system. Remaining gaps for a non-lab host are
operational (TLS, live sshd cutover), not “more keys.”

### Known demo limits (honest)

- **HTTP** to `integration-api:7080` only with **`lab_http=true`** (entrypoint sets it). Outside
  lab, `http://` → `PAM_AUTHINFO_UNAVAIL`. No TLS verify knobs yet. (Slice #4+ / non-lab HTTPS)
- **Demo PAM stack is Ezkey-only** (`pam_ezkey` + `pam_permit`). Host-oriented sketch lives in
  [`sshd.host-sketch`](sshd.host-sketch) — not applied to any live host by this repo.
- **`install.sh` can still build on the host.** Prefer image-matched extract for real hosts
  (`./scripts/build-al2023.sh --extract` for AL2023).
- **Debug file transcripts** live under `/var/log/ezkey-pam/` only when `debug=true` (#818);
  never `/tmp`. Bodies / proof tokens are not logged (slice #1). Demo entrypoint defaults
  `debug=true` (no filelog rotation) — intentional for lab transcripts.
- **`PerSourcePenalties no`** in demo [`sshd_config`](sshd_config) (OpenSSH 9.9+). Stock
  `authfail:5` blocks localhost after a handful of denied logins and breaks M1 conf-security
  rapid UNAVAIL proofs. **Lab only** — do not copy this disable onto a hardened non-lab host.
- **LoginGraceTime ≥ 120 s** (demo `sshd_config`). Abandoning at the prompt no longer relies
  solely on server TTL: PAM best-effort `POST …/cancel` on non-ACCEPTED exit after create
  (#895). If sshd cuts the session before PAM returns, there may still be no attempt line.
- **Cancel best-effort / not guaranteed at wait timeout** (Christophe #897 n3): if cancel
  fails or is skipped (budget exhausted), the attempt window lasts until the **applied TTL**
  from create (`timeoutSeconds`, ≤ **90 s** for PAM). A late device approval then marks an
  orphan attempt `ACCEPTED` while nobody is waiting on that id — SSH already failed closed.
  Residual risk is **approval fatigue** (operator/device sees a prompt that grants nothing).

### Slices #1–#3 done (this eval)

1. Hygiene: AUTHPRIV syslog; no bodies/secrets in logs; debug files under
   `/var/log/ezkey-pam/` (`O_NOFOLLOW`, 0600) not `/tmp` (#818); conf-open errors;
   curl init/cleanup; `explicit_bzero` on Basic `userpwd` and secret copies after HTTP.
2. PAM return-code map + fail-closed / break-glass stack docs (`STATUS` + `sshd.host-sketch`).
3. AL2023 builder + extract + smoke + SSH mirror compose profile.

Still out of scope here: host sshd cutover, TLS enrollment, packages, `failmode=safe`.

---

## 2. What “operable” would require (still small)

Work in **slices**. Dogfood on a Lightsail / AL2023 lab host is the acceptance test. Generic packaging is a by-product of
that, not a parallel product.

### 2.1 Failure posture (do this first, conceptually)

Ezkey design principle: name fail-open vs fail-closed at the boundary.

| Boundary | Posture | Why |
|---|---|---|
| PAM module vs Integration API / timeout / reject | **Fail-closed** | Continuing would grant SSH without the claimed MFA |
| Operator lock-out on the dogfood host if Ezkey is down | **Break-glass in the PAM *stack* and Lightsail console**, not fail-open in the module | Availability without silently weakening MFA |
| Debug / audit side effects | Fail-open (login still decided by Ezkey) | Logging must not become a second authenticator |

Do **not** add a Duo-like `failmode=safe` switch in v1. That flag is how MFA modules quietly
become optional. If the dogfood host needs a login when Ezkey is down, name a **local break-glass account**
(or Lightsail serial/browser console) in `/etc/pam.d/sshd` with `pam_succeed_if` / `pam_unix`,
and keep `pam_ezkey` `required` for everyone else.

Demo stack (Ezkey-only) stays valid **only** for the Docker SSH VM.

Suggested host stack (sketch, not shipped):

- Keep SSH key or a local password for a **named** break-glass user.
- Interactive users: Unix/publickey **and** `pam_ezkey.so` `required` (MFA), **or** Ezkey as the
  interactive factor with console break-glass documented.
- Never put `pam_ezkey` on a line that can lock **root console** without a tested bypass.

### 2.2 Generalizations that are actually needed

Only these change the module from “demo VM” to “Linux host”:

1. **Target distro for the `.so`.** Amazon Linux 2023 builder stage (same Dockerfile pattern as
   Rocky). Install path `/usr/lib64/security/` (AL2023) vs `/lib64/security/` (often equivalent).
2. **Username mapping.** v1 stays 1:1 (`ec2-user` enrollment). A map file is accidental complexity
   until a second Linux account must share one enrollment.
3. **TLS.** Use HTTPS; keep libcurl defaults (`CURLOPT_SSL_VERIFYPEER`). Optional later:
   `ca_info=` in conf if a private CA appears. No custom crypto.
4. **API key IP allowlist.** Demo keys have none. A dogfood host key should allow that Lightsail host (or the
   Docker bridge if PAM runs in a container). That is Integration API policy, not C code.
5. **`debug` default false** (sample conf; Docker lab may enable). Syslog via `pam_syslog` /
   `LOG_AUTHPRIV`; optional file transcripts under `/var/log/ezkey-pam/` with `O_NOFOLLOW` /
   mode `0600` (#818). Never log secret keys, Basic headers, or wait bodies.
6. **PAM return codes** (see §2.4). The stack can then distinguish “Ezkey down” from “denied.”
7. **Host PAM snippet + sshd_config** documented next to `sshd` / `sshd_config` in this folder,
   separate from the demo-only Ezkey-only stack.

Not needed to generalize:

- JSON module config
- Encryption at rest of `secret_key` on the SSH host
- A second property format
- gettext / French PAM prompts
- Official packages / releases

### 2.3 Configuration: keep key=value, do not invent a format

**Community practice for PAM modules** is a plain file under `/etc/security/`, root-owned,
unreadable by others. Duo (`pam_duo`), `pam_access`, and `pam_limits` all do this. Operators
expect `key=value` or whitespace fields, comments with `#`, and a PAM argument `conf=`.

| Idea | Decision |
|---|---|
| JSON conf | **No.** JSON is for the Integration API body (cJSON stays). A JSON *config* would add a parser surface and surprise every PAM operator. |
| Dedicated property library | **No.** Current parser is enough. |
| Encryption at rest of the API secret | **No for v1.** The module must send the secret to the API. Encrypting the file on the same disk requires another key on the same disk. Gain is near zero unless secrets live in an external manager (Lightsail secrets / IAM) — out of scope. **File mode `0600`, root owner** is the control. |
| Split `pam_ezkey.conf` (public) + `pam_ezkey.secret` (0600) | Optional later if the main file must be world-readable. Not required while the whole file is `0600`. |
| Env vars as the real contract | **No for host install.** sshd does not pass them. Keep env only as Docker entrypoint input that **writes the file**. |

Small keys worth considering **when** dogfood starts (not before):

- `tls_verify=true` (explicit; default on)
- `debug=false` default in the sample installed on hosts

Do not add `failmode`, `http_proxy`, or per-user maps in the same slice.

### 2.4 Error handling and PAM C hygiene

Linux-PAM module writers’ guide:
[Linux-PAM Module Writer's Guide](http://www.linux-pam.org/Linux-PAM-html/Linux-PAM_MWG.html).

Closest **network MFA** reference implementations (read, do not clone features):

- [Duo Unix `pam_duo`](https://github.com/duosecurity/duo_unix) — HTTPS MFA, conf file, syslog.
  Adopt file layout and TLS discipline; **do not** adopt `failmode=safe` for Ezkey v1.
- [Yubico `pam_u2f`](https://github.com/Yubico/pam-u2f) — careful PAM conversation and return
  codes (local token, not HTTP).
- [Google Authenticator `pam_google_authenticator`](https://github.com/google/google-authenticator-libpam)
  — `pam_sm_authenticate` / `pam_sm_setcred` shape, syslog.
- In-tree Linux-PAM modules under `modules/pam_unix` / `pam_access` for stack conventions.

Reasonable return-code map (module stays fail-closed; the **code** is for operators and stacks):

| Situation | Return | Notes |
|---|---|---|
| Wait `ACCEPTED` | `PAM_SUCCESS` | Already done |
| Reject, expire, not ACCEPTED | `PAM_AUTH_ERR` | Denied |
| Cannot read conf, missing keys, DNS, TLS, HTTP 5xx, curl fail | `PAM_AUTHINFO_UNAVAIL` | Still denies when the line is `required` |
| Empty username | `PAM_USER_UNKNOWN` | Already done |
| OOM / internal | `PAM_SERVICE_ERR` or `PAM_AUTH_ERR` | Prefer `SERVICE_ERR` |

Do **not** return `PAM_IGNORE` or `PAM_SUCCESS` on API failure.

Resource hygiene (progressive, each is a small patch):

- Pair `openlog` / `closelog` (already) on every return path in `pam_sm_authenticate`.
- `curl_global_init` once per authenticate (or documented lazy init) and `curl_easy_cleanup`
  (already) plus `curl_global_cleanup` if init was local.
- Always free cJSON, curl slists, and growable buffers (mostly done; audit early-returns).
- `explicit_bzero` on `userpwd` / `secret_key` copies after the HTTP calls.
- Stop logging HTTP bodies. Log `authAttemptId`, HTTP status, and wait `status` only.
- Keep file debug under `/var/log/ezkey-pam/` only; never reintroduce `/tmp` fixed names (#818).
- `pam_sm_setcred` returning `PAM_SUCCESS` is correct for an auth-only module.
- `pam_sm_acct_mgmt` / session hooks are **not** required for SSH MFA v1 (`PAM_IGNORE` if a
  stack ever calls them). Do not implement a second policy engine there.

Conversation: keep a single English `pam_info()` so keyboard-interactive actually starts. That
is a protocol need, not an i18n project. French (or any locale) belongs in `context_title` /
`context_message` if an operator wants it on the phone — not in gettext inside the `.so`.

### 2.5 Packaging and distribution (keep extraction, skip Release)

Preferred contract, same idea as the current Dockerfile:

```text
# Rocky (lab demo)
docker build --target builder -t ezkey-pam-builder .
docker create --name ezkey-pam-extract ezkey-pam-builder
docker cp ezkey-pam-extract:/src/build/pam_ezkey.so ./pam_ezkey.so
docker rm ezkey-pam-extract

# Amazon Linux 2023 (host / dogfood ABI)
docker build -f Dockerfile.al2023 --target builder-al2023 -t ezkey-pam-builder-al2023:local .
# or: ./scripts/build-al2023.sh --extract
```

Then on the host: install the `.so` next to other PAM modules, install conf `0600`, add one
`/etc/pam.d/sshd` line, reload `sshd`. `install.sh` can wrap that **after** it copies a
pre-built `.so` instead of compiling on the dogfood host.

| Path | Builder image | Notes |
|---|---|---|
| Lab demo (done) | Rocky Linux 10 | Nested SSH VM |
| AL2023 host ABI (done for build/mirror) | Amazon Linux 2023 | `builder-al2023` + `runtime-al2023` / smoke; install on Lightsail host is slice #5 |
| Later generic EL | Rocky / RHEL 9–10 | Same family as the demo |
| Debian/Ubuntu | later | New builder stage when a host exists |

No project Release is required. The artifact is the `.so` + a 20-line conf + a PAM snippet.

### 2.6 Dogfood on Lightsail (Amazon Linux 2023)

The Lightsail lab VM already runs the Ezkey APIs in Docker
(`experimental-hybrid/`). PAM would protect **sshd on the VM**, so a stolen SSH password/key is
not enough. It does not replace Caddy, Cloudflare, or Admin API login.

Prerequisites before touching `sshd` on the live lab:

1. AL2023-built `.so` tested in a throwaway AL2023 container (mirror of today’s Rocky test).
2. Enrollment `userIdentifier=ec2-user` (or whichever account operators use), VERIFIED, on an
   integration whose API key is reachable from the host (loopback / Docker bridge / private URL).
3. TLS to Integration API (localhost HTTPS or internal hostname), `debug=false`.
4. Lightsail **browser/serial console** confirmed working the same day (break-glass).
5. Documented rollback: restore `/etc/pam.d/sshd` and `sshd_config` from copies; `sshd -t`
   before restart.
6. Do not enable IP-restricted API keys until the host’s source address is known and listed.

`ec2-user` must remain able to run Docker after MFA; the enrollment is an operator device
(Demo Device or the mobile app), not a second Linux password.

### 2.7 Display language

**English only** in the module. There is no PAM i18n template worth adopting at this size.
Device-facing strings are already configuration. Revisit gettext only if a second locale is
required by a real host — not as a prerequisite for host dogfood.

### 2.8 Suggested slice order

Each slice should stay mergeable on its own:

1. ~~Hygiene: syslog-only, no proof tokens in logs, `debug` default false, conf-open errors logged.~~ **Done (2.1.0 eval).**
2. ~~PAM return codes + documented fail-closed / break-glass stack (still Rocky demo).~~ **Done**
   (`sshd.host-sketch`).
3. ~~Amazon Linux 2023 builder target + extract instructions; test in an AL2023 container.~~ **Done**
   (`builder-al2023`, `smoke-al2023`, `ezkey-pam-ssh-al2023`).
4. TLS + dogfood integration/enrollment/key; dry-run on a spare user before changing `ec2-user`.
5. Host sshd cutover with rollback window.
6. Only then: a generic “copy this snippet onto EL10” note. Packages if a second distro appears.

---

## Other notes worth keeping

- **SELinux / firewall.** sshd making outbound HTTPS from a PAM module may need an allow rule.
  Deal with it on the AL2023 dogfood host; do not pre-build a policy pack.
- **sudo later.** Same `.so` can appear in `/etc/pam.d/sudo` once SSH is boring. Do not couple
  the first slice to sudo.
- **Challenge (number matching).** Supported when `challenge_requested=true`: create response
  `authAttemptChallenge` is shown with `pam_info` (SSH keyboard-interactive) before `/wait`.
  Device must enter the same code. Default demo conf stays `false` for simpler SSH proof;
  `./scripts/test-challenge.sh` exercises the path.
- **Observability.** Operator proof is Ezkey audit (`AUTH_ATTEMPT_*`) plus host `authpriv`. Do not
  add a metrics sidecar in the module.
- **Methodology.** This stays a hygiene/lab map in `ezkey-pam/`. Promote an `I-*` / `TB-*` only
  if a host sshd cutover is funded as a program slice. Until then, do not grow a second documentation
  tree.

When unsure, prefer the Rocky demo as the living test, change one operational concern at a time,
and refuse features that would make the module smarter than the Integration API.

---

## M0 — Stabilize (PR #610 rebase onto main)

**Goal:** keep slices 1–3 behavior from #610, reconcile with main, re-prove lab demos, document
lessons. No host sshd cutover, no product promotion, no AWS / ezkey.online touch.

### Substantive conflicts resolved (rebase onto main)

| Area | Main had landed | #610 wanted | Resolution (behavior kept) |
|---|---|---|---|
| Debug file path | `#818` — `/var/log/ezkey-pam/` via `ezkey_filelog` (`openat`/`O_NOFOLLOW`/`0600`) + `ezkey_sanitize_for_log` | Gate `/tmp/pam_ezkey.{out,err}` on `debug=true` | **Keep main’s filelog.** Do not reintroduce `/tmp` fixed names. Debug still gated on `debug`. |
| Log helpers | `log_pam_error(pamh, cfg, …)` always takes cfg; sanitize before syslog/file | Separate `ezkey_filesyslog_*` + `log_pam_error_debug` writing `/tmp` | Main sinks + thin `log_pam_error_debug` → `log_pam_error` for call-site clarity |
| HTTP / wait logging | Could log response **bodies** (incl. proof token risk) | Omit bodies; log `authAttemptId` / HTTP status / wait status only | **Keep #610** (no bodies/secrets) |
| PAM return codes | Mostly `PAM_AUTH_ERR` for all failures | Fail-closed map (`AUTHINFO_UNAVAIL` / `AUTH_ERR` / `SERVICE_ERR` / `USER_UNKNOWN`) + `curl_global_*` + `explicit_bzero` | **Keep #610** |
| Conf / docs | STATUS still listed pre-slice demo limits | Slices 1–3 “done” + AL2023 path | Merge: update limits to post-slice truth; keep `#818` wording |
| `test_pam.sh` | Assert `/var/log/ezkey-pam` mode `700` | `-n "$PAM_SO"` when locating `.so` | Both |
| AL2023 runtime image | N/A on main | Separate `Dockerfile.al2023` | Keep #610 split; **add** `mkdir -p /var/log/ezkey-pam` mode `700` so `#818` + `test_pam.sh` pass on AL2023 |

### Lessons / problems for M0

1. **Stale branch vs parallel hygiene.** While #610 was draft, main shipped `#818` PAM `/tmp`
   hardening. Rebase must prefer main’s safer log path and layer #610’s auth/hygiene on top —
   not “ours” wholesale for file I/O.
2. **AL2023 split must inherit Rocky runtime ops.** Splitting `Dockerfile.al2023` without
   copying the log-dir `mkdir`/`chmod 700` breaks `test_pam.sh` and debug transcripts on the
   mirror VM.
3. **Entrypoint comments drift.** Demo still defaults `EZKEY_DEBUG=true`; docs must say
   `/var/log/ezkey-pam`, not `/tmp`, or operators chase the wrong path.
4. **Observability contract for later milestones:** one `authpriv` syslog line per attempt with
   user, `authAttemptId`, result, PAM code, latency — never secrets. Not implemented in M0
   beyond existing id/status logs; M1 reviews conf `0600` + fail-closed with Patrick/Christophe.
5. **Demo `authpriv` → `/var/log/secure` may stay empty** in the Rocky/AL2023 SSH containers
   (rsyslogd runs; `logger -p authpriv.info` still does not land in `/var/log/secure`). Module
   still calls `syslog`/`pam_syslog`; M0 hygiene was proven on `/var/log/ezkey-pam/debug.*`
   (`body omitted`, `authAttemptId=`, no secrets). Fixing container rsyslog routing is a small
   follow-up, not host sshd cutover.

### Out of M0

Host sshd cutover, TLS, packages, `failmode=safe`, AWS, ezkey.online, product-roadmap promotion.

---

## M1 — Observability + conf hardening (PR #610)

**Goal (Marc GO):** enough observability for Patrick/Christophe review without expanding product
scope. Zero lockout risk: module stays fail-closed; no AWS / ezkey.online / host sshd cutover.

### Attempt syslog lines (canonical)

Up to **two** `LOG_AUTHPRIV` syslog lines per `pam_sm_authenticate` call, **regardless of
`debug`**: a `start` line at call begin (before create) and an `attempt` summary at the end.
Debug chatter (POST/wait details) goes only to `/var/log/ezkey-pam/debug.*` when
`debug=true` — never to syslog.

Format (stable; parse with `grep 'pam_ezkey: attempt '` or `pam_ezkey: start `):

```text
pam_ezkey: start user=<linuxUser> authAttemptId=<id> timeoutSeconds=<n>
pam_ezkey: attempt user=<linuxUser> authAttemptId=<id|-> result=<R> pam_code=<n> latency_ms=<ms> lab_http=<true|false> cancel=<ok|fail|skipped|final> timeoutSeconds=<n|->
```

| Field | Meaning |
|---|---|
| `user` | Linux username (sanitized); `-` if unavailable |
| `authAttemptId` | Numeric id from create, or `-` if never created |
| `result` | `SUCCESS` \| `EXPIRED` \| `TIMEOUT` \| `DENIED` \| `MAXTRIES` \| `UNAVAIL` \| `ERROR` \| `UNKNOWN_USER` |
| `pam_code` | Integer PAM return code |
| `latency_ms` | Monotonic elapsed ms for the authenticate call |
| `lab_http` | Conf `lab_http` as `true` or `false` (Christophe) |
| `cancel` | Exactly one of `ok` \| `fail` \| `skipped` \| `final` (`EZKEY_CANCEL_*`; Patrick #897 M2 + Christophe observability) |
| `timeoutSeconds` | Planned wait on `start` (before create); applied TTL on `attempt` (or `-`) (#907) |

**#907 F9 / R5 (option A — Patrick/Christophe review):** Under OpenSSH portable, each
keyboard-interactive **prompt round** runs in a forked child that `_exit`s and
creates **one** auth request — **without a dedicated sshd `Match` (`MaxAuthTries 1`
plus `AuthenticationMethods keyboard-interactive` and/or `PubkeyAuthentication no`),
a retry in the same connection creates a new auth attempt.** This Match step is
**mandatory** on host install (recommended: `Match Group ezkey-mfa` so non-MFA
users such as `ec2-user` keep the global try budget). Edgar’s 2026-10-20 retest of
#908 confirmed F9 is only fixed when `MaxAuthTries 1` is inside that Match. Do not
claim the in-process final marker or `PAM_MAXTRIES` protects across OpenSSH rounds;
keep the marker only as same-handle fail-closed. Prefer `Match User|Group` over
global `MaxAuthTries 1` (pubkey failures count). Entrypoint / `install.sh` verify
effective `maxauthtries 1` via `sshd -T -C user=…` and warn on global
`MaxAuthTries 1` before `Match` (awk unit-tested in
`scripts/test-install-sshd-warn.sh`). **Match limits:** applies until EOF or the
next `Match`; first matching value wins; it caps tries **per TCP connection**, not
a cross-connection rate limit — forgetting it is an easy operator miss and
recreates orphan pending attempts (F9). R5 (**best-effort, TTL-bounded**): chunked
`/wait` (`EZKEY_WAIT_CHUNK_SECS` 5) + wait-scoped `SIGTERM` (see § M1 craft-rule /
B2a; Christophe security GO). When delivered, cancel uses
`ezkey_bounded_cancel_timeout(..., interrupted=1)` =
`min(api_timeout, EZKEY_INTERRUPT_CANCEL_MAX 5)` so worst case 105+5=110 <
`LoginGraceTime` 120 (#726). On OpenSSH 9.9 pre-auth client kill, SIGTERM often
does **not** arrive — cancel may be skipped; the **applied TTL** from create is
the residual bound. HTTP **429** during `/wait` → `UNAVAIL` (fail-closed). Do
**not** rely on `pam_end` under OpenSSH. Proof: `scripts/test-f9-retry.sh` A/B
gate CI; case C documents best-effort and asserts requested TTL + single create
(does not fail CI when cancel is absent). Client: `NumberOfPasswordPrompts=1`.

**`cancel=` vocabulary (authpriv only; never changes `pam_rc`):**

| Value | When |
|---|---|
| `ok` | Best-effort cancel returned HTTP 2xx |
| `fail` | Cancel curl error or HTTP other than 2xx/409 (e.g. 503) |
| `skipped` | Cancel not attempted (SUCCESS, pre-create, or no monotonic budget) |
| `final` | Cancel returned HTTP **409** already-final (possible late device approve / race) |

Proven in `scripts/test-timeouts.sh`: stub `cancel_final` → attempt line contains `cancel=final`.

**Never** logged: `secret_key`, integration key material, Basic `userpwd`, proof tokens, HTTP
bodies, wait JSON.

Demo containers: entrypoint enables `imuxsock` `SysSock.Use="on"` and disables `imjournal` so
lines land in `/var/log/secure` (no systemd journal in the SSH VM). On **AL2023 hosts**,
authpriv goes to journald — use `journalctl -t pam_ezkey` (not `/var/log/secure`).

### Conf contract (credentials)

| Rule | Behavior |
|---|---|
| `integration_api_url`, `integration_key`, `secret_key` | Only from conf file (not from ENV inside the `.so`) |
| `integration_api_url` missing or empty | `PAM_AUTHINFO_UNAVAIL` — **no** compiled-in `localhost` default (Patrick B1) |
| File ownership | Must be `root:root` |
| File mode | Exactly `0600` or `0400` (rejects `0000`, `0644`, etc.; root bypasses DAC) |
| Timeouts / debug / conf path | Conf file and/or `pam.d` module args only — not process ENV |
| Violation / missing field / unreadable | `PAM_AUTHINFO_UNAVAIL` (fail-closed) |
| Secrets in memory | Wiped (`explicit_bzero`) on every exit path |

Docker entrypoint maps compose ENV → conf write via `mktemp` under `/etc/security`
(not `/tmp`) + `install -m 600 -o root -g root` (rejects newline injection in ENV values).

### Tests added

| Script | Proves |
|---|---|
| `scripts/test-observability.sh` | success / reject / timeout / network → exactly one clean attempt line; secrets absent from secure + debug logs; polled waits. Requires clean-start + Demo Device (local). |
| `scripts/test-conf-security.sh` | debug.err **reason** asserts + stub ACCEPTED positive control + lab_http guard-only delta. |
| `scripts/test-challenge.sh` | Code in live capture **before** approve; wrong code refuse; stub absent → UNAVAIL. |
| `scripts/test-challenge-nits.sh` | Failing PAM conv → `UNAVAIL` + `lab_http=`. |
| `scripts/test-timeouts.sh` | api_timeout / wait_timeout thresholds + max clamp vs LoginGraceTime. |
| `scripts/test-install-sshd-warn.sh` | `install.sh` global `MaxAuthTries 1` awk warning semantics (no Docker; Patrick #908). |
| `test/test_challenge_harness.c` | Challenge bound 0..99 (make test). |
| `test/test_conf_security.sh` | in-container owner/mode/keys self-check (also via `up.sh` path) |
| `make test` | check-source + compile + filelog + sanitize + challenge harnesses |
| CI `ezkey-pam` job | `make test` + Rocky builder + `ci-m1-conf-security.sh` (conf-security + stub `test-timeouts.sh`) → `ci-gate` |

### Lessons learned (M1)

1. **Stock EL rsyslog ≠ Docker.** `imuxsock SysSock.Use=off` + `imjournal` means `syslog()` and
   `logger -p authpriv` never reach `/var/log/secure` without a journal. Fix at entrypoint, not
   by appending duplicate `authpriv.*` rules.
2. **Authpriv sink discipline.** Prior `log_pam_info` always called `syslog()`; M1 moves
   chatter to filelog-only so operators can `grep` `start` / `attempt` without noise or
   secret risk (two authpriv lines per call since #907 `start`).
3. **ENV vs conf.** The `.so` must not honor caller ENV for conf path / debug / timeouts
   (`su`/`sudo` inheritance). Entrypoint remains the *only* ENV→conf bridge for demos.
4. **No URL default.** A compiled `http://localhost:7080` would send ikey/skey to a local
   listener on a misconfigured host and could yield `PAM_SUCCESS` — removed (Patrick B1).
5. **Patrick / Christophe review surface.** Structure and conf keys stay small; fail-closed codes
   and the attempt line are the review anchors — see PR report file:line list.
6. **Stale Demo Device pending.** After failed/timeout SSH tries on a shared enrollment, a leftover
   pending card can be approved while a *new* SSH wait times out. Clear pending (or approve the
   matching `authAttemptId`) before re-running AL2023/`demo-ssh` after negative tests.
7. **SSH retries multiply attempts.** Each prompt round = one create. Demo
   `Match User testuser` / `MaxAuthTries 1` (host: **mandatory**
   `Match Group ezkey-mfa`) keeps “one SSH try → one create”; forgetting Match is
   an easy miss (Edgar #908 retest: 3 requests without it).
8. **OpenSSH 9.9 `PerSourcePenalties`.** Default `authfail:5` blocks the client IP after a handful
   of denied logins — the fifth M1 conf-security case (`missing secret_key`) saw SSH disconnect
   with no new attempt line. Demo `sshd_config` sets `PerSourcePenalties no` (lab only).
9. **CodeQL c-cpp.** Advanced setup formerly used `build-mode: none` for C/C++, which does not
   extract compiled C reliably. Workflow now builds `ezkey-pam` so `pam_ezkey.c` is in the DB.

### Two-digit challenge (number matching)

**Supported (2.2.4+).** Default **`challenge_requested=false`**: ordinary SSH MFA is
approve/reject only (simpler Demo Device path; number matching is opt-in for hosts that
want phishing-resistant matching). When `challenge_requested=true`:

1. `POST /api/v1/auth-attempts` with `challengeRequested=true`.
2. Integration API returns `authAttemptChallenge` (default **2** digits —
   `ezkey.core.auth-attempt.challenge-digits`).
3. Module requires the value in **0..99**; anything else → `PAM_AUTHINFO_UNAVAIL`.
4. Module displays `Ezkey challenge code: NN — enter this code…` via **`pam_prompt`
   (`PAM_PROMPT_ECHO_OFF`, digits in the prompt)** before `GET …/wait` so OpenSSH
   flushes the code to the PTY (bare `PAM_TEXT_INFO` is buffered; no `pam_info`
   fallback — Patrick). If no PAM conversation can show the code →
   `PAM_AUTHINFO_UNAVAIL` (Christophe).
5. Device must submit matching `challengeResponse` on approve; wrong digits → refuse.
6. Fail-closed: missing challenge → `PAM_AUTHINFO_UNAVAIL`.
7. Challenge value is **not** written to authpriv attempt lines (#750); attempt lines
   include `lab_http=true|false`.

**Lab proof target (no physical phone).** `./scripts/test-challenge.sh`:

1. Live PTY capture (`ssh-pty-capture.py`) must show `Ezkey challenge code: NN`
   **before** Enter + approve.
2. Demo Device approve with matching digits → `SUCCESS` + DB `ACCEPTED|NN`.
3. Wrong digits → SSH denied / non-SUCCESS.
4. Stub create without challenge → `UNAVAIL` + debug.err reason.

### Timeouts (Patrick / #726 / P3 budget) — path:line inventory

Single deadline: `pam_ezkey.c:917` `clock_gettime(CLOCK_MONOTONIC, &started)`.
Wall budget `ezkey_config.h:36` **`EZKEY_AUTH_BUDGET_SECS` 105** — **15 s margin** under
demo `LoginGraceTime` 120 (`sshd_config:20`). Effective `/wait` =
`min(conf, rem − CURL_SLACK)` at `pam_ezkey.c:814-825`; too little left →
`UNAVAIL` **with** attempt line (`pam_sm_authenticate` `done:` / `log_attempt_summary`).

**Craft rule (amended #907 B2a / Marc; R5 best-effort):** no `alarm` and no
global `sigaction`. **Exception — wait-scoped `SIGTERM` only:** installed solely
around chunked `/wait`, restored immediately after, handler only sets
`ezkey_wait_abort` (curl xferinfo aborts; no work in the handler). While
installed, that `SIGTERM` is **absorbed** (does not terminate the PAM child) so
cancel can run; after restore, default disposition applies again. **Why:**
OpenSSH portable *may* send `SIGTERM` to the PAM child on client disconnect
(`auth-pam.c:164-168`); catching it in the waiting process is the only path that
can cancel under `_exit`. **Honest limit:** on OpenSSH 9.9 pre-auth kill,
SIGTERM often never arrives — R5 cancel is then **best-effort**; the **applied
TTL** from create bounds the residual pending window. **Christophe security GO**
on this wait-scoped absorption. Enter bound remains measure-only (`pam_prompt` +
CLOCK_MONOTONIC, `pam_ezkey.c:779-796`); duration **>25 s** → UNAVAIL + attempt
line.

**Untested (documented only):** if Enter is **never** pressed, sshd
`LoginGraceTime` cuts the session with **no** attempt line; the Integration
API attempt expires server-side (`ezkey.core.auth-attempt.ttl-seconds` **120**).

| Knob | Where (path:line) | Lab default / bound | Beyond-threshold proof |
|---|---|---|---|
| Auth budget | `ezkey_config.h:36` **105**; rem helper `pam_ezkey.c:432-441` | 105 s (&lt; grace 120) | Delayed Enter 8s shrinks `?timeout=` (proven). Rem-exhaustion branches are defensive under api≤10 + Enter≤25 (rem stays ≥70) — not claimed proven in CI |
| `api_timeout` | clamp `pam_ezkey.c:208-211` (`EZKEY_MAX_API_TIMEOUT` `ezkey_config.h:37`); POST curl `pam_ezkey.c:666-676` | max **10** s | stub sleep 3 + conf `1` → UNAVAIL; conf **60** + stub sleep 12 → UNAVAIL in ≪60s (cap proven) |
| Enter prompt | `pam_ezkey.c:779-796` measure; `EZKEY_MAX_ENTER_SECS` `ezkey_config.h:40` **25** | duration bound (no signals) | Enter **>25 s** → UNAVAIL + attempt + debug.err duration (proven) |
| `wait_timeout` | clamp floor **15** / max **90**; `ttlSeconds` on create; `effective_wait` = min(conf, applied_ttl, rem−slack) | 15–90; shrunk by budget + applied TTL | conf=200 → ttl+`?timeout=90`; conf=2 → floor **15** (proven) |
| curl `/wait` | `EZKEY_CURL_WAIT_SLACK` **15** (cancel margin inside) | wait+≤15; ≤ rem | Covered by wait / delayed-Enter cases (proven) |
| Cancel | `POST …/cancel` best-effort on non-ACCEPTED; interrupt path = wait-scoped `SIGTERM` (B2a/R5); summary `cancel=ok\|fail\|skipped\|final` | ≤ api_timeout from rem; interrupt cap `min(api,5)`; **not guaranteed** at wait timeout **or** on pre-auth disconnect when SIGTERM is not delivered. Residual bound = **applied TTL** (≤90 s); late approve → orphan `ACCEPTED` (no SSH grant); residual = approval fatigue; cancel **409** → `cancel=final` (observability only) | pending→TIMEOUT + `cancel=ok`; EXPIRED + `cancel=ok`; `cancel_fail` 503 → `cancel=fail`; `cancel_final` 409 → `cancel=final`; SUCCESS → `cancel=skipped`; B2b kill-client = **best-effort** (case C asserts requested TTL + single create; does not require cancel ≤5 s) |
| Summary outcomes | `EXPIRED` / `TIMEOUT` / `DENIED` | lacune 5 | stub expired / pending / rejected (proven) |
| `wait_polling` | sample `config/pam_ezkey.conf` `wait_polling=2` | Server-side cadence | Sample conf present (proven) |
| Server attempt TTL | request `ttlSeconds`; response **`timeoutSeconds`** (PR-A #898) | Align down on applied ∈ `[1, planned]`; ignore grow / 0 / null | `applied_shrink` → applied; `applied_grow` 120 vs ask 45 → stay 45; `applied_zero` / `applied_null` → conf wait; legacy + reject/timeout (proven) |
| `LoginGraceTime` | `sshd_config:20` → **120** (runbook ≥120) | sshd cuts session | Budget 105 documented; Enter-never-pressed → cut **without** attempt line (documented, not lab-proven) |
| Auth `/pending` | `ezkey.rate-limit.pending` **10/min** | Device polling | Runbook note (Lightsail) |

### `/wait` response signature — deferred ([#858](https://github.com/mgagp/ezkey/issues/858))

| Question | Finding |
|---|---|
| Does Ezkey sign `/wait` JSON today? | **No.** `AuthAttemptWaitResponseDto` has no detached signature field. |
| What *is* signed? | Device **respond** payload (Ed25519) verified server-side before `ACCEPTED`. |
| What PAM trusts today | TLS (or lab `http://` + `lab_http`) + HTTP Basic; then `status==ACCEPTED`. |
| Why not M1? | Needs new API contract or device keys in the `.so` — disproportionate for lab. |
| Tracking | GitHub **#858** (non-lab host hardening). |
| URL scheme | **Case-insensitive** `https://` always OK; `http://` only with `lab_http=true`. |

### Review inventory (Patrick + Christophe) — done / deferred

| Source | Item | Status |
|---|---|---|
| Patrick B1 | No URL default; empty/missing → UNAVAIL + test | **Done** |
| Patrick | Conf via temp + `install -m 600`; heredoc CR/LF reject | **Done** |
| Patrick | Non-root owner + mode `0000` tests | **Done** |
| Patrick | Document `EZKEY_CONFIG_FILE` (entrypoint-only) | **Done** |
| Patrick | CI builds/tests module; CodeQL c-cpp manual build | **Done** (`ezkey-pam` job + `ci-m1-conf-security`) |
| Patrick | `PerSourcePenalties no` in demo limits | **Done** |
| Patrick | URL absent deletes key line (nit) | **Done** (extra conf-security case) |
| Patrick | `-Wformat-truncation` POST debug | **Done** |
| Patrick | Full `test-observability` in CI | **Deferred** — needs clean-start + Demo Device; CI runs conf-security (B1/0000/non-root) + sanitize harness instead |
| Christophe | Sanitize space/`=`/`>=0x80` | **Done** |
| Christophe | Wipe secrets all exits | **Done** |
| Christophe | No process ENV in `.so` | **Done** |
| Christophe | Exact mode 0600\|0400 (reject 0000) | **Done** |
| Christophe | Document UNAVAIL + `sufficient` fail-open | **Done** (STATUS, README, `sshd.host-sketch`) |
| Christophe | Deterministic waits; secret grep all logs; fail if keys empty | **Done** |
| Christophe | Conf mktemp not under `/tmp` | **Done** (`/etc/security`) |
| Christophe | `pam_rc_for_http` 401/404 → UNAVAIL | **Done** (documented; branches intentional) |
| Christophe | Demo debug always on / no filelog rotation | **Deferred** — lab intentional; host sample `debug=false` |
| Christophe / follow-up | Refuse `http://` outside lab | **Done** (`lab_http`, default false) |
| Christophe / follow-up | `/wait` signature on PAM | **Deferred** — [#858](https://github.com/mgagp/ezkey/issues/858) |
| Marc budget | Two-digit challenge UX | **Done** (default false; opt-in `pam_prompt`) |
| Christophe GO | Challenge bound 0..99 → else UNAVAIL | **Done** |
| Christophe GO | No conv to display code → UNAVAIL | **Done** |
| Christophe GO | `lab_http=` on attempt authpriv line | **Done** |
| Patrick CHANGES | conf tests assert debug.err reason + stub positive control | **Done** |
| Patrick CHANGES | challenge visible before approve; wrong/absent cases | **Done** |
| Patrick CHANGES | timeout inventory + wait clamp below LoginGraceTime | **Done** (`test-timeouts.sh`) |
| Patrick P3 | Single auth budget 105; dynamic `?timeout=`; Enter monotonic (no signals) | **Done** (`test-timeouts.sh` stub + CI) |
| Marc #907 B2a | Keep wait-scoped `SIGTERM` (absorbed during wait; flag-only; install/restore; sshd `auth-pam.c:164-168`) | **Done** — craft rule amended; **Christophe security GO** |
| Christophe #907 M-a | `rem<=0` → TIMEOUT break (no `curl_limit=1`) | **Done** |
| Christophe #907 M-b | `sshd -T -C user=… \| grep -qx 'maxauthtries 1'` (not regex) | **Done** |
| Marc #907 R5 | Disconnect cancel = **best-effort, TTL-bounded** (no new mechanism; SIGTERM kept) | **Done** — case C documents expected miss; A/B gate CI |
| Patrick CHANGES | https scheme case-insensitive + no pam_info fallback | **Done** |
| #895 PR-B | `ttlSeconds` + applied-TTL align + cancel + EXPIRED/TIMEOUT/DENIED | **Done** (`test-timeouts.sh` + README runbook) |
| Patrick #897 CHANGES | stub `legacy_create` / `cancel_fail`; applied-shrink; budget harness (rem→−1) | **Done** (`test-budget` + `test-timeouts.sh`) |
| Patrick #897 M1/M2 | `applied_grow` / zero / null; `cancel=ok\|fail\|skipped`; legacy+reject/timeout | **Done** (`test-timeouts.sh`) |
| Christophe #897 obs | cancel HTTP 409 → `cancel=final` (`EZKEY_CANCEL_FINAL`); no pam_rc change | **Done** (`cancel_final` stub) |

### Out of M1

Host sshd cutover, TLS verify knobs / curl CA pins, packages, `failmode=safe`, sudo, username maps,
AWS, ezkey.online, wait-receipt signatures, full observability success/reject in CI.
