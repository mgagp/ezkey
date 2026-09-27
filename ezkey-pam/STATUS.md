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

1. **Dogfood (EXP1 / Lightsail).** Protect the lab VM that already runs Ezkey. Today that host is
   Amazon Linux 2023 (`ec2-user`) on Lightsail; EXP1 is the experimental public lab, not yet a
   future `ezkey.online` cutover. If the module cannot be installed, rolled back, and survived
   when Integration API is down, it is not ready for this path.
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

Version **2.1.0** is a **working lab demo** plus STATUS §2.8 slices **#1–#3** (hygiene, PAM
return codes, AL2023 builder/mirror). It is still **not** a host-operable MFA agent on EXP1
(sshd cutover remains slice #4+).

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

1. Read `/etc/security/pam_ezkey.conf` (optional PAM arg `conf=`, optional env overrides).
2. `POST /api/v1/auth-attempts` with HTTP Basic (`integration_key` / `secret_key`).
3. `GET /api/v1/auth-attempts/{id}/wait`.
4. Return codes (fail-closed; never `PAM_IGNORE` / `PAM_SUCCESS` on API failure):

| Situation | Return |
|---|---|
| Wait `ACCEPTED` | `PAM_SUCCESS` |
| Reject, expire, not ACCEPTED | `PAM_AUTH_ERR` |
| Conf unreadable / missing keys / DNS / TLS / HTTP unexpected / curl fail | `PAM_AUTHINFO_UNAVAIL` |
| Empty username | `PAM_USER_UNKNOWN` |
| OOM / internal | `PAM_SERVICE_ERR` |

### Configuration today

File format: `key=value` at `/etc/security/pam_ezkey.conf`, mode `0600` in the demo image.
That path and style match Linux-PAM convention (`pam_access`, `pam_limits`, `pam_faillock`).

| Key | Role | Demo-adequate? |
|---|---|---|
| `integration_api_url` | Integration API base URL | Yes for Docker DNS; EXP1 needs HTTPS |
| `integration_key` / `secret_key` | Basic auth | Yes; file perms are the control |
| `wait_timeout` / `wait_polling` / `api_timeout` | HTTP waits | Yes |
| `challenge_requested` | Extra numeric challenge | Yes (keep `false` for SSH) |
| `context_title` / `context_message` | Device copy | Yes |
| `debug` | Extra syslog + `/tmp` files | Default **false** in sample conf; demo entrypoint may set `true` via `EZKEY_DEBUG` |

PAM args: `debug`, `conf=/path`. Env overrides exist for Docker because **sshd does not pass
container ENV into PAM**; the entrypoint writes the conf file. That lesson stays.

**Verdict:** the current parameter set is **enough for the demo** and is the right *shape* for
v1 operable use. It is not missing a second config system. Remaining gaps for EXP1 are
operational (TLS, live sshd cutover), not “more keys.”

### Known demo limits (honest)

- **HTTP** to `integration-api:7080`. No TLS verify knobs. Fine on a compose network; not fine
  on a public hostname. (Slice #4+)
- **Demo PAM stack is Ezkey-only** (`pam_ezkey` + `pam_permit`). Host-oriented sketch lives in
  [`sshd.host-sketch`](sshd.host-sketch) — not applied to EXP1.
- **`install.sh` can still build on the host.** Prefer image-matched extract for real hosts
  (`./scripts/build-al2023.sh --extract` for AL2023).

### Slices #1–#3 done (this eval)

1. Hygiene: AUTHPRIV syslog; no bodies/secrets in logs; `/tmp` gated on debug; conf-open errors;
   curl init/cleanup; `explicit_bzero` on Basic `userpwd` and secret copies after HTTP.
2. PAM return-code map + fail-closed / break-glass stack docs (`STATUS` + `sshd.host-sketch`).
3. AL2023 builder + extract + smoke + SSH mirror compose profile.

Still out of scope here: EXP1 sshd cutover, TLS enrollment, packages, `failmode=safe`.

---

## 2. What “operable” would require (still small)

Work in **slices**. Dogfood on EXP1 is the acceptance test. Generic packaging is a by-product of
that, not a parallel product.

### 2.1 Failure posture (do this first, conceptually)

Ezkey design principle: name fail-open vs fail-closed at the boundary.

| Boundary | Posture | Why |
|---|---|---|
| PAM module vs Integration API / timeout / reject | **Fail-closed** | Continuing would grant SSH without the claimed MFA |
| EXP1 operator lock-out if Ezkey is down | **Break-glass in the PAM *stack* and Lightsail console**, not fail-open in the module | Availability without silently weakening MFA |
| Debug / audit side effects | Fail-open (login still decided by Ezkey) | Logging must not become a second authenticator |

Do **not** add a Duo-like `failmode=safe` switch in v1. That flag is how MFA modules quietly
become optional. If EXP1 needs a login when Ezkey is down, name a **local break-glass account**
(or Lightsail serial/browser console) in `/etc/pam.d/sshd` with `pam_succeed_if` / `pam_unix`,
and keep `pam_ezkey` `required` for everyone else.

Demo stack (Ezkey-only) stays valid **only** for the Docker SSH VM.

Suggested EXP1 stack (sketch, not shipped):

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
4. **API key IP allowlist.** Demo keys have none. EXP1 should allow the Lightsail host (or the
   Docker bridge if PAM runs in a container). That is Integration API policy, not C code.
5. **`debug` default false.** Syslog via `pam_syslog` / `LOG_AUTHPRIV` only. No `/tmp` transcripts.
   Never log secret keys, Basic headers, or wait bodies.
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
- Drop `/tmp/pam_ezkey.out` outside debug-demo builds.
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

# Amazon Linux 2023 (EXP1 ABI)
docker build -f Dockerfile.al2023 --target builder-al2023 -t ezkey-pam-builder-al2023:local .
# or: ./scripts/build-al2023.sh --extract
```

Then on the host: install the `.so` next to other PAM modules, install conf `0600`, add one
`/etc/pam.d/sshd` line, reload `sshd`. `install.sh` can wrap that **after** it copies a
pre-built `.so` instead of compiling on EXP1.

| Path | Builder image | Notes |
|---|---|---|
| Lab demo (done) | Rocky Linux 10 | Nested SSH VM |
| EXP1 ABI (done for build/mirror) | Amazon Linux 2023 | `builder-al2023` + `runtime-al2023` / smoke; install on Lightsail host is slice #5 |
| Later generic EL | Rocky / RHEL 9–10 | Same family as the demo |
| Debian/Ubuntu | later | New builder stage when a host exists |

No project Release is required. The artifact is the `.so` + a 20-line conf + a PAM snippet.

### 2.6 Dogfood on EXP1 (Lightsail, Amazon Linux 2023)

EXP1 already runs the Ezkey APIs in Docker on that VM
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
required by a real host — not as a prerequisite for EXP1.

### 2.8 Suggested slice order

Each slice should stay mergeable on its own:

1. ~~Hygiene: syslog-only, no proof tokens in logs, `debug` default false, conf-open errors logged.~~ **Done (2.1.0 eval).**
2. ~~PAM return codes + documented fail-closed / break-glass stack (still Rocky demo).~~ **Done**
   (`sshd.host-sketch`).
3. ~~Amazon Linux 2023 builder target + extract instructions; test in an AL2023 container.~~ **Done**
   (`builder-al2023`, `smoke-al2023`, `ezkey-pam-ssh-al2023`).
4. TLS + EXP1 integration/enrollment/key; dry-run on a spare user before changing `ec2-user`.
5. EXP1 sshd cutover with rollback window.
6. Only then: a generic “copy this snippet onto EL10” note. Packages if a second distro appears.

---

## Other notes worth keeping

- **SELinux / firewall.** sshd making outbound HTTPS from a PAM module may need an allow rule.
  Deal with it on the AL2023 dogfood host; do not pre-build a policy pack.
- **sudo later.** Same `.so` can appear in `/etc/pam.d/sudo` once SSH is boring. Do not couple
  the first slice to sudo.
- **Challenge PIN.** Keep `challenge_requested=false` for SSH. A PIN is essential complexity for
  a TTY-less or delayed SSH UX.
- **Observability.** Operator proof is Ezkey audit (`AUTH_ATTEMPT_*`) plus host `authpriv`. Do not
  add a metrics sidecar in the module.
- **Methodology.** This stays a hygiene/lab map in `ezkey-pam/`. Promote an `I-*` / `TB-*` only
  if EXP1 cutover is funded as a program slice. Until then, do not grow a second documentation
  tree.

When unsure, prefer the Rocky demo as the living test, change one operational concern at a time,
and refuse features that would make the module smarter than the Integration API.
