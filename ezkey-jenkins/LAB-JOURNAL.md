# ezkey-jenkins lab journal

Working notes for the Jenkins lab. Not a lab paper. Raw material for a later
EZK-LAB-2026-002 and for a pass on agent autonomy. Entries are written as the
work happens.

## 2026-11-03 — Posture, before any phase 2 code

**Decision.** Ezkey stays the only factor (passwordless). Marc's phase 1 intent
was deliberate: device crypto is the factor, not a step after a password.
Second-factor mode for LDAP, AD or SSO is a phase 3 candidate, not this slice.

**What we got wrong.** The first review called the open sign-in page a
push-bombing hole, by analogy with PAM (#992). That overstated it. On the phone,
Approve stays disabled until the two digits shown in the browser are typed
(`PendingAuthScreen.tsx`). With the challenge mandatory, prompt spam cannot
become account takeover. It is number matching, which `SECURITY_POSTURE.md`
already cites (CISA). What was actually weak: any typed name could trigger a
push, the challenge defaulted to off, Sign in did not lead to Ezkey, and the
realm was still named Mock.

**Evidence.** Code read of `PendingAuthScreen.tsx` and `SECURITY_POSTURE.md`.
Reasoned, not re-executed, at the time of the decision. Phase 2's `wrong-code`
live script is the execution of the claim.

**Issue.** [#1184](https://github.com/mgagp/ezkey/issues/1184). Phase 1 close-out
is PR #974.

## 2026-11-03 — M6 design

**Decision.** Realm-owned enabled-user list, not a Jenkins `UserProperty`.

**Why.** This realm is the user source. A user property needs the Jenkins user
to exist before the first passwordless login, which has no password to create
it with. SAML and GitHub OAuth realms own the sign-in URL via `getLoginUrl()`;
we do the same (`ezkey-login`). Core `/login` stays for break-glass.

**Decision.** Unknown user, disabled user and cooldown all render the wait page,
including a two-digit decoy code, and do not call Integration.

**Why.** A different page, or a page with no code, would reveal which usernames
are enabled. Same-session resend inside 30 s keeps the current attempt so the
person waiting is not cancelled. After 30 s, resend cancels the previous attempt
(the phase 1 rule, still tested).

**Decision.** #998 checkbox stays, and can only add a challenge. It is hidden
while `forceChallenge` is true (the new default).

**Residual, reasoned.** A timing difference between a real create and a decoy
response can still hint that a username exists. Not padded. Named in `DESIGN.md`.

## 2026-11-03 — JenkinsRule matrix (executed)

`./ezkey-jenkins/scripts/ci-plugin-test.sh` — BUILD SUCCESS, 51 tests, 0 failures.
That run includes the `Messages.properties` display name (InjectedTest 12, was 11)
and `cancelledPollDoesNotCreateASession`.

Covered by that run, not by reasoning:

- Enabled user gets a push and a challenge; unknown user gets the same wait page and no second create.
- Cooldown blocks a second push. Same-session resend inside 30 s does not cancel. After 30 s, resend cancels.
- Missing challenge denies and cancels. Approve rotates the session id (phase 1 guard, still green).
- REJECTED, CANCELLED and EXPIRED do not create a session.
- Non-break-glass password login is refused. Break-glass works against a dead Integration port.
- Form submit and JCasC both keep the user list, the secret and `forceChallenge=true`.
- The secret string is absent from realm, login and support log records.

**Wrong turn.** `ConfigurationAsCode.configure(String)` is a file path. Passing YAML text failed with "isn't a valid path". The test writes a temp file.

**Reasoned, not executed.** Operator `doCancel` never calls `establishLogin` (same outcome as a CANCELLED poll, which is executed). A timing gap between a real create and a decoy can still hint that a username is enabled.

## Method friction

- The plan named PR 964. That PR is a merged Acme demo-app change. The Jenkins
  lab is PR 974 / issue 972. A cold agent that trusts the number in the prompt
  without opening the PR will review the wrong change. The issue title and the
  `component:jenkins` label are the reliable handles.
- `ci-plugin-test.sh` is documented as the local gate, but Git Bash rewrites
  Docker's `-w /src` into `C:/Program Files/Git/src` unless `MSYS_NO_PATHCONV`
  is set on that one command and the host mount is a Windows path. CI (Linux)
  never sees this. The script now sets it. Worth a line in the Jenkins lab
  README or in the shell-preferences rule, because the failure looks like a
  Docker install problem.
- Phase 1's PR was conflicting and its body described an intermediate review
  batch, not the milestone state. Closing it before phase 2 kept the next PR
  reviewable. Waiting on `ci-gate` for a 20-commit lab PR is the long pole.
