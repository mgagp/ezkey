# ezkey-jenkins design notes (lab)

Experimental (#972). Folder-local only. Independent of other host-login labs.

## Ezkey interaction model (M3 frame)

Jenkins sits on the **integrating-app** side of Ezkey, not the device side.

| Surface | Who talks | Role |
|---------|-----------|------|
| **Authentication API ↔ phone (device)** | Mobile / Demo Device | Enrollment + approve/deny; crypto payloads; challenge display on the device |
| **Integration API ↔ SDK** | Jenkins plugin via `org.ezkey:ezkey-sdk` | M2M: create / wait / cancel auth attempts with integration key + secret |

M3/M4 therefore evaluate **SDK + Integration API** fit only. Auth API / device crypto / Dart helpers are out of scope for this plugin.

## Prior art (M2) — SecurityRealm / 2FA

| Reference | Model | Fit for Ezkey |
|-----------|--------|---------------|
| **OpenMFA** | Second factor *after* primary login (TOTP); exemptions for service accounts | Closest UX to “password then device”; exemption ≈ break-glass. Not a full SecurityRealm replace. |
| **miniOrange 2FA** | Overlay 2FA on existing realm; commercial TOTP/etc. | Same overlay idea; heavy / vendor. |
| **totp-login-plugin** (qc2105) | Custom **SecurityRealm** with TOTP | Shows realm-replace is viable for lab. |

**Lab choice (M2):** custom `EzkeyMockSecurityRealm` (extends `AbstractPasswordBasedSecurityRealm`) — one login form, break-glass local password, everyone else mock-auto-accept. Overlay-after-password (OpenMFA style) remains available if M4 UX needs a second step after Jenkins password.

## Break-glass (anti-lockout)

A local admin path that does **not** depend on Ezkey / Integration API being up.

- Username default `admin` (override `EZKEY_JENKINS_ADMIN_USER`)
- Password default `ezkey-lab-admin` (override `EZKEY_JENKINS_ADMIN_PASSWORD`) — lab only
- Interim boot: `HudsonPrivateSecurityRealm` via init groovy
- After plugin configure: same credentials on `EzkeyMockSecurityRealm`

## Secret handling (M2 / M3)

- Stored as `hudson.util.Secret` on the realm config (Jenkins instance encryption).
- **Never logged** (no integration secret, no challenge codes in logs).
- **Credentials plugin (`StringCredentials`) — deferred (not cheap for M3):** lab skips the setup wizard, so credentials + plain-credentials + transitive detached plugins are not present; pulling that fan-out solely to relocate one secret is accidental complexity. Keep `hudson.util.Secret` through M4 unless Credentials is already installed for another reason.

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
| Zero extra Maven deps in `.hpi` | Zero compile-scope dependencies; Java 17 | Fits Jenkins plugin packaging |
| Dogfood | `ezkey-demo-app-acme` uses the same Integration client | Proven in-repo |

**Not needed:** Admin API client, Auth API / device crypto, or OpenAPI codegen. Those are other surfaces (device path or other SDK folders).

**Dart (`ezkey_dart`):** device-side Auth API helpers — not the Integration API path. No change to the decision.

**Thin adapter (plugin-local, M4):** map break-glass vs Ezkey path; hold `EzkeyClient` from realm config; drive the Jenkins login conversation against Integration create/wait/cancel; never log secret or challenge. No fork of `ezkey-sdk`.

## Login flow for M4 — Integration API consumer rules

M4 implements a live **Integration API** round-trip through the Java SDK. The phone still does Auth API approve/deny; Jenkins never speaks Auth API.

Operator UX rules for the integrating app:

1. **Break-glass** username → local password only (no Integration call).
2. **Ezkey path** after primary credentials (or mock replacement in M2):
   1. Prompt: confirm intent to start an Ezkey auth attempt (explicit Continue) — **before** `createAuthAttempt*`, so abandoned login pages do not leave orphan Integration attempts.
   2. On confirm → `createAuthAttemptByUserIdentifier` (challenge requested as product chooses; still honor the create response).
   3. If the create response includes a usable `authAttemptChallenge` → **display it** to the Jenkins operator so they can match what the device shows (Integration response field; device path is separate).
   4. If challenge was requested / required but no usable code returned, or the UI cannot show it → **fail closed** (deny login) + best-effort `cancelAuthAttempt`.
   5. Wait for approve/deny (`waitForAuthAttempt`); cancel on timeout/abandon where applicable.
   6. **Never log** the challenge code or integration secret (Jenkins log, console, support dumps).

M2 mock does not perform HTTP; M3 locks the design only. M4 implements it.

## Milestone map

| M | Focus |
|---|--------|
| M0 | Jenkins LTS + `m0-ls` |
| M1 | Hello-world `.hpi` |
| M2 | Mock SecurityRealm + break-glass + prior-art |
| **M3** | **SDK / Integration API fit decision (this section) + M4 flow design** |
| M4 | Real Integration API round-trip via SDK + confirm/challenge UI per rules above |
