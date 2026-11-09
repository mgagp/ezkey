# Ezkey PAM — host-grade contract

**Experimental.** This file is the only copy of the host-grade contract (#1200).
[`STATUS.md`](STATUS.md) points here. Do not copy this text into `docs/` or
`docker/README.md`.

Host-grade means: two real factors, a fail-closed module, a tested break-glass
path, and an install that can be removed. It stops before signing, packages,
and a real-host cutover. The module is not a second PAM framework.

Proof today: `./scripts/host-grade-accept.sh` from `ezkey-pam/` (Docker, no
manual step). The same script runs inside the existing Rocky Linux 10 and
Amazon Linux 2023 images. It applies fixtures and removes them.
`install.sh` is not that script: it still only warns, and it does not edit
`sshd_config`.

Not proved here: a real host, Lightsail (#1201 stays blocked until this
contract is merged), macOS, Windows, sudo, SELinux, reboot, journald, or the
community instance (#955).

## Fail-open / fail-closed

| Boundary | Posture |
|---|---|
| Integration API down, HTTP 404, expiration, deny, bad conf mode, TLS verify failure | **Fail-closed.** No SSH session. |
| PAM control flag on `pam_ezkey` | **Fail-closed only if** the fixture line is `required` or `requisite`. `sufficient` and `[default=ignore]` are rejected by the suite. |
| Break-glass | **Separate sshd**, not a fail-open flag in the module. No `failmode=safe`. |
| Rollback timer with no confirmation | **Fail-closed for the edit.** Previous files return. |
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
   sessions. The fixture's `pam_ezkey` control is `required` or `requisite`.
3. **Break-glass.** A second `sshd` listens on another port with `UsePAM no`,
   public-key only, and an admin account. Removing `pam_ezkey.so` or replacing
   it with a broken file kills sessions on the MFA port. The admin port still
   accepts the admin key. `UsePAM no` rejects a locked shadow (`!`) before the
   key is read, so the fixture gives that account a non-login password hash
   and leaves `PasswordAuthentication no`. Whether Linux also refuses key-only
   users on the MFA port is **measured** (artifact keys below), not assumed.
4. **Group scope.** A user outside `ezkey-mfa` gets a session from the key
   alone and creates no auth attempt. A member creates an attempt. **`Match`
   does not unload PAM for the whole daemon.** The module line stays in
   `/etc/pam.d/sshd` for every connection; non-members avoid Ezkey only because
   their `AuthenticationMethods` never enter that auth stack.
5. **Install / remove.** Apply is idempotent. Remove restores `sshd_config`,
   `/etc/pam.d/sshd`, and `pam_ezkey.conf` byte for byte from the snapshot
   taken before apply, and a second remove stays there.
6. **Rollback function.** `hg_rollback_arm` / `hg_rollback_check` in
   `scripts/host-grade-accept.sh`. Default window **15 minutes**
   (`EZKEY_PAM_ROLLBACK_MINUTES`). `EZKEY_PAM_ROLLBACK_SECONDS` overrides for
   the lab. With no confirmation, previous files return and `sshd -t` passes.
   Confirmation keeps the new files. Function only: no systemd unit.
7. **Files.** Root-owned. Conf mode `0600`. The conf directory and the module
   directory are not writable by the SSH user. Conf mode `0666` returns
   `UNAVAIL` and no session.
8. **Logs.** One `pam_ezkey: start` and one `pam_ezkey: attempt` per try that
   passes the conf gate. Authpriv contains no `secret_key`, no integration
   key, and no challenge digits.
9. **Non-lab URL.** The fixture uses `https://` with `lab_http=false` and a CA
   installed in the image trust store. Peer verify stays on. The self-signed
   unit case is not weakened: a self-signed server still yields `UNAVAIL` and
   zero HTTP requests (`scripts/test-conf-security.sh`,
   `test/test_decision_harness.c`, and this suite).

Early conf rejection (mode `0666`) still logs `attempt` with `UNAVAIL` and
does not log `start`. `start` is emitted only after the conf and URL gates
(`pam_ezkey.c`). Line 8 is proved on tries that pass those gates. Moving
`start` earlier would touch every early-fail log; that is a separate ticket,
not this change.

The keyboard-interactive-only sketch in [`sshd.host-sketch`](sshd.host-sketch)
still requires `Match` `MaxAuthTries 1` (#907). This fixture is two-factor
(`publickey,keyboard-interactive`) and uses a higher `Match` `MaxAuthTries` so
one rejected key does not consume the only try. The suite, not that sketch,
proves the single create. Do not drop `MaxAuthTries 1` on a
keyboard-interactive-only host because this fixture exists.

## What the suite runs

`./scripts/host-grade-accept.sh` builds the Rocky builder, the Rocky runtime,
the AL2023 builder, and the AL2023 runtime, then runs this same script with
`--inside` in each runtime. Inside, it talks to
`scripts/integration-api-stub.py` (approve, deny, expire, API down, HTTP 404).
Artifacts (no hostname, no address):

- `ezkey-pam/runtime/artifacts/host-grade-builders.txt`
- `ezkey-pam/runtime/artifacts/host-grade-rocky.txt`
- `ezkey-pam/runtime/artifacts/host-grade-al2023.txt`

Override the directory with `EZKEY_PAM_ARTIFACT_DIR`.

Break-glass measurement keys (value `session` or `denied`):

- `line3_plain_on_mfa_when_module_broken`
- `line3_plain_on_mfa_when_module_removed`

On both Docker images those keys are `session`. A user outside `ezkey-mfa`
still gets a key-only session on the MFA port after the module file is broken
or removed. Their `AuthenticationMethods` never enter the auth stack, so a
dead module does not lock them out. The admin port still accepts the admin key.

## Operator notes the suite does not replace

Demo Device audit on a real Integration API is a manual check (Rootbeer). The
stub has no audit log. See the pull request for #1200.
