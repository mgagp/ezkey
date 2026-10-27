# ezkey-pam ↔ Ezkey interaction model (SSH keyboard-interactive)

Canonical home for how ezkey-pam talks to the Integration API and to the SSH
client during one keyboard-interactive login. Maintained with the module
(README / STATUS); any PR that touches the prompt, wait, cancel,
`challenge_requested`, or the sshd recipe updates §2 and keeps the evidence
tags honest.

> Origin: Patrick craft draft (2026-10-21). State below is **ezkey-pam 2.2.19**
> (#1016 cycle 2b decision harness / mutation re-kill on cycle 2; #995 connector
> default `challenge_requested=true`, #982 tiers 0–1 / #976 confirm-before-create
> + enrollment challenge).
> Evidence tags: **[code]** verified in source, **[test]** automated assert,
> **[isa]** Isabelle on ezkey-pam-test, **[inf]** OpenSSH/PAM inference.
> Paths relative to the repo root. `pam.c` = `ezkey-pam/src/pam_ezkey.c`.

### What changed in 2.2.19 (#1016 cycle 2b)

- `ezkey_decision.h` + `test-decision` kill ACCEPTED/REJECTED/HTTP/URL mutations;
  TLS verify pin; conf unknown-key warn; `#` in values; pinned 25/768/65536.

### What changed in 2.2.18 (#1016 cycle 2)

- Refuse when `SSLKEYLOGFILE` set (S1); wipe conf line buffer (S2); URL with
  `@` logs `scheme://-` (S3); LDLIBS after objects + DT_NEEDED (S4); RELRO /
  FORTIFY `_chk` + 1022-char conf message (S5).

### What changed in 2.2.17 (#1016 cycle 1b)

- HTTP body ≤ 64 KiB + `CURLOPT_MAXFILESIZE` (C1); `CURLOPT_PROXY ""` (C2);
  overlong conf → UNAVAIL (C3); safe JSON number→long (C4); SIGTERM-only note
  (C5); hardening flags + readelf (C6); orphan-without-id TTL note (C7);
  debug URL scheme://host only (C8).

### What changed in 2.2.16 (#1016)

- SIGNAL snapshot double-buffer (N5); portable artifact dir (N6); `test-diag`
  external `timeout` (P-a); `fcntl O_NONBLOCK` fallback (P-b); wait-entry
  real helper (T1); CI Rocky live `/dev/log` required (T2).
- Docs: SUCCESS-after-client-gone caveat (D1); SIGTERM disposition wording
  (D2); observability ENV isolation (D3); host H1/H2/H4 + H3 note only.

### What changed in 2.2.15 (#1011)

- Authpriv attempt lines gain `stage` / `reason` / `curl_rc` / `http` /
  `confirm_ms` / `api_ms`. Empty pre-create `ok` → `result=CONFIRM`.
- **SIGTERM outside `/wait`:** best-effort `result=SIGNAL` (async-signal-safe
  `/dev/log` write), restore `SIG_DFL`, `raise(SIGTERM)` — no hung `[pam]` at
  the `ok` or challenge prompt. During `/wait` only the abort flag is set
  (cancel path unchanged).
- **D1 caveat:** do not claim “never SUCCESS after client gone”. If TERM is
  absorbed during `/wait` after ACCEPTED already arrived in the same chunk,
  PAM can return SUCCESS to an sshd whose client has left — not an elevation.
- Unknown timings print `-` (`latency_ms=-`, `confirm_ms=-`), never a false `0`.
- Execution probes: `scripts/test-signal-probes.sh` [test] (client kill /
  SIGTERM at prompt, create, wait).

### Decision — challenge default (#995)

**Decision by Alex, confirmed by Marc on 2026-10-22:** the PAM connector
requests the challenge code by default. Reason: the challenge code is the
**safer default for SSH logins**. The **connector owns the default** — no
Admin UI / Integration API notion of a “PAM integration type”, no server-side
default, no migration. Refs [#995](https://github.com/mgagp/ezkey/issues/995).

ezkey-pam is **experimental and has never been released**: there is no product
release note and no rollback procedure for this default flip.

**Precedence (which level wins):**

1. **Enrollment wins on the server.** When the **enrollment** has
   `authAttemptChallengeRequired` (`Enrollment`), the server includes a
   challenge in the create response whatever the connector sent in
   `challengeRequested`. Server rule: enrollment requires it **OR** the
   request asks (`AuthAttemptService`). PAM always shows a valid 0..99 code
   from the response (#975) [code][test].
2. **Connector `challenge_requested=true`** (compiled-in + sample conf +
   Docker entrypoint default since 2.2.14) **asks** for a code when the
   enrollment does not require one. Missing/out-of-range → fail-closed UNAVAIL.
3. **Connector `false`** only means **“not requested by the connector”** —
   it does not forbid a server-imposed code. Opt-out path for labs that want
   approve-only SSH (S0 in the catalogue).

**No minimum server version** for this OR: enrollment-required-or-request has
existed since `e5711d4d` (2025-06-28). Any current Integration API that
implements that rule is enough.

**`ok` prompt (unchanged):** the pre-create `ok` confirm stays as is. It is an
**anti-accident filter for batch clients** (empty / EOF / `ssh -T </dev/null`
→ zero creates), **not a security defense**. It keeps its **25 s** Enter cap
(`EZKEY_MAX_ENTER_SECS`). The **challenge-code prompt** timeout stays as in
#982 (rem auth-budget only — no separate 25 s cap). Device approval (and the
challenge code when present) is the factor.

**Case C (future):** an operator-chosen “require a code” path beyond host conf /
enrollment flags is **not covered today** — tracked as
[#997](https://github.com/mgagp/ezkey/issues/997).

## 0. The model in one paragraph

One SSH login under `Match … MaxAuthTries 1` gives one `pam_sm_authenticate`
call in sshd's PAM child. That call (2.2.15):

1. Shows a **pre-create** `PAM_PROMPT_ECHO_ON` confirm
   ("SSH login for user… / Type ok … to request device approval") and
   requires trimmed case-insensitive **`ok`** (echo visible — #982 tier 0 /
   M1: no “Approve on your phone” before the request exists).
   Empty / EOF / wrong token → authpriv `result=CONFIRM` `reason=empty_confirm`
   (`PAM_AUTHINFO_UNAVAIL`) with **zero** Integration API creates; conversation
   error → `UNAVAIL`; sshd SIGTERM mid-prompt / during create → best-effort
   `result=SIGNAL` then child exit (#1011) [code][test]
   (`scripts/test-signal-probes.sh`). Batch clients leave no phone noise. The
   `ok` token is an anti-accident filter, not a security defense (#995).
2. Only then `POST /auth-attempts` with `userIdentifier=<linux user>`,
   `challengeRequested=<conf>` (default **true** since #995),
   `ttlSeconds=<planned>`.
3. If the create response carries a valid `authAttemptChallenge` (0..99) —
   host requested **or** enrollment-required (#975; server wins) — shows a
   **second** `PAM_PROMPT_ECHO_ON` with the digits and **"Approve on your
   phone, then press Enter"** (no second `Type ok` — #982 tier 1). After Enter
   the module polls `/wait` until ACCEPTED / REJECTED / TTL. Host
   `challenge_requested` but missing/out-of-range code → UNAVAIL + cancel.
   Digits never go to authpriv (#750). Connector-opt-out + no enrollment code:
   skip this prompt; go straight to wait.
4. Polls `GET …/wait` in 5 s chunks, SIGTERM handler armed only here (#726 / #941).
5. Maps the result: `ACCEPTED` → `PAM_SUCCESS`; `EXPIRED` / `REJECTED` /
   `INVALID` / unknown → `PAM_AUTH_ERR`; transport/config/conversation →
   `PAM_AUTHINFO_UNAVAIL` or `PAM_SERVICE_ERR`. Best-effort `cancel` on any
   non-ACCEPTED exit **after** create.

The single budget is 105 s from the start of authenticate, Enter included
(`EZKEY_AUTH_BUDGET_SECS`). Pre-create Enter is capped at 25 s
(`EZKEY_MAX_ENTER_SECS`); the challenge Enter keeps only the remaining
auth-budget bound (Patrick B1 / #982 — late Enter must not cancel an
already-approved request). The server-side TTL starts at create (≤ 90 s).
The device decides; PAM never sees the challenge answer typed on the phone.

**Where the challenge comes from.** The server generates a code if the
**enrollment** has `authAttemptChallengeRequired` **or** the request's
`challengeRequested` is true (`AuthAttemptService`; OR since `e5711d4d`,
2025-06-28 — no PAM minimum-server gate). Decided **once at create**. Default
length is 2 digits (`ezkey.core.auth-attempt.challenge-digits`); PAM accepts
0..99 only. PAM's connector default is `challengeRequested=true` (#995);
enrollment-required still overrides a connector `false`.

**Key fact most people miss.** The keyboard-interactive prompt is rendered by
the **ssh client on its own controlling terminal (`/dev/tty`)**, not on the
remote PTY. `-T` / `-t` do **not** decide visibility. Without a usable tty /
console / askpass, OpenSSH answers with an **empty string** (Isabelle D4
[isa]). Confirm-before-create turns that into UNAVAIL **before** create.

## 1. SSH parameter inventory

### 1.1 Client side

| Parameter / situation | Effect on keyboard-interactive / PAM conversation | Consequence for ezkey-pam | Tag |
| --- | --- | --- | --- |
| `-T` (no remote PTY) in a real terminal | None on auth. The prompt still goes to the local `/dev/tty`. | Normal: prompt visible, Enter, device decides. | [inf] |
| `-T </dev/null`, pipes, `cmd \| ssh` | stdin is not used for kbd-int answers. `/dev/tty` is used if one exists. If the process has **no** controlling tty (cron, CI, Git Bash/mintty without a pty, IDE runners), the answer is `""` + `PAM_SUCCESS`. | **2.2.12:** `""` ≠ `ok` on the **pre-create** confirm → UNAVAIL with **zero** creates (no phone noise). Pre-2.2.12: create then empty accepted → device approval could open a shell (D4). | [isa] D3/D4; [code][test] |
| `-t` / `-tt` | Remote PTY only; no effect on auth. `-tt` is the documented workaround for D1 (Windows). | — | [inf], #976 README |
| `BatchMode=yes` | Disables interactive auth methods (kbd-int and password) on the client side. | No PAM call, no attempt created, immediate denial. | [isa] D2 |
| `PreferredAuthentications` | Order and choice of methods. Without `keyboard-interactive`, PAM is never reached. | With `AuthenticationMethods keyboard-interactive`, login fails without an attempt. | [inf] |
| `NumberOfPasswordPrompts=N` | The client retries kbd-int up to N times **if the server allows it** (`MaxAuthTries`). | Each round is a new `pam_sm_authenticate` and a **new create** (F9, #907). Bounded by `Match … MaxAuthTries 1`. | [test] `test-f9-retry.sh` |
| `KbdInteractiveDevices` | Selects PAM sub-devices; irrelevant with `UsePAM yes` default. | — | [inf] |
| `ControlMaster` / `ControlPath` / `ControlPersist` | New `ssh`, `scp`, `sftp` and `git` sessions **reuse the master connection: no authentication at all**. | MFA happens once per master. For `ControlPersist` duration (can be hours), every new session skips Ezkey. The server cannot see this. `MaxSessions` only bounds concurrency. | [inf] |
| `SSH_ASKPASS` + `SSH_ASKPASS_REQUIRE=force` (or `DISPLAY` set, no tty) | The askpass program answers prompts, so a script can answer `ok`. | Defeats #976's "human gesture" in automation. Device approval is still required, so this is not a bypass of the factor. | [inf] |
| No TTY / non-interactive launcher (H1) | Pre-create `ok` needs a real conversation. | `UNAVAIL`/`CONFIRM` with **zero** API calls — nothing appears on the device. Prefer `ssh -tt`. | [isa] H1 #1016; [code] |
| Windows OpenSSH (Win32-OpenSSH) without a console (ConPTY-less, service, some IDEs) | The prompt read blocks forever, with no EOF. | **Windows D1:** hangs until sshd `LoginGraceTime` (120 s). **2.2.12 confirm-before-create:** hang is on the pre-create prompt → **no** attempt created. Prefer `ssh -tt`. | [isa] Windows D1; [code] |
| PuTTY / plink | plink prompts on the console. `plink -batch` refuses interactive prompts. Pageant does not affect kbd-int. | Same as OpenSSH: interactive works, `-batch` behaves like BatchMode. | [inf] |
| `scp` / `sftp` / `rsync -e ssh` | Spawn `ssh`, so the same rules apply. Interactive works; cron/CI behaves like the "no tty" row. Usually combined with ControlMaster. | Each cron run against an MFA account **creates an attempt (device push)** before failing. | [inf] |
| Remote command (`ssh host cmd`) | No effect on auth. | — | [inf] |
| Agent forwarding (`-A`) | No effect on kbd-int. Exposes the agent after login. | Out of scope for Ezkey; the usual SSH hygiene. | [inf] |
| `ConnectTimeout` | Covers TCP connect + banner, not the auth wait. | The 75–105 s device wait is not limited by it. | [inf] |
| `ServerAliveInterval` | **Do not use during authentication (H2).** Client keepalives have been observed to cut the MFA wait on real-host dogfood. | Leave unset (or `0`) until the session is established; post-auth keepalives are separate. | [isa] H2 #1016 |
| Ctrl-C at the prompt | The client closes the connection. sshd's monitor ends the PAM child: SIGTERM, or a conversation error. | **Pre-create prompt:** nothing created. **Challenge (2nd) prompt:** create already done → cancel if conversation error returns; else attempt until TTL (STATUS #956 M3). | [code], [inf] |

### 1.2 Server side

| Parameter | Effect | Consequence | Tag |
| --- | --- | --- | --- |
| `UsePAM yes` | Required: kbd-int goes through PAM. | With `no`, the module is never called. | [inf] |
| `KbdInteractiveAuthentication yes` (`ChallengeResponseAuthentication` is the old alias) | Enables the method. | Required. Demo: `ezkey-pam/sshd_config:12-13`. | [code] |
| `PasswordAuthentication` | With `UsePAM yes`, the password method also runs the PAM `auth` stack but **cannot display prompts** beyond the password. | If allowed for MFA users, pam_ezkey runs with a non-interactive conversation, so it fails or gives an unexpected UX. Keep `no` in the MFA Match. | [inf] |
| `AuthenticationMethods keyboard-interactive` | Ezkey is the **only** factor; the username alone triggers a push. | Push-fatigue surface (§3 U1). Demo default (`sshd_config:15`, `:36`). | [code] |
| `AuthenticationMethods publickey,keyboard-interactive` | Key first, then Ezkey. Only a key holder can trigger a push. | Needs `MaxAuthTries` ≥ 2, because a refused key consumes a try. Out of scope today (README:325-331). | [code] doc |
| `MaxAuthTries 1` inside `Match Group ezkey-mfa` | One kbd-int round per TCP connection, so one create. | Mandatory (#934). Global `1` breaks pubkey users; the shared awk warns about it (`scripts/sshd-warn-global-maxauthtries-1.awk`). | [test] f9-retry, install warn test |
| `Match Group ezkey-mfa` / `Match User` | Scopes the above. **Does not scope the PAM stack.** | Non-MFA users who reach PAM `auth` (password/kbd-int) also run pam_ezkey (§3 U6). | [code] (no group filter in `pam.c`) |
| `LoginGraceTime` (≥ 120) | Hard cap on the whole pre-auth phase. | Must exceed budget 105 + slack (README:283). It is also the **only** bound on a hung client (D1). | [code] doc, [isa] |
| `ForceCommand`, `PermitTTY no` | Post-auth; no effect on prompts (prompts are client-side). | `PermitTTY no` does **not** prevent the prompt. | [inf] |
| `ClientAliveInterval` | Post-auth keepalive. | Not relevant to the wait. | [inf] |
| `MaxStartups`, `PerSourcePenalties` | Limits on concurrent unauthenticated connections and penalties per source. | The only server-side throttle on **push spam**: each connection creates one attempt. The demo sets `PerSourcePenalties no` (`sshd_config:28`). | [code] |
| `MaxSessions` | Bounds concurrent sessions on a multiplexed connection. | Does not stop ControlPersist reuse. | [inf] |
| PAM stack: `auth required pam_ezkey.so` | Failure ends auth. | Demo: `ezkey-pam/sshd:4`. | [code] |
| PAM `sufficient`, `optional` or `[default=ignore]` | UNAVAIL/ERR is ignored and the next modules decide. | **Fail-open** if a later module succeeds (e.g. `pam_permit`, or the password module in `password-auth`). Documented (README:242-243, STATUS:91-93). | [code] doc |
| Module position "top of /etc/pam.d/sshd" (`install.sh:32`) | Runs before `password-auth`. | With kbd-int on a RHEL host, `pam_unix` will **also** prompt for a password after Ezkey. That gives 2 factors, but it is undocumented UX (§3 U7). | [inf] |
| Module args | `debug`, `conf=` (`ezkey-pam/sshd:4`). Settings come from the conf file (`config/*`): `wait_timeout`, `api_timeout`, `challenge_requested` (default **true**, #995), `lab_http`, `context_*`. `wait_polling` is legacy (chunked wait). | `challenge_requested` only **asks**; enrollment can still impose a code (§ Decision / §2). | [code] |

## 2. Outcome matrix

Columns:
- **Msg**: waiting line visible before the verdict.
- **Code**: challenge digits visible.
- **Appr / Deny / Exp**: result of device approve, device deny, TTL expiry.
- **FC**: fail-closed holds.
- **Orphan**: attempt left pending, and for how long.

Rows assume `Match … MaxAuthTries 1` and an interactive client unless stated.

### 2.1 Challenge source × module version

| Conf `challenge_requested` | Enrollment `authAttemptChallengeRequired` | Server code? | 2.2.13 | **2.2.14 (#995 default true)** |
| --- | --- | --- | --- | --- |
| false (opt-out) | false | none | Pre-create `ok` → create → wait | Same; S0 opt-out path. Empty → **0 creates** [test] |
| **true (default)** | false | yes (request) | Pre-create `ok` → code → Enter → poll | Same; now the **default** demo/host path [test] S1-H |
| false | **true** | yes (enrollment) | Code on 2nd prompt; Enter → poll | Same — **server wins** even when connector opts out [test] stub |
| true | true | yes | `ok` → code → Enter → poll | Same |
| **true** | any | code missing or outside 0..99 | UNAVAIL (`ezkey_prewait_prompt_mode` −1) | Same fail-closed [test] |
| **false** | any | code outside 0..99 (e.g. `challenge-digits` ≥ 3) | Skip post-create prompt; device INVALID/EXPIRED | Same: **not** UNAVAIL — silent deny after INVALID/expiry [code] |

### 2.2 Toggling the enrollment flag (Marc's test)

| When toggled | Effect | Tag |
| --- | --- | --- |
| Between two logins | The next create follows the new flag (OR with the conf). | [code] `AuthAttemptService:449-451` |
| Off → **on** after create, before `/respond` | The attempt has **no** code, but pending/respond now say "required". The device asks for a code; respond compares it to `null` and marks the attempt INVALID (`AuthAttemptRespondService`). PAM maps INVALID → DENIED (`PAM_AUTH_ERR`). FC ✔, confusing UX. | [code] |
| On → **off** after create | The attempt keeps its code, and `respond` still requires it (`… \|\| authAttempt.getAuthAttemptChallenge() != null`). Safe. On main, if the conf is false, the code was never shown → INVALID or expiry. | [code] |
| Deny with a challenge | Validation is skipped on reject, so REJECTED (`AuthAttemptRespondService:425-431`). | [code] |

### 2.3 Client situations (conf false, enrollment false unless noted)

| Situation | Msg | Appr | Deny | Exp | FC | Creates / orphan | Tag |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Interactive, type `ok` | ✔ | SUCCESS | DENIED | EXPIRED ~TTL | ✔ | exactly 1 create; cancel on non-ACCEPTED | [isa] A/B, [test] |
| No tty (`-T </dev/null`, cron) | ✘ | n/a (UNAVAIL at pre-create) | — | — | ✔ | **0 creates** | [code][test] D4 |
| Bare Enter / wrong token | ✔ | n/a UNAVAIL | — | — | ✔ | **0 creates** | [test] |
| Windows OpenSSH, no console | ✘ (hangs) | n/a | — | grace cut 120 s | ✔ | **0 creates** (hang before create) | [isa] D1; [code] |
| BatchMode=yes | — | — | — | — | ✔ | none (no PAM) | [isa] D2 |
| Pre-create `ok` after > 25 s | ✔ | n/a | — | — | ✔ UNAVAIL | **0 creates** | [test] |
| Challenge Enter after > 88 s (rem ≤ CURL_SLACK+MIN_WAIT) | ✔ | n/a | — | — | ✔ UNAVAIL | 1 create + `cancel=ok`; if already ACCEPTED → cancel HTTP **409** / `cancel=final`, login still refused | [test] #982 B3 |
| Challenge Enter @30 s (already approved) | ✔ | SUCCESS | — | — | ✔ | 1; `cancel=skipped` | [test] #982 B1 |
| Late pre-create `ok` (< 25 s) | ✔ | wait **and** create `ttlSeconds` shortened (computed after confirm) | | | ✔ | 1 | [test] |
| Ctrl-C at pre-create prompt | ✔ | — | — | — | ✔ | **0 creates** | [code][test] M3 |
| Ctrl-C at challenge / during `/wait` | ✔ | — | — | — | ✔ | cancel if conv error / SIGTERM in wait; else TTL | [code], STATUS M3 |
| Client disconnect during `/wait` | ✔ | — | — | — | ✔ | cancel within the 5 s chunk | [test] #908 |
| ControlMaster reuse | — | no auth | — | — | n/a | none | [inf] |
| pubkey-only user (ec2-user) | — | — | — | — | n/a | none, no PAM call | [isa] E |
| No PAM conversation at all | ✘ | n/a | — | — | ✔ UNAVAIL | 0 creates | [test] `test_no_conv_client` |
| Missing `Match … MaxAuthTries 1` + `NumberOfPasswordPrompts=3` | ✔ ×N | the first approved wins | | | ✔ | **N creates** (F9) | [test] f9-retry case A |

## 3. Uncovered or under-covered cases (severity order, fail-open first)

| # | Case | Severity | Recommendation | #976 covers? |
| --- | --- | --- | --- | --- |
| U1 | **Push fatigue / username-only initiation.** With `AuthenticationMethods keyboard-interactive` (sshd default in the demo) anyone who can reach sshd and knows an MFA username can push approval requests after typing `ok`. Default `challenge_requested=true` (#995) reduces mistaken-tap risk when the device requires the code; opting conf out to `false` restores the approve-only path. Demo `PerSourcePenalties no`. There is no create rate limit per enrollment in PAM (the server-side create limit is not verified here). | **High** (human fail-open when challenge opted out). → Christophe | Keep challenge default on. Recommend `publickey,keyboard-interactive` for internet-facing hosts. Verify the server create rate limit per enrollment. Keep `PerSourcePenalties` on outside the lab. | No |
| U2 | **D4: device approval with no visible prompt** (no-tty client). | High → fix | Require explicit `ok` **before** create. | **Yes** (2.2.12) |
| U3 | **ControlMaster/ControlPersist** reuse skips MFA for every later session. | Medium (by design of SSH) | Document. Optionally recommend `MaxSessions 1` for MFA accounts (limits concurrency, not reuse) and "one approval = one master lifetime". | Doc |
| U4 | **Pushes from non-interactive clients.** | Medium (noise) | **Confirm-before-create** (done): empty/batch fail with **zero** creates, challenge or not. | **Yes** (2.2.12) |
| U5 | **D1 Windows hang**. | Medium | Document `ssh -tt`. Confirm-before-create removes the orphan (hang before create). | Doc + 0-create |
| U6 | **The PAM stack is not scoped by `Match`.** Any user who reaches PAM `auth` (password or kbd-int) runs pam_ezkey: unenrolled users get UNAVAIL, so they are locked out, or their login is attempted against Ezkey. | Medium (availability; stack ordering mistakes can lead to fail-open) | Document a host recipe: MFA users via Match + `AuthenticationMethods`, others pubkey-only. If someone adds `pam_succeed_if user notingroup ezkey-mfa`, it must be `[success=1 default=ignore]`-style **skip of pam_ezkey only**, never `sufficient` for the whole stack. | No |
| U7 | **Host stack order**: "top of /etc/pam.d/sshd" followed by `password-auth`, so a password prompt **also** appears after Ezkey. Wait budget plus password typing must still fit within `LoginGraceTime`. | Low (UX) | Document the exact RHEL/AL2023 stack in `sshd.host-sketch`. | No |
| U8 | **Server `challenge-digits` ≥ 3:** host `challenge_requested=true` → UNAVAIL (fail-closed). Host `false` → waiting line, code never shown → INVALID/EXPIRED on the device (silent deny). | Low | Prefer `challenge-digits=2` for PAM, or extend PAM to 1–6 digits. | Doc |
| U9 | **Enrollment toggle off→on mid-attempt** gives INVALID with a confusing device UX. | Low | Snapshot "challenge required" on the attempt at create, and stop re-reading the enrollment in pending/respond (`AuthAttemptPendingService:266-269`, `AuthAttemptRespondService:434-437`). Or document it. | No |
| U10 | **SSH_ASKPASS automation** can answer `ok` (#976). | Low (the factor still holds) | Document: `ok` proves a conversation, not a human. | Partly (out of its stated scope) |
| U11 | **Case/normalisation of `userIdentifier`**: the Linux username is sent verbatim, and the case-sensitivity of the Ezkey lookup was not checked. | Low [inf] | Verify and document. | No |
| U12 | Name truncation at 127 chars (display only) and username sanitisation in the prompt. | Nit | Accept. | n/a |

## 4. Discoverability

This file is linked from:

- `ezkey-pam/README.md` (Runbook + config table row `challenge_requested`);
- `ezkey-pam/STATUS.md` (waiting / challenge / known limits);
- `ezkey-pam/sshd.host-sketch` (header comment);
- `install.sh` end message;
- Admin UI enrollment "challenge required" help (`create.challengeRequiredHelp`).

**Maintenance rule:** any PR that touches the prompt, wait, cancel,
`challenge_requested`, or the sshd recipe updates §2 and keeps the evidence
tags honest.

---

## 5. Scenario catalogue and decision matrix

Purpose: replace "effect of each setting" with **one decision per plausible scenario**, ranked so that fixes, documented limits and QA effort go where they matter.

### 5.1 Axes

| Axis | Values |
| --- | --- |
| **A. Client interactivity** | **I** = interactive human with a terminal (Linux/macOS terminal, Windows Terminal/ConPTY, PuTTY). **B** = batch: `BatchMode=yes`, no controlling tty, cron, CI, `scp`/`rsync`/ansible from automation. **S** = semi: Windows OpenSSH without a console, IDE remote runners, multiplexed sessions (ControlMaster). |
| **B. Challenge required** | no / yes |
| **C. Requirement source** | **H** = host conf `challenge_requested=true`, known **before** create. **E** = enrollment flag set in the Admin UI, known by PAM **only from the create response** (`authAttemptChallenge` present). Server rule: H OR E (`AuthAttemptService.java:449-451`). |
| **D. Toggle** | none / enrollment flag changed during an attempt |

**What "the phone is notified" means here.** Ezkey's device side is **pull**: the Demo Device and the mobile app claim pending attempts when the user taps "Check" (mobile FSM pull model; Demo Device `/auth` check; TB-2026-10-20 C2). A created attempt therefore does not ring the phone, but it **is visible** to anyone who checks within the TTL (≤ 90 s). It also counts in the audit trail and dashboards. "Phone noise" below means a visible, actionable request that no human at the SSH side asked for.

### 5.2 Feasibility facts (they constrain the decisions)

| Question | Answer | Evidence |
| --- | --- | --- |
| Can PAM detect "no human / no terminal"? | **Not reliably.** With OpenSSH and no client tty, the conversation **succeeds** with an empty reply, which cannot be told apart from a bare Enter. | Isabelle D3/D4 [isa]; #976 PR body; `read_passphrase` behaviour [inf] |
| `pam_get_item(PAM_TTY)`? | Useless. sshd sets `PAM_TTY` to the constant `"ssh"` for every connection before auth; the client's terminal is never visible to the server. | OpenSSH `auth-pam.c` [inf]; not used in `pam_ezkey.c` [code] |
| `isatty()` server-side? | Meaningless. The PAM child has no link to the client terminal. | [inf] |
| BatchMode? | Never reaches PAM: the client does not offer kbd-int. | Isabelle D2 [isa] |
| Hung client (Windows no console)? | The conversation never returns, so PAM cannot act. Only `LoginGraceTime` ends it; SIGTERM during the pre-create prompt leaves **0 creates** (confirm-before-create). | Isabelle D1 [isa]; [code] |
| **What PAM *can* do** | Require a **non-empty explicit token** (`ok`), which rejects every client that auto-answers empty. Do it **before create**, so that rejected clients leave no attempt. | **Done in 2.2.12** [code][test] |
| Integration API cancel with a typed reason? | **No.** `POST /api/v1/auth-attempts/{id}/cancel` has no body and sets status `EXPIRED`; there is no `CANCELLED` status and no reason field. | Integration API OpenAPI [code] |
| Typed reason on the PAM side | Authpriv summary `result=… cancel=ok\|fail\|skipped\|final` plus debug.err ("pre-wait confirm missing or not ok"). | [code] |

**Confirm-before-create (2.2.12 / #976) + fluidity tiers 0–1 (2.2.13 / #982).**
Prompt and require `ok` **first** (`PAM_PROMPT_ECHO_ON`), then create, then —
when a code is present — show it in a **second** `PAM_PROMPT_ECHO_ON` where
**Enter** continues to `/wait` (no second `Type ok`). Still one kbd-int round
and one create under `Match … MaxAuthTries 1`. Batch / empty / Ctrl-C at the
first prompt → **zero** attempts + typed debug error. Operator may approve on
the phone **before or after** the challenge Enter; the module polls until
ACCEPTED / REJECTED / TTL. Create `ttlSeconds` is computed from the remaining
auth budget **after** the pre-create `ok`. The 25 s Enter cap applies to the
**pre-create** prompt only; challenge Enter uses rem-budget only (Patrick B1 /
#982 — approve-vs-cancel race). **Out of scope here:** tier 2 (drop pre-create
`ok` on no-challenge path — needs Christophe + Alex) and tiers 3–4.

### 5.3 Catalogue (ranked)

Tiers: **P1** core · **P2** common variant · **P3** edge · **P4** exotic.
Tags as in §0. **2.2.13** = #982 tiers 0–1 on #976 confirm-before-create.

| ID | Tier | Scenario | Interactive | Phone noise | Expected decision | 2.2.13 | Verdict |
| --- | --- | --- | --- | --- | --- | --- | --- |
| **S0** | P1 | Opt-out: no connector challenge (`challenge_requested=false`), interactive `ssh` | I | yes (wanted) | Pre-create `ok` (ECHO_ON) → 1 create → wait | ✔ [test] | **Covered** (opt-out since #995; QA reorder by Isabelle) |
| **S1-H** | P1 | Challenge from host conf (**default** since #995) | I | yes | `ok` → create → code → **Enter** → poll → SUCCESS | ✔ [test] #982/#995 | **Covered** |
| **S1-E** | P1 | Challenge from **enrollment**, host false | I | yes | Code → Enter → poll (#975/#982) | ✔ [test] stub | **Covered** |
| **S1-before** | P1 | Approve on phone **before** challenge Enter | I | yes | SUCCESS after late Enter (no cancel) | ✔ [test] #982 B1 Enter@30s | **Covered** |
| **S1-after** | P1 | Approve on phone **after** challenge Enter | I | yes | SUCCESS after delayed `/wait` | ✔ [test] #982 delayed_accept | **Covered** |
| **S1-deny** | P1 | Deny while polling after challenge Enter | I | yes | DENIED + cancel | ✔ [test] #982 | **Covered** |
| **S1-timeout** | P1 | PAM wait budget exhausted while polling | I | yes | TIMEOUT + cancel | ✔ [test] #982 M4 | **Covered** |
| **S1-expired** | P1 | Server EXPIRED while polling after challenge Enter | I | yes | EXPIRED + cancel | ✔ [test] #982 M4 | **Covered** |
| **S2** | P2 | Batch / no tty, no challenge | B | **none** | UNAVAIL; **0 creates** | ✔ [test] D4 | **Covered** |
| **S3** | P2 | Batch + enrollment challenge (Marc) | B | **none** | UNAVAIL; **0 creates** (never learns the code) | ✔ [test] | **Covered** |
| **S4** | P2 | `BatchMode=yes` | B | no | Immediate denial, no PAM | ✔ [isa] D2 | **Covered** |
| **S5** | P2 | Pubkey-only non-MFA (ec2-user) | I/B | no | No PAM call | ✔ [isa] E | **Covered** |
| **S6** | P2 | Ctrl-C at prompt / during wait | I | pre-create: no; after create: until cancel/TTL | Pre-create Ctrl-C: 0 creates [test]; wait kill-client: cancel=ok ≤5s when SIGTERM delivered, else best-effort NOTE (OpenSSH 9.9) [test] f9 B2b; wait-timeout cancel=ok [test-timeouts] | ✔ | **Covered** |
| **S7** | P2 | Pre-create `ok` after > 25 s | I | **none** (0 creates) | UNAVAIL | ✔ [test] | **Covered** |
| **S7b** | P2 | Challenge Enter after 30 s (already approved) | I | yes (wanted) | SUCCESS; no cancel | ✔ [test] #982 B1 | **Covered** |
| **S7c** | P2 | Challenge Enter after > 88 s (rem exhausted) | I | yes (until cancel) | UNAVAIL + cancel=ok; if already ACCEPTED → cancel=final 409, login refused | ✔ [test] #982 B3 | **Covered** |
| **S8** | P3 | Windows OpenSSH no console | S | **none** | Hang ≤ grace; **0 creates**; use `ssh -tt` | Doc + 0-create | **Doc** |
| **S9** | P3 | ControlMaster reuse | S | no | MFA once per master | [inf] | **Doc** |
| **S10** | P3 | Match forgotten | I | yes ×N | N creates; install warn | [test] f9 A | **Doc + warn** |
| **S11** | P3 | Enrollment toggle off→on mid-attempt | I | yes | INVALID | [code] | **Doc** |
| **S12** | P3 | Toggle on→off mid-attempt | I | yes | Attempt keeps its code | ✔ | **Covered** |
| **S13** | P3 | Username-only push spam | B | yes if they type `ok` | Throttle / pubkey+kbd-int | posture | **Doc + Christophe** |
| — cutoff — | | | | | | | | | |
| S14 | P4 | Server `challenge-digits` ≥ 3 | I | yes | Show any 1–6 digit code | host true → UNAVAIL; host false → waiting line then INVALID/EXPIRED (code never shown) | silent deny when host false | **Doc** (prefer digits=2) |
| S15 | P4 | Non-MFA users reaching PAM `auth` (password/kbd-int allowed outside Match) | I | yes (unenrolled) | Not reach pam_ezkey | runs pam_ezkey (no group filter) [code] | same | host recipe | **Doc** (stack recipe; never `sufficient`) |
| S16 | P4 | `SSH_ASKPASS` automation answering `ok` | B | yes | Device approval still required | n/a | `ok` accepted | none for the factor | **Doc** |
| S17 | P4 | Username case/normalisation mismatch | I | — | Consistent lookup | not verified [inf] | same | unknown | **Doc after check** |
| S18 | P4 | Host stack places pam_ezkey before `password-auth` (double prompt) | I | yes | Clear UX | [inf] | same | UX | **Doc** |

### 5.4 Cutoff and why

| Tier | Rule | Reason |
| --- | --- | --- |
| **P1** | Must work; fix and **live retest** on ezkey-pam-test before calling the feature done. | Every login goes through S0/S1. A regression here is a product failure. |
| **P2** | Must **fail closed with no unwanted attempt where feasible**; fix + automated test + live retest. | Batch clients against MFA accounts are common (scp, ansible, cron). S2/S3 produce the only contract violation observed (D4) and the only phone noise a user meets daily. |
| **P3** | **Documented** in INTERACTION-MODEL + README known limits, with **one** test (automated or QA) each. Code fix only if cheap or if it falls out of a P2 fix. | Plausible but the cause is config, platform or policy. Confirm-before-create fixes S8 for free. |
| **P4** | Documented only. | Diminishing returns: each needs a deliberate odd setup; all fail closed. |

**Confirm-before-create is shipped in 2.2.12** (§5.2 / #976). It closes S2/S3
phone noise, S6 (pre-create Ctrl-C), and S8 orphans with **no** Integration API
change. A typed cancel reason on the API would need a new field; PAM debug.err
already carries the "why" for operators.

### 5.5 QA cases for Isabelle (P1–P3, QA Sphere format)

Common preconditions: ezkey-pam-test VM with 2.2.12+ (#976 or later); `Match Group ezkey-mfa` with `MaxAuthTries 1`, `AuthenticationMethods keyboard-interactive`; user `testuser` in `ezkey-mfa`, enrolled on the Demo Device; `ec2-user` pubkey-only; observe `/var/log/secure` (`pam_ezkey: attempt` line) and the device "Check".

| ID | Scenario | Preconditions | Steps | Expected |
| --- | --- | --- | --- | --- |
| PAM-S0-01 | S0 approve (opt-out) | host `challenge_requested=false` (opt-out of default true); enrollment challenge off | 1. `ssh testuser@host`. 2. Read pre-create prompt. 3. Type `ok`. 4. Approve on the device. | "SSH login… request device approval" **before** create; shell; `result=SUCCESS`; **exactly 1** create; no challenge prompt |
| PAM-S0-02 | S0 deny | same | steps 1-3, then Deny | Access denied; `result=DENIED`; 1 attempt |
| PAM-S0-03 | S0 expire | same | steps 1-3, no device action | Denied at ≈ applied TTL; `result=EXPIRED`; 1 attempt |
| PAM-S0-04 | S0 wrong confirm | same | step 1, Enter (empty) or type `x` | `CONFIRM` `reason=empty_confirm`; **no attempt created** |
| PAM-S1H-01 | S1-H code (default) | host `challenge_requested=true` (sample/entrypoint default since #995) | `ssh`, `ok` (create), read code on 2nd prompt, Enter, enter code on device, Approve | Code visible; SUCCESS; code **not** in `/var/log/secure` |
| PAM-S1H-02 | S1-H wrong code | same | wrong code on device | `result=DENIED` (INVALID); 1 attempt |
| PAM-S1E-01 | S1-E enrollment challenge | host **false**; enrollment challenge **on** | as S1H-01 | Code visible (#975); SUCCESS |
| PAM-S1E-02 | S1-E deny | same | Deny without code | `result=DENIED` (REJECTED) |
| PAM-S2-01 | S2 batch no tty | host false | `ssh -T testuser@host whoami </dev/null` | Denied; `CONFIRM`; **0 creates**; approval must not open a shell |
| PAM-S2-02 | S2 scp from cron | same | `scp file testuser@host:` from cron | Fails; no PENDING attempt |
| PAM-S3-01 | S3 Marc's case | host false; enrollment challenge on | as S2-01 | Denied; **0 creates**; code never shown |
| PAM-S4-01 | S4 BatchMode | any | `ssh -o BatchMode=yes testuser@host` | Immediate denial; **no** attempt |
| PAM-S5-01 | S5 non-MFA | — | `ssh -i key ec2-user@host` | OK; no pam_ezkey line |
| PAM-S6-01 | S6 Ctrl-C at pre-create | host false | `ssh`, Ctrl-C at first prompt | Disconnect; **0 creates** |
| PAM-S6-02 | S6 disconnect during wait | host false | `ok`, then kill client during wait | `cancel=ok` within ~5 s |
| PAM-S7-01 | S7 slow pre-create | host false | wait 30 s before first `ok` | UNAVAIL; **0 creates** |
| PAM-S8-01 | S8 Windows no console | Windows OpenSSH no console | `ssh testuser@host` | Hang ≤ `LoginGraceTime`; **no attempt** |
| PAM-S8-02 | S8 workaround | Windows | `ssh -tt testuser@host` | Prompt visible; S0 behaviour |
| PAM-S9-01 | S9 multiplexing | `ControlMaster` / `ControlPersist` | log in, then second `ssh`/`scp` within persist | Second session **without** Ezkey |
| PAM-S10-01 | S10 Match forgotten | no `MaxAuthTries 1`; prompts=3 | `ssh`, deny 3 times | ≥ 2 creates; install warn |
| PAM-S11-01 | S11 toggle on mid-attempt | host false; enrollment off | `ok`; enable challenge in Admin UI; approve | Denied (INVALID) |
| PAM-S12-01 | S12 toggle off mid-attempt | enrollment on | see code, `ok`; disable challenge; approve **with** code | SUCCESS (attempt keeps code) |
| PAM-S13-01 | S13 spam posture | `PerSourcePenalties` on | 10 rapid `ssh` without `ok` | sshd penalises; **0 creates** until someone types `ok` |

### 5.6 Host dogfood observations (#1016 — not product bugs)

| ID | Observation | Action |
| --- | --- | --- |
| H1 | Pre-create `ok` needs a real TTY; non-interactive launcher → UNAVAIL, **zero** API calls | Documented in README runbook + §1.1 |
| H2 | Client `ServerAliveInterval` during auth can cut the approval wait | Documented: no client keepalive during authentication |
| H3 | ~30 s between attempt create and card on the simulated device (both tries, AL2023 2.2.15) | **Note only** — cause not established; pointer [#982](https://github.com/mgagp/ezkey/issues/982). Do not guess. |
| H4 | Challenge digits appear only on the SSH terminal; `.env` → conf key mapping | Documented in README runtime configuration + `.env.example` |
