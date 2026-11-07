---
name: pam-host-grade-macos
overview: Bring ezkey-pam to a credible host-grade level, with a Mac-native SSH integration, stopping just before signing and distribution, and use the exercise to surface signals that harden the platform.
todos:
  - id: f0-macos
    content: "#1198 — F0: feasibility and native design on a disposable macOS VM."
    status: in_progress
  - id: host-grade-contract
    content: "#1200 — Host-grade contract and acceptance suite, on Docker Linux first."
    status: pending
  - id: c-portability
    content: "#1199 — Portable C and macOS CI."
    status: pending
  - id: mac-ssh
    content: "#1202 — Mac-native SSH integration at the host-grade level."
    status: pending
  - id: lightsail
    content: "#1201 — Acceptance suite on a disposable Lightsail instance."
    status: pending
  - id: sudo
    content: "#1203 — Evaluate the same principle for sudo."
    status: pending
isProject: false
---

# ezkey-pam — host-grade level and Mac-native integration

Tracking issue and source of truth for scope: #1112. This plan keeps what the issues should not
carry: the reasoning, the options left open and the rejected alternatives.

## Intent (Marc, 2026-11-04)

- **Mac-native, not a Linux copy.** A Mac administrator should find it expected and unsurprising.
- **Serious, but stop before signing and distribution.** The rest of the project is in alpha.
- **Signals are a goal.** Labs have repeatedly surfaced blind spots; findings that go beyond the
  module feed the platform.
- **Keep complexity balanced.** Ezkey's rule still applies: ~80% of the value for ~20% of the
  complexity, and the module never gets smarter than the Integration API.

## Is this posture specific to the Mac?

No. "Credible on a real host" is an OS-neutral property, and Linux does not have it yet:
`STATUS.md` says the module is "not a host-operable MFA agent on a real host". So the plan splits:

- **One contract**, proved by one acceptance suite with a small adapter per OS (#1200).
- **One integration per OS**, following that OS's conventions (#1202 for the Mac).
- **Proof order by cost:** Docker Linux, disposable Lightsail, macOS VM. The Mac track runs in
  parallel from F0 on; it does not wait for Lightsail.

#955 keeps its own path to the community instance. It no longer gates this work, and benefits from
the suite.

## Levels

| Level | Meaning | Where the module is |
|---|---|---|
| Lab demo | Docker, Ezkey-only stack, stubs | Today |
| **Host-grade** | Two real factors, safe install and rollback, tested on real hosts | **This plan** |
| Distributable | Signing, notarization, packages, releases | Out of scope |

## Mac-native questions for F0

Observed on the maintainer's Mac (macOS 27.2, arm64, read-only checks, 2026-11-04):

- `/usr/lib/pam/` holds Apple's modules, in `arm64e` and `arm64e.x1` slices.
- `sshd` has the hardened runtime and the `clear-library-validation` entitlement.
- `/etc/ssh/sshd_config.d/` exists and is included.
- `/etc/pam.d/sudo` includes `sudo_local`, documented as surviving system updates; `sshd` has no
  such file.
- Login window: `system.login.console` runs `authd` mechanisms, and there is no
  `/etc/pam.d/loginwindow`.

| Question | Options to weigh | Bias before evidence |
|---|---|---|
| Module location | `/usr/local/lib/pam/`, other root-owned path | Third-party convention |
| Signature level | none, ad hoc, Developer ID | Lowest level that loads; a need for Developer ID is a finding, not a scope change |
| Configuration | `/etc/security/pam_ezkey.conf` (same as Linux), Mac-style path | Decide on what an admin expects |
| Integration API secret | root-only `0600` file, System keychain | File, unless the keychain is cheap from a PAM module |
| Logs | `syslog()` through unified logging, `os_log` | Keep `syslog()` if `log show` is readable |
| Automatic rollback | `launchd` job (Linux uses cron in #955) | `launchd` |
| Scope by user | `Match Group` with a local group | Same principle as Linux |
| Update survival | Reapply, detect, documented repair | Measure first |

## Rejected alternatives

- **Linux port as is.** The Ezkey-only demo stack (`pam_permit`) and its paths are not what a Mac
  administrator would accept.
- **`ForceCommand` or `AuthorizedKeysCommand` as the primary path.** They avoid PAM but give weaker
  guarantees and leave out the module's tested interaction model. Kept as a fallback if Apple's
  `sshd` refuses the module.
- **Authorization plugin for the login window.** A different product, with a different risk of
  lockout. Out of scope.
- **Homebrew OpenSSH.** It sidesteps the system `sshd`, which is the opposite of "expected on a Mac".
- **Homebrew libraries in the module.** The module runs in a root process, and that prefix is
  generally user-writable.

## Working method

- One PR per subject, linked to its issue.
- Move forward on observations: the plan is revised when evidence contradicts it.
- Nothing on the maintainer's Mac before validation on a VM and Marc's explicit go-ahead.
- Two failed approaches on one point: stop and report.
- Findings beyond the module go to the signal table in #1112, with their own issue when warranted.
