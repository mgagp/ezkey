# ezkey-jenkins design notes (lab)

Experimental (#972). Folder-local only.

## Prior art (M2) — SecurityRealm / 2FA

| Reference | Model | Fit for Ezkey |
|-----------|--------|---------------|
| **OpenMFA** | Second factor *after* primary login (TOTP); exemptions for service accounts | Closest UX to “password then device”; exemption ≈ break-glass. Not a full SecurityRealm replace. |
| **miniOrange 2FA** | Overlay 2FA on existing realm; commercial TOTP/etc. | Same overlay idea; heavy / vendor. |
| **totp-login-plugin** (qc2105) | Custom **SecurityRealm** with TOTP | Shows realm-replace is viable for lab. |
| **Google Authenticator / PAM TOTP** (PAM side) | Host SSH second factor | Analogous to ezkey-pam, not Jenkins UI. |

**Lab choice (M2):** custom `EzkeyMockSecurityRealm` (extends `AbstractPasswordBasedSecurityRealm`) — one login form, break-glass local password, everyone else mock-auto-accept. Overlay-after-password (OpenMFA style) remains a M3 option if SDK wiring fits better as a filter than a realm.

## Break-glass (anti-lockout)

Same spirit as ezkey-pam: a local admin path that does **not** depend on Ezkey being up.

- Username default `admin` (override `EZKEY_JENKINS_ADMIN_USER`)
- Password default `ezkey-lab-admin` (override `EZKEY_JENKINS_ADMIN_PASSWORD`) — lab only
- Interim boot: `HudsonPrivateSecurityRealm` via init groovy
- After plugin configure: same credentials on `EzkeyMockSecurityRealm`

## Secret handling (M2)

- Stored as `hudson.util.Secret` on the realm config (Jenkins instance encryption).
- **Never logged** (no secret, no challenge codes in logs).
- Credentials plugin `StringCredentials` preferred for production; deferred in M2 because this lab skips the setup wizard (no recommended plugins) and credentials pulls a large detached dependency set. Revisit in M3 if install story is worth it.

## Challenge display rules (M3/M4) — from PAM lesson

When the Ezkey create/auth response includes a challenge code (or challenge was requested):

1. **Always display** the server-returned challenge to the operator, regardless of a local “challenge off” preference if the server actually returned a code / requested challenge.
2. **Fail closed** if challenge was requested but no usable code was returned (or the conversation cannot display it).
3. **Never log** the challenge code (syslog, Jenkins log, console dump of secrets).

M2 mock does not perform HTTP; these rules are normative for M3/M4 implementation.

## Milestone map

| M | Focus |
|---|--------|
| M0 | Jenkins LTS + `m0-ls` |
| M1 | Hello-world `.hpi` |
| M2 | Mock SecurityRealm + break-glass + prior-art (this file) |
| M3 | SDK fit decision |
| M4 | Real Ezkey round-trip + challenge UI per rules above |
