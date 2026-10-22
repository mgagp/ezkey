# ezkey-jenkins design notes (lab)

Experimental (#972). Folder-local only. Independent of other host-login labs.

## Ezkey interaction model

Jenkins sits on the **integrating-app** side of Ezkey, not the device side.

| Surface | Who talks | Role |
|---------|-----------|------|
| **Authentication API ↔ phone (device)** | Mobile / Demo Device | Enrollment + approve/deny; crypto payloads; challenge display on the device |
| **Integration API ↔ SDK** | Jenkins plugin via `org.ezkey:ezkey-sdk` | M2M: create / wait / cancel auth attempts with integration key + secret |

M3/M4 evaluate **SDK + Integration API** fit only. Auth API / device crypto / Dart helpers are out of scope for this plugin.

## Prior art (M2) — SecurityRealm / 2FA

| Reference | Model | Fit for Ezkey |
|-----------|--------|---------------|
| **OpenMFA** | Second factor *after* primary login (TOTP); exemptions for service accounts | Closest UX to “password then device”; exemption ≈ break-glass. Not a full SecurityRealm replace. |
| **miniOrange 2FA** | Overlay 2FA on existing realm; commercial TOTP/etc. | Same overlay idea; heavy / vendor. |
| **totp-login-plugin** (qc2105) | Custom **SecurityRealm** with TOTP | Shows realm-replace is viable for lab. |

**Lab choice:** custom `EzkeyMockSecurityRealm` (extends `AbstractPasswordBasedSecurityRealm`) — break-glass local password on the standard Jenkins login form; Ezkey MFA via `/ezkey-lab-login/` (confirm → create → challenge → wait).

## Break-glass (anti-lockout)

A local admin path that does **not** depend on Ezkey / Integration API being up.

- Username default `admin` (override `EZKEY_JENKINS_ADMIN_USER`)
- Password default `ezkey-lab-admin` (override `EZKEY_JENKINS_ADMIN_PASSWORD`) — lab only
- Interim boot: `HudsonPrivateSecurityRealm` via init groovy
- After plugin configure: same credentials on `EzkeyMockSecurityRealm`

## Secret handling — `hudson.util.Secret` vs Credentials plugin

**Current (M2–M5):** integration secret stored as `hudson.util.Secret` on the realm (Jenkins instance encryption). **Never logged** (no integration secret, no challenge codes).

**Credentials plugin (`StringCredentials`) — deferred (M5 confirmed not low-risk):** the lab skips the setup wizard, so credentials + plain-credentials + transitive detached plugins are not present. Pulling that fan-out solely to relocate one secret is accidental complexity. Remains deferred until a product need forces Credentials install for other reasons.

**When migration would make sense:** if the lab (or a future product plugin) already installs the Credentials plugin for other reasons, or operators need shared secret rotation / ACL-scoped credentials across plugins — then move the Integration secret to `StringCredentials` and reference it by ID from the realm. Until then, keep `hudson.util.Secret`.

## M3 — Java SDK fit (decision)

**Decision (one line):** Java SDK (`org.ezkey:ezkey-sdk`) is **adequate as-is** for Jenkins login over the Integration API; the plugin adds only a thin UX/session adapter (confirm → create → display challenge → wait), not a new SDK.

### Evidence

| Need for Jenkins login (Integration API) | SDK surface | Verdict |
|------------------------------------------|-------------|---------|
| Create attempt by Jenkins username | `createAuthAttemptByUserIdentifier(user, challengeRequested[, context])` | Covered |
| Optional challenge on create | `AuthAttemptCreateResponse.authAttemptChallenge()` | Covered |
| Wait for approve/deny | `waitForAuthAttempt(id, …)` + `isReadTimeout()` | Covered |
| Cancel on abandon / fail-closed | `cancelAuthAttempt(id)` | Covered |
| HTTP Basic integration key + secret | `EzkeyClient.builder().integrationKey().secretKey().baseUrl()` | Covered |
| Zero extra Maven deps in `.hpi` | Zero compile-scope dependencies; Java 17 | Fits Jenkins plugin packaging (bundled in `.hpi`) |
| Dogfood | `ezkey-demo-app-acme` uses the same Integration client | Proven in-repo |

**Not needed:** Admin API client, Auth API / device crypto, or OpenAPI codegen.

## Login flow (M4/M5) — Integration API consumer

Implemented: `EzkeyLabLogin` (`/ezkey-lab-login/`) + `EzkeyLabLoginSupport` + `EzkeyClient`.

1. **Break-glass** → standard `/login` local password only.
2. **Ezkey path** (`/ezkey-lab-login/`):
   1. Form: username + POST button **Envoyer la demande Ezkey** (Jenkins CSRF crumb; `@RequirePOST`).
   2. On send → `createAuthAttemptByUserIdentifier(user, realm.forceChallenge)`.
   3. If create returns `authAttemptChallenge` → **display** on the wait page (never log it).
   4. If challenge missing and `forceChallenge=true` → **fail closed** + best-effort `cancelAuthAttempt`.
   5. Poll `waitForAuthAttempt`; Cancel button → cancel; ACCEPTED → Jenkins session; REJECTED/EXPIRED → deny.
3. Jenkins compose joins `ezkey_ezkey-network` as an **external** network only (DNS to
   `integration-api:7080`). No shared volumes; still a separate compose project. Fallback:
   `host.docker.internal:7080` when the external network is unavailable.

## Challenge matrix (A / B / C / D)

Server generates a code when `enrollment.authAttemptChallengeRequired || request.challengeRequested`.
Realm field `forceChallenge` (default **false**, `@DataBoundSetter`) maps to Integration
`challengeRequested`. Configure via UI or `EZKEY_JENKINS_FORCE_CHALLENGE` in
`configure-mock-auth.sh`.

| Case | Enrollment requires code | Realm `forceChallenge` | Result |
|------|--------------------------|------------------------|--------|
| **A** | no | false | No code; wait page has no challenge block |
| **B** | yes | false | Code returned → **display** |
| **C** | — | — | **Out of scope** — no per-login opt-in; not tested |
| **D** | no (or ignored) | true | Code requested; missing code → **fail closed** |

Display rule: show the code whenever `authAttemptChallenge != null`. Reject only when
`forceChallenge=true` and the code is missing. Lab tests cover **A / B / D** only.

**Wait / poll thresholds** (single source in `EzkeyLabLoginSupport`): wait clamp 1..30 s,
Integration poll interval 2 s, per-poll wait 8 s, wait-page JS 1500 ms (2000 ms on
transport error). Session `timeoutSeconds` sets a deadline; 401 and 5xx from `poll` are
terminal (result `error`), not infinite retries.

**Default seed (matrix B):** `m5-provision-lab-integration.sh` creates `labuser` with
`authAttemptChallengeRequired=true`. For matrix A at provision time:

```bash
EZKEY_JENKINS_ENROLLMENT_CHALLENGE_REQUIRED=false ./scripts/m5-provision-lab-integration.sh
```

(Re-runs PATCH an existing VERIFIED lab enrollment to match.) Alternate live A without
re-provision: `./scripts/m5-live-no-challenge.sh`.

## M5 — dedicated lab seed + login UI states

**Dedicated integration + enrollment (not system enrollment #1):**

| Piece | Lab default | Notes |
|-------|-------------|--------|
| Integration | code `jenkins-lab` | Created via Admin API; reused on re-run |
| Enrollment | name `Jenkins Lab labuser` | `userIdentifier=labuser`, challenge required |
| Demo Device bind/verify | `scripts/lib/demo-device-enroll.sh` | Uses create challenge + proof token; never logs them |
| API key | description `jenkins-lab-m5` | Written to gitignored `.ezkey/m5-integration.env` |
| Admin token bootstrap | system enrollment `#1` / `admin.docker` | Token only — Jenkins MFA does **not** patch enrollment `#1` |

Setup: `./scripts/m5-provision-lab-integration.sh` then `source .ezkey/m5-integration.env` and
`./scripts/configure-mock-auth.sh`. Live: `./scripts/m4-live-demo.sh approve|deny` (reads
`EZKEY_JENKINS_ENROLLMENT_ID` from the env file).

**`/ezkey-lab-login/` states (M5):** confirm → challenge + waiting → approved | denied | expired |
cancelled (plus error). Each page exposes `data-ezkey-lab-state` / `data-testid="ezkey-lab-state"`.

**Credentials:** still deferred (see above) — not low-risk for this lab.

**Boundaries:** no Admin UI changes; integration behaviour documented only in this folder
(`DESIGN.md` / `README.md` / `TUTORIAL.md`). Break-glass unchanged.

## Milestone map

| M | Focus |
|---|--------|
| M0 | Jenkins LTS + `m0-ls` |
| M1 | Hello-world `.hpi` |
| M2 | SecurityRealm + break-glass + prior-art |
| M3 | SDK / Integration API fit decision |
| M4 | Live Integration round-trip + confirm/challenge UI |
| **M5** | **Dedicated lab integration/enrollment + UI states; Credentials deferred** |
