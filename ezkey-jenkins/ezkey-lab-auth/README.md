# ezkey-lab-auth

Experimental Jenkins plugin (#972).

- Break-glass admin: local password on the standard Jenkins login form
- Ezkey MFA: `/ezkey-lab-login/` — confirm → Integration `createAuthAttemptByUserIdentifier` →
  display challenge → `waitForAuthAttempt` / cancel
- Bundles `org.ezkey:ezkey-sdk` (Integration API only; never Auth API)
- Realm `forceChallenge` (default false) — see `../DESIGN.md` challenge matrix A/B/D (C out of scope)
- Build: `../scripts/build-auth-plugin.sh` (Docker `maven:3.9-eclipse-temurin-21`)
- Secret: `hudson.util.Secret` (never logged; never log challenge codes)

Not for production.
