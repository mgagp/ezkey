# EZK-LAB-2026-002 — Jenkins phase 2 notes

Raw material for a later lab paper. Not published. Source journal:
`ezkey-jenkins/LAB-JOURNAL.md`. Issue [#1184](https://github.com/mgagp/ezkey/issues/1184).
Phase 1 close-out is PR #974 / issue #972.

## Posture

Ezkey stays the only factor. Phase 1 did that on purpose: the device signature
is the factor, not a step after a Jenkins password. With the challenge required,
the phone will not enable Approve until the two digits shown in the browser are
typed. That is number matching. Prompt spam does not become account takeover.

A second-factor overlay for LDAP, AD or SSO is a phase 3 candidate. It was not
built.

## What the first review got wrong

The open sign-in page was called a push-bombing hole, by analogy with the PAM
lab (#992). That overstated it. What was actually weak: any typed name could
trigger a push, the challenge defaulted to off, Sign in did not lead to Ezkey,
and the realm was still named Mock.

## What phase 2 changed

- Realm renamed to `EzkeySecurityRealm`. No plugin compatibility alias.
- `getLoginUrl()` is `ezkey-login`, the same hook SAML and GitHub OAuth use.
  Break-glass stays on core `/login`.
- A push is sent only for a user on the realm's enabled list. Unknown, not
  enabled, and cooled-down sends all render the same wait page, including a
  two-digit decoy, and do not call Integration.
- `forceChallenge` defaults to true. The sign-in checkbox (#998) can only add
  a code, and it is hidden while the realm already requires one.
- One push per user per 30 seconds, in memory. After the window, a resend
  cancels the previous attempt.
- Config is a Jenkins form and JCasC (`ezkey`).

## Evidence

JenkinsRule: `./ezkey-jenkins/scripts/ci-plugin-test.sh`, 51 tests, 0 failures.
That includes the matrix in #1184 (enabled vs unknown, cooldown, missing
challenge, approve rotates the session, reject / cancel / expire, break-glass
against a dead port, secret not logged, form round-trip, JCasC).

Live, after clean-start, on enrollment 2 (`labuser`, challenge required):

- approve, code `60` typed on Demo Device → `ACCEPTED`, Jenkins session
- deny, code `36` → `REJECTED`, no session
- wrong code, displayed `59`, typed `00` → `INVALID`, no session

A deny started inside the 30 second cooldown showed a code and created no
attempt. That was the decoy, confirmed because Demo Device had nothing to answer.

## Residuals (reasoned, not executed)

- A timing difference between a real create and a decoy can still hint that a
  username is enabled. Not padded.
- An Ezkey user when Integration is unreachable gets the error page and no
  session (`doSend` does not call `establishLogin`). The executed "Ezkey down"
  test is break-glass, not that catch.
- Break-glass success is logged at WARNING. The test checks the login, not the
  log line.

## Method friction (for a later autonomy pass)

- The brief named PR 964. That PR is a merged Acme change. The Jenkins lab is
  #974 / #972. Open the PR, do not trust the number in the prompt.
- Git Bash rewrites container paths (`/src`, `/var/jenkins_home/...`) unless
  `MSYS_NO_PATHCONV=1` is set on that one Docker command and the host mount is
  a Windows path. CI on Linux never sees this.
- Windows `python3` decodes files as cp1252 unless `PYTHONUTF8=1`.
- `ConfigurationAsCode.configure(String)` is a file path, not YAML text.
- Renaming a Jenkins realm class bricks an existing lab volume at boot
  (`CannotResolveClassException`). The install script rewrites `config.xml`.
  There is no plugin alias.
- `ci-gate` waits on every required job, including a 50 minute iOS XCTest that
  this change does not touch. A green `ezkey-jenkins` job is not a green gate.
