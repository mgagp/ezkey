# ezkey-lab-auth

Experimental Jenkins plugin (#972, phase 2 #1184).

- Passwordless: Ezkey is the only factor for users listed on the realm
- Break-glass admin: local password on `/login` (works when the Integration API is down)
- Sign-in: `/ezkey-login/` — Jenkins' Sign in link points here (`getLoginUrl`)
- Flow: enabled user → Integration `createAuthAttemptByUserIdentifier` → display challenge → wait / cancel
- Unknown users see the same wait page and get no push
- Bundles `org.ezkey:ezkey-sdk` (Integration API only; never Auth API)
- `forceChallenge` defaults to true. The sign-in checkbox can only add a code (#998)
- JCasC symbol: `ezkey`
- Build: `../scripts/build-auth-plugin.sh` (Docker `maven:3.9-eclipse-temurin-21`)
- Secret: `hudson.util.Secret` (never logged; never log challenge codes)

Not for production.
