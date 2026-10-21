# ezkey-jenkins design notes (lab)

Experimental (#972). Folder-local only.

## Prior art (M2) — SecurityRealm / 2FA

| Reference | Model | Fit for Ezkey |
|-----------|--------|---------------|
| **OpenMFA** | Second factor *after* primary login (TOTP); exemptions for service accounts | Closest UX to “password then device”; exemption ≈ break-glass. Not a full SecurityRealm replace. |
| **miniOrange 2FA** | Overlay 2FA on existing realm; commercial TOTP/etc. | Same overlay idea; heavy / vendor. |
| **totp-login-plugin** (qc2105) | Custom **SecurityRealm** with TOTP | Shows realm-replace is viable for lab. |
| **Google Authenticator / PAM TOTP** (PAM side) | Host SSH second factor | Analogous to ezkey-pam, not Jenkins UI. |

**Lab choice (M2):** custom `EzkeyMockSecurityRealm` (extends `AbstractPasswordBasedSecurityRealm`) — one login form, break-glass local password, everyone else mock-auto-accept. Overlay-after-password (OpenMFA style) remains available if M4 UX needs a second step after Jenkins password.

## Break-glass (anti-lockout)

Same spirit as ezkey-pam: a local admin path that does **not** depend on Ezkey being up.

- Username default `admin` (override `EZKEY_JENKINS_ADMIN_USER`)
- Password default `ezkey-lab-admin` (override `EZKEY_JENKINS_ADMIN_PASSWORD`) — lab only
- Interim boot: `HudsonPrivateSecurityRealm` via init groovy
- After plugin configure: same credentials on `EzkeyMockSecurityRealm`

## Secret handling (M2 / M3)

- Stored as `hudson.util.Secret` on the realm config (Jenkins instance encryption).
- **Never logged** (no secret, no challenge codes in logs).
- **Credentials plugin (`StringCredentials`) — deferred (not cheap for M3):** lab skips the setup wizard, so credentials + plain-credentials + transitive detached plugins are not present; pulling that fan-out solely to relocate one secret is accidental complexity. Keep `hudson.util.Secret` through M4 unless Credentials is already installed for another reason.

## M3 — Java SDK fit (decision)

**Decision (one line):** Java SDK (`org.ezkey:ezkey-sdk`) is **adequate as-is** for Jenkins login; the plugin adds only a thin UX/session adapter (confirm → create → display challenge → wait), not a new SDK.

### Evidence

| Need for Jenkins login | SDK surface | Verdict |
|------------------------|-------------|---------|
| Create attempt by Jenkins username | `createAuthAttemptByUserIdentifier(user, challengeRequested[, context])` | Covered |
| Optional challenge on create | `AuthAttemptCreateResponse.authAttemptChallenge()` | Covered |
| Wait for approve/deny | `waitForAuthAttempt(id, …)` + `isReadTimeout()` | Covered |
| Cancel on abandon / fail-closed | `cancelAuthAttempt(id)` | Covered |
| HTTP Basic integration key + secret | `EzkeyClient.builder().integrationKey().secretKey().baseUrl()` | Covered |
| Zero extra Maven deps in `.hpi` | Zero compile-scope dependencies; Java 17 | Fits Jenkins plugin packaging |
| Dogfood | `ezkey-demo-app-acme` uses the same client | Proven in-repo |

**Not needed:** Admin API client, Auth/device crypto, or OpenAPI codegen. Those are other SDK folders / surfaces.

**Dart (`ezkey_dart`):** device-side crypto + payload helpers for Auth API — teaches nothing for Integration M2M login. No change to the decision.

**Thin adapter (plugin-local, M4):** map break-glass vs Ezkey path; hold `EzkeyClient` from realm config; drive the login conversation (confirm / challenge UI); never log secret or challenge. No fork of `ezkey-sdk`.

## Login flow for M4 — confirm before create + challenge rules

Aligned with PAM lesson from [#975](https://github.com/mgagp/ezkey/issues/975) / PR #976 (enrollment challenge always shown; fail closed; never log code; explicit operator confirm), adapted for Jenkins:

PAM SSH does **create → show prompt → require `ok` → wait**. Jenkins lab M4 will **confirm before create** so abandoned login pages do not leave orphan Integration auth attempts:

1. **Break-glass** username → local password only (no Ezkey). Same anti-lockout spirit as PAM.
2. **Ezkey path** after primary credentials (or mock replacement in M2):
   1. Prompt: confirm intent to start Ezkey MFA (explicit affirmative, e.g. typed `ok` / UI Continue) — **before** `createAuthAttempt*`.
   2. On confirm → `createAuthAttemptByUserIdentifier` (challenge requested as product chooses; still honor server).
   3. If the create response includes a usable `authAttemptChallenge` → **always display it** to the operator, even if a local “challenge off” setting would have preferred waiting-only copy (PAM #975 class of bug).
   4. If challenge was requested / required but no usable code returned, or the UI cannot show it → **fail closed** (deny login) + best-effort cancel.
   5. Wait for approve/deny (SDK `waitForAuthAttempt`); cancel on timeout/abandon where applicable.
   6. **Never log** the challenge code or integration secret (Jenkins log, console, support dumps).

M2 mock does not perform HTTP; M3 locks the design only. M4 implements it.

## Milestone map

| M | Focus |
|---|--------|
| M0 | Jenkins LTS + `m0-ls` |
| M1 | Hello-world `.hpi` |
| M2 | Mock SecurityRealm + break-glass + prior-art |
| **M3** | **SDK fit decision (this section) + M4 flow design** |
| M4 | Real Ezkey round-trip using SDK + confirm/challenge UI per rules above |
