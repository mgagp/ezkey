# Backlog Idea — `I-2026-10-13-pam-tmp-log-hardening` PAM debug logs under `/tmp` (TOCTOU / symlink)

## Metadata

- **ID:** `I-2026-10-13-pam-tmp-log-hardening`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-10-13`
- **Updated at:** `2026-10-13`
- **Last reviewed at:** `2026-10-13`
- **Component tags:** `pam`, `security`, `docs`
- **Captured by:** Marc (code-scanning triage 2026-10-13)
- **GitHub issue:** _(none yet)_
- **Decision trace:** approved by Marc 2026-10-13 19:40 (code-scanning triage plan); Lot C keeps CodeQL alerts **123** and **124** open until this hardens or Marc decides otherwise.

## Intent

Stop writing PAM debug output with world-writable `/tmp` pathnames that CodeQL flags as
symlink / TOCTOU-prone (`fopen` append), while keeping `ezkey-pam` clearly experimental and
out of the operable-release path.

## Problem and value

- **Problem:** `ezkey-pam/src/pam_ezkey.c` opens fixed paths with
  `fopen("/tmp/pam_ezkey.out", "a")` and `fopen("/tmp/pam_ezkey.err", "a")` (approx. lines
  260 and 268). On a multi-user host those paths are classic symlink races; debug builds may
  also echo wait JSON that includes `authAttemptProofToken` (see `ezkey-pam/STATUS.md`).
- **Expected value:** Safe debug logging (or syslog-only) behind an explicit debug option;
  CodeQL alerts **123** and **124** can close honestly when the C change lands.

## Scope

- **In scope:**
  - Replace `fopen("/tmp/pam_ezkey.{out,err}", "a")` with
    `open(..., O_WRONLY|O_CREAT|O_APPEND|O_NOFOLLOW|O_CLOEXEC, 0600)` under a directory **outside**
    `/tmp` (for example a PAM-owned path under `/var/log` or `/run`), **or** route debug output
    to syslog only.
  - Gate the file/syslog debug path behind an explicit debug option (not on by default in any
    non-lab build).
  - Keep experimental posture documented in root `AGENTS.md` and `ezkey-pam/README.md`.
- **Out of scope:**
  - Changing PAM auth protocol / Integration API contract.
  - Promoting `ezkey-pam` onto the operable-release or Docker demo path.
  - Dismissing CodeQL **123** / **124** without a code or explicit won't-fix decision.

## Key assumptions

- `ezkey-pam` remains a personal-lab module loaded in host root `sshd`/`login`, not a
  containerised product surface.
- Alerts **123** and **124** stay open until this idea ships or Marc records a different
  disposition.

## Risks and exceptions

- Host PAM debugging may still need a writable log location; choose ownership and mode carefully
  so only root/PAM can write.
- Syslog-only may lose convenience for the lab container (`docker exec … cat /tmp/pam_ezkey.out`);
  document the replacement path in `ezkey-pam/README.md` when implemented.

## Promotion notes

Ready for a tracer bullet once the target log location (or syslog-only) and debug flag shape are
chosen, and a minimal test plan exists (build + lab SSH approve path without `/tmp` fixed names).

## Links

- CodeQL alerts **123**, **124** (`py`/`c` code-scanning triage 2026-10-13)
- [`ezkey-pam/README.md`](../../../../ezkey-pam/README.md)
- [`ezkey-pam/STATUS.md`](../../../../ezkey-pam/STATUS.md) (debug log / proof-token notes)
- Root [`AGENTS.md`](../../../../AGENTS.md) domain pointer (experimental PAM)
