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

**Current (M2–M4):** integration secret stored as `hudson.util.Secret` on the realm (Jenkins instance encryption). **Never logged** (no integration secret, no challenge codes).

**Credentials plugin (`StringCredentials`) — deferred (not cheap):** the lab skips the setup wizard, so credentials + plain-credentials + transitive detached plugins are not present. Pulling that fan-out solely to relocate one secret is accidental complexity.

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

## Login flow (M4) — Integration API consumer

Implemented: `EzkeyLabLogin` (`/ezkey-lab-login/`) + `EzkeyLabLoginSupport` + `EzkeyClient`.

1. **Break-glass** → standard `/login` local password only.
2. **Ezkey path** (`/ezkey-lab-login/`):
   1. Confirm form: username + typed `ok` — **before** create (no orphan attempts on abandoned pages).
   2. On confirm → `createAuthAttemptByUserIdentifier(user, true)`.
   3. If create returns a usable `authAttemptChallenge` → **display** on the wait page (never log it).
   4. If challenge missing → **fail closed** + best-effort `cancelAuthAttempt`.
   5. Poll `waitForAuthAttempt`; Cancel button → cancel; ACCEPTED → Jenkins session; REJECTED/EXPIRED → deny.
3. Jenkins compose joins `ezkey_ezkey-network` as an **external** network only (DNS to
   `integration-api:7080`). No shared volumes; still a separate compose project. Fallback:
   `host.docker.internal:7080` when the external network is unavailable.

## Milestone map

| M | Focus |
|---|--------|
| M0 | Jenkins LTS + `m0-ls` |
| M1 | Hello-world `.hpi` |
| M2 | SecurityRealm + break-glass + prior-art |
| M3 | SDK / Integration API fit decision |
| **M4** | **Live Integration round-trip + confirm/challenge UI** |
| M5 | (proposed) polish / Credentials-optional / seed automation |
