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

## 2026-11-03 — Live run on Rootbeer (executed)

Clean-start (`ezkey-tests/clean-start.sh`, exit 0). Stack came up healthy.
`m5-provision-lab-integration.sh` created integration `jenkins-lab` id 2 and
VERIFIED enrollment 2 for `labuser` with the challenge required. Demo Device
approved the admin login and bound that enrollment. `configure-mock-auth.sh`
applied `EzkeySecurityRealm` with `forceChallenge=true`. Break-glass basic auth
and `/ezkey-login/` both returned 200.

`m6-live-passwordless.sh`, enrollment 2, after the 30 s cooldown between runs:

- approve, displayed `60`, Demo Device typed `60` → poll `ACCEPTED`, Jenkins session for `labuser`
- deny, displayed `36` → poll `REJECTED`, no Jenkins session
- wrong-code, displayed `59`, typed `00` → poll `INVALID`, no Jenkins session

**Surprise, executed.** A deny started inside the 30 s cooldown after approve
showed a two-digit code and Demo Device had no pending attempt. That is the
decoy. The script comment now says to wait between runs. The cooldown is not
logged as a different page, which is the point.

**Surprise.** Renaming the realm with no plugin alias made the existing lab
volume unbootable: `CannotResolveClassException: EzkeyMockSecurityRealm` in
`config.xml`, exit 5, restart loop. `configure-mock-auth.sh` rewrites that
class name before restart. That is a volume migration, not a compatibility
alias in the plugin.

**Surprise.** `build-auth-plugin.sh` re-ran the suite while clean-start was
loading Docker. One guard test (`waitPageJsStopsOn502AndShowsError`) hit a
socket timeout. The same suite had just passed 51/51 via `ci-plugin-test.sh`.
The `.hpi` used for the live run was packaged with `-DskipTests` from that tree.

## Security self-check (quality criteria)

- No Jenkins session without `ACCEPTED`, except break-glass. Executed: approve
  creates a session; REJECTED, CANCELLED, EXPIRED, INVALID and a missing
  challenge do not. Break-glass against a dead port is a JenkinsRule test.
- No push for an unknown or non-enabled user. Executed in JenkinsRule (same
  wait page, no extra create). Live unknown-user was not repeated.
- Challenge required by default, fail-closed when missing. Executed in
  JenkinsRule. Live enrollment required a code, and a mistyped code was `INVALID`.
- Integration down for an Ezkey user returns the error page and does not call
  `establishLogin`. Reasoned from `doSend`'s `EzkeyException` catch. The
  executed down-path test is break-glass, not that catch.
- Secret not in log records. Executed (`secretIsNotLogged`). Live scripts print
  the displayed challenge (the operator types it) and do not print the
  integration secret.
- Session id rotated on approve. Executed (`acceptedPollRotatesSessionId`).
- Sign in URL, form round-trip, JCasC. Executed.
- Break-glass success is logged at WARNING. Reasoned from `authenticate2`;
  the test checks the login, not the log level.
- Timing side channel of decoy vs real create. Reasoned, accepted, named in
  `DESIGN.md`. Not padded.

## 2026-11-03 — Phase 1 merged

PR #974 squash-merged as `ce289aab`. `ci-gate` went green after a rerun of
`mobile-ios / ios-xctest`. The first attempt was cancelled near the 50 minute
job timeout. `ezkey-jenkins` and `quality-gate` were already green.

Phase 2 was rebased onto that squash with `git rebase --onto origin/main cbd6d6666`.
A plain `git rebase origin/main` tried to replay all 21 phase-1 commits, because
a squash merge does not make those commits ancestors of `main`.

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
  On this run `ci-gate` failed because `mobile-ios / ios-xctest` was cancelled
  near its 50 minute timeout, after `ezkey-jenkins` and `quality-gate` had
  passed. The Jenkins change was not the red check.
- Git Bash path conversion is not only Docker's `-w /src`. `docker exec` of
  `sed` on `/var/jenkins_home/config.xml` was rewritten to
  `C:/Program Files/Git/var/jenkins_home/config.xml`. `MSYS_NO_PATHCONV=1` on
  that one command is required. Same class of bug as the plugin test script.
- `python3` on this workstation is Windows CPython. It decodes files as
  cp1252 unless `PYTHONUTF8=1`. Jenkins HTML is not cp1252. The live scripts
  set that variable. A cold agent that only tests the scripts on Linux will
  not see it.
- `build-auth-plugin.sh` always runs `clean test package`. Under a concurrent
  clean-start that retest is not a useful signal. The definition-of-done
  gate is `ci-plugin-test.sh`, which does not produce the `.hpi`.
