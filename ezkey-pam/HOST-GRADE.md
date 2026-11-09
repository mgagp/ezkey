# Ezkey PAM — host-grade contract

**Experimental.** This file is the only copy of the host-grade contract (#1200).
[`STATUS.md`](STATUS.md) points here. Do not copy this text into `docs/` or
`docker/README.md`.

Host-grade means: two real factors, a fail-closed module, a tested break-glass
path, and an install that can be removed. It stops before signing, packages,
and a real-host cutover. The module is not a second PAM framework.

Proof today: `./scripts/host-grade-accept.sh` from `ezkey-pam/` (Docker, no
manual step). The same script runs inside the existing Rocky Linux 10 and
Amazon Linux 2023 images. It is not wired into the `ezkey-pam` CI job. A
pass count from it is local Docker evidence, not a CI result. Apply, remove,
and rollback are
[`scripts/host-apply.sh`](scripts/host-apply.sh). That script ships in the
source tree. `make install` copies the module and the sample conf; it does
not copy the script. The runner calls it. `install.sh` still only warns, and
it does not edit `sshd_config`.

Not proved here: a real host, Lightsail (#1201 stays blocked until this
contract is merged), macOS, Windows, sudo, SELinux, reboot, journald, or the
community instance (#955).

## Fail-open / fail-closed

| Boundary | Posture |
|---|---|
| Integration API down, HTTP 404, expiration, deny, bad conf mode, TLS verify failure | **Fail-closed.** No SSH session. |
| PAM control flag on `pam_ezkey` | **Fail-closed only if** the auth control field is `required` or `requisite` and the line has no leading `-`. A leading `-` on the `pam_ezkey` line is rejected, because PAM skips an unloadable module and continues. The suite parses auth lines with comments removed, ignoring case. A leading `-` on a different module is still that type. It rejects `optional`, `sufficient`, `required` hidden in module arguments, and a line before `pam_ezkey` that is `sufficient`, `-auth sufficient`, `[default=ignore]`, any `done` action, any jump `=N` with N≥1 including leading zeros (`[success=1]`, `[success=01]`, `[success=2]`, `[default=007]`, `[default=done]`, `[success=ok default=1]`), a backslash continuation before or on the `pam_ezkey` line, `include`, `substack`, `@include`, or the same words in uppercase. |
| Break-glass | **Separate sshd**, not a fail-open flag in the module. No `failmode=safe`. |
| Rollback with no confirmation | **Fail-closed for the edit** when `host-apply.sh check` runs after the window. Previous files return. A system timer that would call `check` is not installed. |
| Authpriv logging | Must not grant a session. A missing line fails the observability check. |
| Global `MaxAuthTries 1` | **Warn, do not write.** `install.sh` and this suite's apply leave the file unchanged. |

## The contract

1. **Two factors on group `ezkey-mfa`.** An SSH key and an Ezkey approval are
   both required. Key alone in batch mode: no session. A keyboard-interactive
   conversation without a key: no session. Key plus approve: session. Key plus
   deny: no session and a single `POST /api/v1/auth-attempts`. A wrong key
   offered first, then the right key: Ezkey is reached once. A global
   `MaxAuthTries 1` is warned and not written.
2. **Fail-closed.** API down, HTTP 404, expiration, and deny each yield zero
   sessions, and each of those tries reaches the module (a new `attempt` line,
   and a create when the stub is up). The fixture's `pam_ezkey` auth control
   is `required` or `requisite`.
3. **Break-glass.** A second `sshd` listens on another port with `UsePAM no`,
   public-key only, and an admin account. Removing `pam_ezkey.so` or replacing
   it with a broken file kills sessions for members of `ezkey-mfa` on the MFA
   port. The admin port still accepts the admin key. An `ezkey-mfa` member
   is refused there. The suite unlocks that member's shadow first, because a
   locked account is refused before `AllowUsers`, then authpriv records
   `not listed in AllowUsers`. A user outside the group
   still gets a key-only session on that same MFA port; the suite fails if
   they do not. `UsePAM no` rejects a locked shadow (`!` or `!!`) before the
   key is read. The fixture gives that account a valid random password so
   the key is considered. `PasswordAuthentication no` is what blocks password
   login. The #1201 runbook needs that pair: an unlocked hash, and password
   authentication left off. A locked break-glass account is refused.
4. **Group scope.** A user outside `ezkey-mfa` gets a session from the key
   alone and creates no auth attempt. A member creates an attempt. **`Match`
   does not unload PAM for the whole daemon.** The module line stays in
   `/etc/pam.d/sshd` for every connection; non-members avoid Ezkey only because
   their `AuthenticationMethods` never enter that auth stack.
5. **Install / remove.** `host-apply.sh apply` is idempotent. It writes
   `sshd_config`, `/etc/pam.d/sshd`, and the admin config to a temp file in
   that directory, copies the existing mode and owner (`644 root:root` when
   the file is new), then renames it over the destination. Those three are
   refused, with no write, when the file or its directory is not root-owned
   or is group- or world-writable. An existing `640 root:wheel` file stays
   `640 root:wheel`. The conf and `snapshot/pam_ezkey.conf` are always
   `0600` `root:root`, including when the live file was `0644` or absent on
   a later apply. Other snapshot copies are not more permissive than the
   originals. A symlinked target is refused. `EZKEY_PAM_SSHD` must be a
   root-owned executable that is not writable by group or other. An existing
   state directory that is not `root:root` mode `700` is refused. A missing
   parent is created root-only. A `.hg` temp is removed on exit. `restorecon`
   runs on the destination when the command exists.
   `sshd -t` must pass on the MFA config and the
   admin config before apply returns 0. If any step fails inside the script,
   the snapshot is restored byte for byte. An apply killed midway removes its
   temp and does not restore the snapshot. Files already renamed stay as
   written. Recover with `host-apply.sh remove`, or with `check` after `arm`.
   `remove` restores `sshd_config` and `/etc/pam.d/sshd` from the snapshot,
   and restores the conf as `0600` `root:root` even when the original file
   was `0644`. That tightening is intended. It runs `sshd -t` on that restored
   config, and a second remove stays there. If `snapshot/READY` already
   exists, apply skips the snapshot and does not re-check modes left by an
   earlier version of this script. A CR or LF in any environment value is
   rejected and apply
   exits non-zero without writing. Group and user must match
   `[A-Za-z_][A-Za-z0-9_-]{0,31}`. A name that contains a dot is refused.
   Ports must be decimal 1–65535 with no
   leading zero. The API URL must match `^https://` with no space or tab.
   The conf, sshd, pam.d, admin config, and state directory must be absolute
   paths. Each snapshot copy is checked; a missing conf does not write the
   snapshot ready mark.
6. **Rollback function.** `host-apply.sh arm`, `confirm`, and `check`.
   Default window **15 minutes** (`EZKEY_PAM_ROLLBACK_MINUTES`).
   `EZKEY_PAM_ROLLBACK_SECONDS`, when set, must be a positive integer and
   overrides for the lab. `check` before the
   deadline does not change files. With no confirmation, `check` restores
   the previous files, `sshd -t` passes on that restored config, and the
   rollback record is closed. A later apply is not undone by `check` unless
   `arm` is called again. Confirmation keeps the new files. What is proved
   is that tool. A system timer that calls `check` on its own is not
   installed and is not proved; that trigger stays with phase 2 (#1201).
   No systemd unit.
7. **Files.** Root-owned. Apply forces the conf to mode `0600` and
   `root:root`. The module accepts only `0600` or `0400` with that owner, so
   a preserved `0644` would publish the keys and then refuse every member.
   The conf directory and the module
   directory are not writable by the SSH user. Conf mode `0666` returns
   `UNAVAIL` and no session.
8. **Logs.** One `pam_ezkey: start` and one `pam_ezkey: attempt` per try that
   passes the conf gate. Authpriv contains no `secret_key`, no integration
   key, and no challenge digits.
9. **Non-lab URL.** The fixture URL is `https://` and a CA is installed in
   the image trust store. Peer verify stays on. The conf sets `lab_http`
   twice (`true`, then ` FALSE` with spaces). The parser keeps the last key,
   trims spaces, and accepts that value as false. The suite reads
   `lab_http=false` on the approve `attempt` line. A literal `lab_http=false`
   row is not the proof. The self-signed unit case is not weakened: a
   self-signed server still yields `UNAVAIL` and zero HTTP requests
   (`scripts/test-conf-security.sh`, `test/test_decision_harness.c`, and this
   suite).

Early conf rejection (mode `0666`) still logs one `attempt` with `UNAVAIL`
and `user=-`, and does not log `start`. The Linux user is only in the sshd
log, not on that line. `start` is emitted only after the conf and URL gates
(`pam_ezkey.c`). Line 8 is proved on tries that pass those gates. Moving
`start` earlier would touch every early-fail log; that is a separate ticket,
not this change.

The `EXIT` trap in the inside run stops the stub and sshd and puts
`pam_ezkey.so` back if a break-glass case replaced it. It does not restore
`sshd_config`, `/etc/pam.d/sshd`, or `pam_ezkey.conf`. That is acceptable
because `--inside` refuses to run outside a disposable container.

The keyboard-interactive-only sketch in [`sshd.host-sketch`](sshd.host-sketch)
still requires `Match` `MaxAuthTries 1` (#907). This fixture is two-factor
(`publickey,keyboard-interactive`) and uses a higher `Match` `MaxAuthTries` so
one rejected key does not consume the only try. The suite, not that sketch,
proves the single create. Do not drop `MaxAuthTries 1` on a
keyboard-interactive-only host because this fixture exists.

## What the suite runs

`./scripts/host-grade-accept.sh` builds the Rocky builder, the Rocky runtime,
the AL2023 builder, and the AL2023 runtime, then runs this same script with
`--inside` in each runtime. The `ezkey-pam` CI job does not run it. Inside,
it sources `host-grade-fixtures.sh` and
`host-grade-scenarios.sh`, calls `host-apply.sh`, and talks to
`scripts/integration-api-stub.py` (approve, deny, expire, API down, HTTP 404).
Artifacts (no hostname, no address):

- `ezkey-pam/runtime/artifacts/host-grade-builders.txt`
- `ezkey-pam/runtime/artifacts/host-grade-rocky.txt`
- `ezkey-pam/runtime/artifacts/host-grade-al2023.txt`

Override the directory with `EZKEY_PAM_ARTIFACT_DIR`.

Break-glass keys (the suite fails unless the value is `session`):

- `line3_plain_on_mfa_when_module_broken`
- `line3_plain_on_mfa_when_module_removed`

On both Docker images those keys are `session`. A user outside `ezkey-mfa`
still gets a key-only session on the MFA port after the module file is broken
or removed. Their `AuthenticationMethods` never enter the auth stack, so a
dead module does not lock them out. The admin port still accepts the admin key.

## Rootbeer path that works

Standard clean-start, then `./scripts/provision.sh` and `./scripts/up.sh`.
The demo user is `testuser` on port 2222 (keyboard-interactive only, not this
two-factor fixture).

Runbook: `authAttemptChallengeRequired=false` on the enrollment does not
disable a challenge the PAM connector requested (`challenge_requested=true`).
The server ORs the two flags. `provision.sh` leaves the enrollment flag false;
the demo conf still requests the code.

`./scripts/demo-ssh.sh` reads `Ezkey challenge code:` from the SSH prompt and
calls `./scripts/respond-pending.sh approve` with those digits. That is the
path that opens a session. `approve-pending.sh` without the digits posts an
approval the device rejects. A deny is `./scripts/respond-pending.sh reject`
with the same digits: no session, one create.

The `authAttemptId` on `pam_ezkey: attempt` in the container
`/var/log/secure` is the id to find in the Admin audit
(`AUTH_ATTEMPT_CREATED`, then the approved respond). The stub has no audit log.

## Linux-only notes for a Windows host

This campaign does not add Windows support.

- `ssh-pty-capture.py` needs Python's `pty` module. Windows Python does not
  provide it. Drive the SSH steps from WSL or from Linux.
- Under Git Bash, `./scripts/up.sh` needs `MSYS_NO_PATHCONV=1` so MSYS does
  not rewrite the paths passed to Docker.
