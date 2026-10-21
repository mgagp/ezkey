# ezkey-lab-auth

Experimental Jenkins plugin (#972).

- Break-glass admin: local password on the standard Jenkins login form
- Ezkey MFA: `/ezkey-lab-login/` — confirm → Integration `createAuthAttemptByUserIdentifier` →
  display challenge → `waitForAuthAttempt` / cancel
- Bundles `org.ezkey:ezkey-sdk` (Integration API only; never Auth API)
- Secret: `hudson.util.Secret` (never logged; never log challenge codes)

Not for production.
