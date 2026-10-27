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

Version **2.2.18** (#1016 cycle 2: SSLKEYLOGFILE refuse, conf-line wipe,
URL `@` → `scheme://-`, LDLIBS after objects, FORTIFY/RELRO/DT_NEEDED
guards). Prior **2.2.17** (#1016 cycle 1b transport/conf/hardening). Prior
**2.2.16** (#1016 snapshot/probes). Prior **2.2.15** (#1011 diagnostics).
Prior **2.2.14** (#995: connector default `challenge_requested=true`). Prior
**2.2.13** (#982 tiers 0–1 on #976: ECHO_ON confirm + challenge Enter-only
wait). Prior **2.2.12** (#975 / PR #976: **confirm-before-create** + display
server-provided challenge even when host `challenge_requested=false`; builds on
**2.2.11** #941 F1 waiting line via `pam_prompt`, and **2.2.10** #907 F9 option A:
sshd `Match` MaxAuthTries; chunked wait + best-effort disconnect cancel
TTL-bounded; honest OpenSSH fork limit) plus STATUS §2.8 slices **#1–#3** and
**M1**. Canonical SSH ↔ Integration API behaviour:
[`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md). It is still **not** a
host-operable MFA agent on a real host (sshd cutover remains slice #4+).
**Experimental** status is unchanged pending Marc host validation.

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
| `challenge_requested` | Two-digit number-matching challenge | **Default `true` (#995)**. Connector owns the default (safer for SSH). Operator types **`ok` before create** (confirm-before-create; anti-accident; 25 s Enter cap). Create asks for a challenge unless conf opts out with `false`; when the **enrollment** has `authAttemptChallengeRequired` the server still forces a code. Unknown/empty conf values keep the prior default + warning. Valid challenge in the response → **second** `pam_prompt` (#975; challenge Enter = rem-budget / #982). `true` + missing/out-of-range → UNAVAIL. See [`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md) |
| `lab_http` | Allow `http://` URL | Docker entrypoint sets `true`; host sample default `false` |
| `wait_timeout` | `/wait` client wait + create `ttlSeconds` (seconds) | Default **90**; clamped **15–90** (`EZKEY_MIN_TTL_SECONDS` / `EZKEY_MAX_WAIT_TIMEOUT`); aligned on applied TTL from create response (#895) |
| `context_title` / `context_message` | Device copy | Yes |
| `debug` | Extra filelog under `/var/log/ezkey-pam/` (`O_NOFOLLOW`, 0600) | Docker lab entrypoint sets `true`; sample conf default `false` (#818) |

PAM args (root-owned `pam.d` only): `debug`, `conf=/path`, optional `wait_timeout=N` (overrides conf).

**Process ENV is not read by `pam_ezkey.so` for conf path, credentials, debug,
or timeouts.** Compose may still set `EZKEY_INTEGRATION_*`, `EZKEY_WAIT_*`,
`EZKEY_DEBUG`, and optionally `EZKEY_CONFIG_FILE` for **`docker-entrypoint.sh`
only** — the entrypoint writes the root-owned conf via `mktemp` under
`/etc/security` (never `/tmp`) + `install -m 600 -o root -g root`. That bridge
exists because sshd does not pass Docker ENV into PAM. A caller-controlled
environment (e.g. `su`/`sudo`) cannot redirect conf path, debug, or timeouts
inside the `.so`. **Exception (#1016 C2):** libcurl would honour
`http_proxy` / `https_proxy` / `ALL_PROXY` unless the module clears the proxy
(`CURLOPT_PROXY ""` on every easy handle). Without that guard, Basic auth could
reach a caller-controlled proxy in `lab_http` `http://` mode. **Exception
(#1016 S1):** libcurl/OpenSSL honour `SSLKEYLOGFILE` — with it set, a root PAM
child would create the caller-chosen path (typically mode `0644`) and write TLS
session keys. The module refuses authenticate early (`UNAVAIL`, `reason=env`,
no network call) when `getenv("SSLKEYLOGFILE")` is non-NULL. On the measured
build, `SSL_CERT_FILE` / `SSL_CERT_DIR` / `CURL_CA_BUNDLE` are **not** honoured;
that is a build-specific observation and is **not** guaranteed on other libcurl
builds — do not treat those names as a complete ENV surface.

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
  **Without an id only the server TTL bounds the request** (#1016 C7): if create times out
  or the response is lost before `authAttemptId` is known, PAM cannot cancel and a phone
  may still receive a request nobody waits for. An idempotency key is out of scope.

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

Conversation: use English `PAM_PROMPT_ECHO_OFF` lines so keyboard-interactive starts **and**
OpenSSH flushes the waiting / challenge text to the PTY before `/wait` (#941). Do not rely on
bare `pam_info` / `PAM_TEXT_INFO` for client-visible waiting copy — OpenSSH buffers it until the
next prompt or the end of the round. French (or any locale) belongs in `context_title` /
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
- **Challenge (number matching).** Default **`challenge_requested=true`** (#995): create
  requests a code; response `authAttemptChallenge` is shown with `pam_prompt` before `/wait`.
  Device must enter the same code. Set conf `false` to opt out of *connector-requested*
  challenge (when the **enrollment** has `authAttemptChallengeRequired` the server still
  shows a code; OR since `e5711d4d` / 2025-06-28 — no PAM min-server gate). Decision: Alex,
  confirmed by Marc on 2026-10-22 — safer SSH default; connector owns it (refs #995).
  Experimental / never released: no release note or rollback procedure. Case C (operator
  “require a code” beyond conf/enrollment) → [#997](https://github.com/mgagp/ezkey/issues/997).
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
pam_ezkey: attempt user=<linuxUser> authAttemptId=<id|-> result=<R> pam_code=<n> latency_ms=<ms> lab_http=<true|false> cancel=<ok|fail|skipped|final> timeoutSeconds=<n|-> stage=<S> reason=<R> curl_rc=<n|-> http=<n|-> confirm_ms=<n|-> api_ms=<n|->
```

| Field | Meaning |
|---|---|
| `user` | Linux username (sanitized); `-` if unavailable |
| `authAttemptId` | Numeric id from create, or `-` if never created |
| `result` | `SUCCESS` \| `EXPIRED` \| `TIMEOUT` \| `DENIED` \| `MAXTRIES` \| `UNAVAIL` \| `CONFIRM` \| `SIGNAL` \| `ERROR` \| `UNKNOWN_USER` |
| `pam_code` | Integer PAM return code |
| `latency_ms` | Monotonic elapsed ms for the authenticate call, or `-` if unknown (never invent `0`) |
| `lab_http` | Conf `lab_http` as `true` or `false` (Christophe) |
| `cancel` | Exactly one of `ok` \| `fail` \| `skipped` \| `final` (`EZKEY_CANCEL_*`; Patrick #897 M2 + Christophe observability) |
| `timeoutSeconds` | Planned wait on `start` (before create); applied TTL on `attempt` (or `-`) (#907) |
| `stage` | Fixed vocab: `init` \| `conf` \| `confirm` \| `create` \| `challenge` \| `wait` (#1011) |
| `reason` | Fixed vocab (e.g. `empty_confirm`, `curl`, `http`, `budget`, `signal`, `challenge_missing`, `-`) — never free-form secrets |
| `curl_rc` | libcurl code from the last create/wait call (including SUCCESS), or `-` |
| `http` | HTTP status from the last create/wait call (including SUCCESS), or `-` |
| `confirm_ms` | Pre-create `ok` prompt duration (ms), or `-` if unknown |
| `api_ms` | Cumulative create/wait HTTP time (ms), or `-` |

**#1011 notes:** `CONFIRM` = empty/wrong pre-create `ok` (`pam_code` =
`PAM_AUTHINFO_UNAVAIL` / Linux `9`; `EZKEY_PAM_AUTHINFO_UNAVAIL` in diag init).
Only **SIGTERM** is handled during authenticate (abort during `/wait`, or
best-effort SIGNAL + `SIG_DFL` + re-raise outside `/wait`). Other signals
(HUP, USR1, ALRM, PIPE, INT, QUIT, …) keep the process default action: the
PAM child dies with no cancel and no SIGNAL line, never SUCCESS, and a
created attempt stays pending until its TTL (#1016 C5).

`SIGNAL` = best-effort authpriv line when SIGTERM arrives **outside** `/wait`
(prompts or create): async-signal-safe non-blocking `write` to `/dev/log`
(`SOCK_NONBLOCK` or `fcntl O_NONBLOCK`; full queue → drop the line), then
`SIG_DFL` + `raise(SIGTERM)` so the child exits (no hung `[pam]` under OpenSSH
atomicio). The SIGNAL line is a **best-effort snapshot**: `stage` may be stale,
`authAttemptId=-` and `cancel=skipped` are expected outside `/wait` (no cancel
is attempted there — the Integration API attempt stays pending on the device
until TTL). Snapshot length is double-buffered (#1016 N5): a TERM mid-refresh
still emits the previous complete line (never a zeroed active length). Wait-path
SIGTERM still uses abort → cancel → normal summary (`reason=signal`, often
`cancel=ok`) — #1012 unchanged. **D1 — do not document “never SUCCESS after
client gone”:** if TERM is absorbed during `/wait` after the ACCEPTED answer
already arrived in the same chunk, PAM can return `SUCCESS` to an sshd whose
client has left; that is not an elevation. Residual window: SIGTERM between
`/wait` ACCEPTED and clearing `ezkey_in_wait` is still absorbed as abort (doc
only). SIGNAL lines use real `latency_ms` when the auth clock is known;
otherwise `latency_ms=-` / `confirm_ms=-`. Experimental / never released: no
log-format compatibility promise.

**#1016 T2 `/dev/log` coverage:** host `make test` without a log-daemon socket
(dangling `/dev/log`, no journald/rsyslog) **skips** the live path. CI Rocky
runtime (rsyslog up) runs `test_diag_harness` with
`EZKEY_PAM_REQUIRE_DEVLOG=1` so the real `/dev/log` O_NONBLOCK path is
required, not skipped.

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
effective `maxauthtries 1` via `sshd -T -C user=…` and **warn** (do not rewrite
`sshd_config`) on global `MaxAuthTries 1` before `Match` using the shared helper
`scripts/sshd-warn-global-maxauthtries-1.sh` + sibling
`sshd-warn-global-maxauthtries-1.awk` (same code path as
`scripts/test-install-sshd-warn.sh` — case-insensitive, exact `1` only, tab-safe;
missing awk WARNs, never silent-skip; `EZKEY_SSHD_WARN_AWK` overrides path for
mutation tests). **Match limits:** applies until EOF or the next `Match`;
first matching value wins; it caps tries **per TCP connection**, not a
cross-connection rate limit — forgetting it is an easy operator miss and
recreates orphan pending attempts (F9). Host: `groupadd ezkey-mfa` +
`usermod -aG ezkey-mfa <user>` so `Match Group` applies. Keep
`PubkeyAuthentication no` with `MaxAuthTries 1` (refused pubkey would consume the
single try; `publickey,keyboard-interactive` needs a higher try budget — out of
scope). R5 (**best-effort, TTL-bounded**): chunked
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
| Secrets in memory | Module buffers wiped (`explicit_bzero` on conf line, `userpwd`, `integration_key` / `secret_key`); libcurl-internal and residual stack copies are **not** guaranteed (#1016 S2) |

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
| `scripts/test-install-sshd-warn.sh` | Runs shared `sshd-warn-global-maxauthtries-1.sh` (install.sh + entrypoint); exact `awk -f "${SSHD_WARN_AWK}"`; missing-file warn; mutation via `EZKEY_SSHD_WARN_AWK` (no Docker; #908 / #934). |
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

### SSH waiting message and two-digit challenge

**Confirm-before-create + fluidity (#982 tiers 0–1 / 2.2.13; builds on #976 /
#941 F1).** Canonical matrix:
[`docs/INTERACTION-MODEL.md`](docs/INTERACTION-MODEL.md).

Default **`challenge_requested=true`** (#995 — connector default):

1. **Pre-create** `pam_prompt(PAM_PROMPT_ECHO_ON)`:
   `SSH login for user <user>.` +
   `Type ok and press Enter to request device approval:`
   (#982 M1 — no “Approve on your phone” before the request exists). The `ok`
   prompt is an **anti-accident filter for batch clients**, not a security
   defense (stays as-is per #995).
2. Trimmed case-insensitive **`ok`** required. Empty / bare Enter / EOF →
   `UNAVAIL` with **zero** creates (D4 / S2 / S3).
3. `POST /auth-attempts` with `challengeRequested=true` (unless conf opts out).
4. Valid challenge (0..99) in the response → **second** `PAM_PROMPT_ECHO_ON`
   with digits + `Approve on your phone, then press Enter:` (no second Type ok);
   after Enter, poll `/wait` until ACCEPTED / REJECTED / TTL. Challenge Enter
   has **no** 25 s cap — rem auth-budget only (#982 Patrick B1). Digits never in
   authpriv (#750). Opt-out (`false`) with no enrollment-required code skips
   the second prompt and goes straight to `/wait`.

OpenSSH buffers bare `pam_info` / `PAM_TEXT_INFO` until the next prompt (Edgar
F1) — hence a real prompt (now ECHO_ON). One authenticate call → at most one
create under `Match … MaxAuthTries 1`. Username controls sanitized
(`ezkey_sanitize_for_prompt`).

**No TTY / empty auto-answer (#956 M2 + #975 D4 + confirm-before-create).**

| Client situation | Pre-2.2.12 | 2.2.12 |
|---|---|---|
| No `PAM_CONV` / `PAM_CONV_ERR` | UNAVAIL | UNAVAIL (0 creates) |
| `ssh -T </dev/null` (empty EOF) | **SUCCESS if device approved** | **CONFIRM + 0 creates** (#1011) |
| Interactive bare Enter | SUCCESS path continued | **CONFIRM + 0 creates** (#1011) |
| `BatchMode` + pubkey outside MFA Match | unchanged | unchanged |

**D1 (known):** some Windows OpenSSH clients without a console hang until
`LoginGraceTime` 120 s. Confirm-before-create: hang is on the **pre-create**
prompt → **no** Integration API attempt. Prefer `ssh -tt` or non-MFA Match
accounts for automation.

**Quit at prompts (#956 M3).** Wait-scoped `SIGTERM` is armed only around
chunked `/wait`. **Pre-create** Ctrl-C → nothing created. **After create**
(challenge prompt or wait): conversation error may cancel; otherwise attempt
until applied TTL (≤ 90 s). Arming SIGTERM during `pam_prompt` was declined
(Christophe / STATUS M3).

**Two-digit challenge (2.2.4+; display + confirm-before-create 2.2.12 / #975).**

1. Host `challenge_requested=true` **or** enrollment `authAttemptChallengeRequired`.
2. Create returns `authAttemptChallenge` (default 2 digits).
3. **Display rule (#975):** valid 0..99 in the response → show on the **second**
   prompt, independent of host conf. Pre-2.2.12 dropped enrollment-required codes.
4. Fail-closed when host requested and code missing/out-of-range → UNAVAIL + cancel.
5. Device must submit matching `challengeResponse`; wrong digits → DENIED.
6. Challenge value **not** in authpriv (#750).

**Lab proof target (no physical phone).**

- Confirm-before-create (#975): Approve/`ok` visible with **creates==0**; after
  `ok`, exactly **1** create (`test-timeouts.sh`); D4 `ssh -T </dev/null` + bare
  Enter → UNAVAIL + **0 creates**.
- Enrollment challenge (#975): stub `enrollment_challenge` + conf false → code
  `42` on 2nd prompt; SUCCESS without digits in authpriv.
- Delayed / >25 s Enter on pre-create (0 creates); challenge Enter@30s with
  already-approved → SUCCESS (B1); challenge Enter>88s → UNAVAIL + cancel=ok
  (B3 rem-budget) in `test-timeouts.sh` (#982).
- Challenge live: `./scripts/test-challenge.sh` Cases A–D (pre-create `ok` then
  challenge prompt). Harness: `ezkey_prewait_prompt_mode` /
  `ezkey_prewait_response_confirmed`.

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
on this wait-scoped absorption. Pre-create Enter bound remains measure-only
(`pam_prompt` + CLOCK_MONOTONIC); duration **>25 s** → UNAVAIL + attempt line
(0 creates). Challenge (code) Enter: **no** 25 s cap — rem-budget only (#982
Patrick B1).

**Approve-vs-cancel race (#982 / #726):** phone may ACCEPTED while the
operator still sits on the code prompt. A 25 s Enter cap on that prompt would
refuse and `POST …/cancel` an already-approved attempt. Fix: challenge
`prompt_echo_on(require_ok=0)` keeps only the remaining auth-budget bound;
proof = Enter@30s + stub already ACCEPTED → SUCCESS + `cancel=skipped` (B1).
Rem exhaustion on that prompt (elapsed **>88 s**, rem ≤ CURL_SLACK+MIN_WAIT) →
UNAVAIL + cancel (B3). **If the request is already ACCEPTED** when rem-exhaust
cancel runs, the server conditional UPDATE matches 0 rows → HTTP **409** →
`cancel=final`; **login is still refused** (PAM never reached `/wait`
ACCEPTED).

**Untested (documented only):** if Enter is **never** pressed, sshd
`LoginGraceTime` cuts the session with **no** attempt line; the Integration
API attempt expires server-side (`ezkey.core.auth-attempt.ttl-seconds` **120**).

| Knob | Where (path:line) | Lab default / bound | Beyond-threshold proof |
|---|---|---|---|
| Authpriv line max | `ezkey_diag.h` **`EZKEY_DIAG_LINE_MAX` 768** | ≥ measured worst ~416 | `test_diag_harness` worst-case format fits (proven) |
| Attempt `user[]` | `EzkeyDiag.user[128]` (`ezkey_diag.h`) | sanitize + truncate | `test_diag_harness` huge user → `&lt;128` incl NUL (proven) |
| Auth budget | `ezkey_config.h` **105**; rem helper `remaining_budget_secs` | 105 s (&lt; grace 120) | Delayed Enter 8s shrinks `?timeout=` (proven). Challenge Enter **>88 s** → UNAVAIL + cancel (B3 rem-exhaust proven) |
| `api_timeout` | clamp / `EZKEY_MAX_API_TIMEOUT` **10**; POST curl | max **10** s | stub sleep 3 + conf `1` → UNAVAIL; conf **60** + stub sleep 12 → UNAVAIL in ≪60s (cap proven) |
| Enter (pre-create) | `prompt_echo_on(require_ok=1)`; `EZKEY_MAX_ENTER_SECS` **25** | duration bound (no signals) | Enter **>25 s** → UNAVAIL + 0 creates (proven) |
| Enter (challenge / code) | `prompt_echo_on(require_ok=0)`; rem budget only — **no** 25 s cap; after-Enter check `pam_ezkey.c` rem ≤ CURL_SLACK+MIN_WAIT | rem &gt; 17 (105−88) | Enter@30s + ACCEPTED → SUCCESS (B1); Enter**>88 s** → UNAVAIL + `cancel=ok` (B3) |
| `wait_timeout` | clamp floor **15** / max **90**; `ttlSeconds` on create; `effective_wait` = min(conf, applied_ttl, rem−slack) | 15–90; shrunk by budget + applied TTL | conf=200 → ttl+`?timeout=90`; conf=2 → floor **15** (proven) |
| curl `/wait` | `EZKEY_CURL_WAIT_SLACK` **15** (cancel margin inside) | wait+≤15; ≤ rem | Covered by wait / delayed-Enter cases (proven) |
| Cancel / concurrency | PAM `POST …/cancel`; server **`AuthAttemptRepository.expireIfPendingOrRead`** conditional UPDATE (`AuthAttemptService.cancel`, #833 m1) — wins approve/deny race without read-modify-write | ≤ api_timeout from rem; interrupt cap `min(api,5)`; UPDATE 0 rows when already final → HTTP **409** → `cancel=final` (login refused if PAM never saw `/wait` ACCEPTED) | pending→TIMEOUT + `cancel=ok`; EXPIRED + `cancel=ok`; `cancel_fail` 503 → `cancel=fail`; `cancel_final` 409 → `cancel=final`; SUCCESS → `cancel=skipped`; B3 rem-exhaust → `cancel=ok`; already-ACCEPTED + rem-exhaust cancel → **409 / final** (documented); B2b kill-client = **best-effort** |
| Summary outcomes | `EXPIRED` / `TIMEOUT` / `DENIED` | lacune 5 | stub expired / pending / rejected (proven); #982 challenge path: PAM TIMEOUT vs server EXPIRED both covered |
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
| Marc budget | Two-digit challenge UX | **Done** (default **true** since #995; opt-out via conf) |
| #995 (Alex / Marc 2026-10-22) | Connector default `challenge_requested=true`; no server PAM type | **Done** (2.2.14) |
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
| #941 F1 (Edgar) | No-challenge waiting line via `pam_prompt` before `/wait` (not buffered `pam_info`) | **Done** (2.2.11) |
| #975 (Isabelle) | Show server challenge even when `challenge_requested=false` (enrollment-required) | **Done** (2.2.12) |
| #975 D4 / confirm-before-create | `ok` **before** create; empty/`ssh -T`/Ctrl-C → **0 creates**; challenge as 2nd prompt | **Done** (2.2.12 / #976) |
| #975 D1 | Windows no-TTY hang until LoginGraceTime 120 s (known; document) | **Documented** (2.2.12) |
| #956 Patrick E1 | Stub suites + before-wait / no-conv asserts in `ezkey-pam` CI job | **Done** |
| #956 Patrick M1 | No-challenge delayed Enter + Enter >25 s in `test-timeouts.sh` | **Done** |
| #956 Patrick M2 | No-TTY / empty auto-answer fail-closed (corrected: require `ok`) | **Done** (2.2.12) |
| #956 Patrick M3 | Document SIGTERM only during `/wait`; quit-at-prompt → TTL ≤90 s | **Done** (document; no early arm) |
| #956 Patrick nit a | `mktemp -d` + fifo inside (common.sh / timeouts / f9) | **Done** |
| #956 Patrick nit b | Sanitize username controls before waiting prompt | **Done** |
| #895 PR-B | `ttlSeconds` + applied-TTL align + cancel + EXPIRED/TIMEOUT/DENIED | **Done** (`test-timeouts.sh` + README runbook) |
| Patrick #897 CHANGES | stub `legacy_create` / `cancel_fail`; applied-shrink; budget harness (rem→−1) | **Done** (`test-budget` + `test-timeouts.sh`) |
| Patrick #897 M1/M2 | `applied_grow` / zero / null; `cancel=ok\|fail\|skipped`; legacy+reject/timeout | **Done** (`test-timeouts.sh`) |
| Christophe #897 obs | cancel HTTP 409 → `cancel=final` (`EZKEY_CANCEL_FINAL`); no pam_rc change | **Done** (`cancel_final` stub) |

### Out of M1

Host sshd cutover, TLS verify knobs / curl CA pins, packages, `failmode=safe`, sudo, username maps,
AWS, ezkey.online, wait-receipt signatures, full observability success/reject in CI.
