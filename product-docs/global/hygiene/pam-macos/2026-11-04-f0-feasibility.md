# ezkey-pam on macOS — F0 feasibility (2026-11-04)

Issue: [#1198](https://github.com/mgagp/ezkey/issues/1198). Roadmap:
[#1112](https://github.com/mgagp/ezkey/issues/1112).

## Verdict

**Continue, for SSH.** Apple's `sshd` loads and runs the module with SIP enabled, an ad hoc signature
and no logic change. Developer ID is not required to load.

**The design must change on one point: break-glass.** A broken module takes down every login that
goes through PAM, including key-only accounts. Measured on `sudo`, it locks out `sudo` itself. This
reshapes the host-grade contract (#1200), and `sudo` stays out of the SSH slice (#1203).

## Setup

- Host: maintainer's Mac, macOS 27.2 (26B123), Apple Silicon. Nothing changed in its `/etc/pam.d`
  or `sshd`.
- Guest: disposable Tart VM, `ghcr.io/cirruslabs/macos-golden-gate-vanilla:27.2`, same build, SIP
  enabled. Destroyed after the tests.
- Module: unchanged `ezkey-pam/src` built with `-D_DARWIN_C_SOURCE`, a `pam_ext.h` shim and vendored
  cJSON 1.7.18; links only `/usr/lib` (`libpam`, `libcurl`, `libSystem`).
- Integration API: **the repo stub** (`scripts/integration-api-stub.py`) through an SSH reverse
  tunnel. No real Ezkey stack or phone in this pass.
- Client flow: `AuthenticationMethods publickey,keyboard-interactive` under `Match User ezuser`.
  `admin` stayed key-only, out of the Ezkey path.

## Results

### SSH through Apple's `sshd`

| Scenario | Stub or state | Result | PAM code |
|---|---|---|---|
| Approved | `ok` | Session | 0 |
| Rejected | `rejected` | No session | 9 |
| Expired | `expired` | No session | 9 |
| Never answered | `pending` | No session | 9 |
| API down | no listener | No session | 12 |
| Wrong service answering | HTTP 404 | No session | 12 |
| Key, no interaction | `BatchMode` | No session | n/a |
| Interaction, no key | no key offered | No session | n/a |
| Challenge shown | `ok`, challenge on | Session, code displayed | 0 |
| Repeated approvals | `ok` | **10 sessions out of 10** | 0 |
| After reboot | `ok` | Session, files unchanged | 0 |

### Loading

| Module | Result |
|---|---|
| `arm64e`, ad hoc | Loads |
| `arm64`, ad hoc | Loads |
| **Unsigned** | `sshd-session` dies: **all of port 22 is down, `admin` key included** |
| **Missing file** | **All of port 22 is down, `admin` key included** |
| Owned by `admin:staff`, or group-writable | **Loads: no ownership check** |
| Relative name, `/usr/local/lib/pam/pam_ezkey.so.2` present | Loads |
| Conf mode `0666` | Module refuses, no session (fail closed) |

Each session logs two kernel lines, `Library Validation failed: Rejecting ...pam_ezkey.so`, and the
module then runs anyway. `sshd`, `sshd-session` and `sudo` hold the
`com.apple.private.security.clear-library-validation` entitlement, which likely explains it
(inferred, not proved).

### Filesystem

| Writer | `/etc/pam.d` | `/etc/ssh/sshd_config.d`, `/etc/security`, `/usr/local/lib/pam` |
|---|---|---|
| `sudo` over Apple's `sshd` (port 22) | Allowed | Allowed |
| `sudo` over a second `sshd` from `launchd` | **Operation not permitted** | Allowed |
| Root `launchd` job | **Operation not permitted** | Allowed (`sshd_config.d` measured) |

`/etc/pam.d` shows no flag in `ls -lO` and SIP stays enabled: the likely cause is TCC, with Apple's
Remote Login holding the right. Inferred, not proved. **A local Terminal was not measured.**

### Break-glass

A second `sshd` from `launchd` (port 2222, `UsePAM no`, keys only, `AllowUsers admin`) stayed up
through both port 22 outages. It could replace the module, which restored port 22.

### `sudo` through `sudo_local`

| Scenario | Result |
|---|---|
| Approved, then correct password | Success |
| Rejected | Refused, after 3 tries |
| API down | Refused, **3 Ezkey prompts in one `sudo`** |
| Syslog lines | Visible in `log stream` |
| **Module missing** | **`sudo: unable to initialize PAM`, for everyone, `NOPASSWD` included; `sudo` cannot repair it** |

### Logs

- `pam_ezkey: start` and `attempt` lines are visible from `sudo`, and **absent from `sshd-session`**
  in `log stream --level debug`, `log show` and `/var/log/system.log`. Likely cause, not proved:
  `sshd` runs PAM in a forked child, and macOS logging may not survive a fork without exec.
- The debug file under `/var/log/ezkey-pam` works.
- A standalone test binary using the same `syslog` calls logs normally.
- `ezkey_diag.c` hard-codes `/dev/log` for its signal-safe line. macOS has `/var/run/syslog` and no
  `/dev/log`, so that path should fail there (read from the code and the filesystem, not run).

### TLS

- Untrusted private CA: refused, zero request reached the server.
- `security add-trusted-cert` over SSH: `authorization was denied since no user interaction was
  possible`. So a private CA needs a local interactive admin or MDM, and the module has no `ca_info`.
- **A successful TLS login was not measured** (no publicly trusted endpoint in this setup).

### Rollback

Restoring the original `/etc/pam.d/sshd` and removing the module, conf, logs and drop-in took under
a second over port 22. The file hash matched the original, and `ezuser` logged in with a key alone.

## Consequences for the design

1. **Break-glass must not depend on PAM.** A key-only account on the same `sshd` is not break-glass.
   Candidates: a second `sshd` with `UsePAM no`, as measured, or local console access.
2. **Automatic rollback cannot rewrite `/etc/pam.d`** from `launchd`. It can remove the
   `sshd_config.d` drop-in and restart `sshd`, which is the better design anyway: the login path
   turns off in one file. The module file must stay in place while the PAM stack references it.
3. **The installer owns the checks.** Apple's OpenPAM does not check module ownership here, so
   install and a self-check must verify owner, mode, signature and presence.
4. **Build:** a plain ad hoc signature is enough, and the linker adds one on Apple Silicon. `arm64`
   and `arm64e` both load. Prefer `arm64`: it uses the standard toolchain. Stripping the signature
   is the lockout, so verify it at install.
5. **`sudo` stays separate (#1203).** A missing module locks out `sudo` entirely, and the stack calls
   Ezkey before the password check and once per retry.
6. **Logs on Mac need their own answer for `sshd`** in #1202: file, socket or another phase.

## Signals for the platform

| Signal | Scope | Goes to |
|---|---|---|
| Break-glass on the same `sshd` shares the fate of the PAM stack; **Linux not measured** | Also #955 guardrails | #1200, #955 |
| Retries multiply device prompts (3 per `sudo`) | Approval fatigue | #992, #1070, #1203 |
| `pam_code` is numeric and differs by PAM library (`12` on OpenPAM here) | Log parsers, runbooks | #1202 |
| Audit lines can vanish silently depending on the host process | Observability contract, "one attempt, one line" | #1200, #1202 |
| A private CA cannot be trusted without interaction | Self-hosted adopters | #1202, `ca_info` in `STATUS.md` |

## Not measured

- Survival after a macOS update.
- Local Terminal writes to `/etc/pam.d`.
- The real Ezkey stack and a real phone.
- TLS success, and `SSL_CERT_FILE` / `CURL_CA_BUNDLE` behaviour with Apple's `libcurl`.
- A physical Mac, and Intel Macs.
- Developer ID: whether it removes the kernel log lines.
