# Admin Token Management (Tests) - Short Reference

## What this document is (and is not)

This is a **brief index/overview** of how Ezkey functional tests obtain a **GlobalAdmin bearer token**.
It intentionally avoids duplicating the full step-by-step flows already documented elsewhere.

## Source of truth

- **Token acquisition patterns + examples**: `FUNCTIONAL_TESTING_GUIDE.md` (section "Obtaining Admin Tokens")
- **Full bootstrap/token flow (all API calls + cache files + RestAssured switching)**: `BOOTSTRAP_FLOW_ANALYSIS.md`

## How tests get an admin token

### Preferred entrypoint

Use `AuthTokenManager.getAdminToken()` in tests.

### Actual priority order (important)

`AuthTokenManager.getAdminToken()` uses this order:

1. **Environment variable** `EZKEY_ADMIN_TOKEN`
2. **Cached token** from `.ezkey-test/admin-token.json` (**validated** before reuse; invalid cache is deleted)
3. **Automatic bootstrap** via `AdminBootstrapService.ensureAdminToken()` (when bootstrap dependencies are set)

Separately, `AdminBootstrapService.ensureAdminToken()` uses a three-tier strategy:

1. Cached token (`.ezkey-test/admin-token.json`, validated)
2. Reuse device credentials (`.ezkey-test/device-credentials.json`) to mint a new token
3. Initial bootstrap (extract credentials → enroll device → verify) then mint a token

## Cache files (GlobalAdmin)

- **`.ezkey-test/bootstrap-credentials.json`**: extracted from Docker logs; reused across runs
- **`.ezkey-test/device-credentials.json`**: created after successful bootstrap; enables fast token creation
- **`.ezkey-test/admin-token.json`**: the cached bearer token; may be invalidated by logout/rotation and will be regenerated

## “Building block” tests (optional, but useful)

These tests are mainly helpful to **pre-warm caches** or **debug token/bootstrapping issues**:

- `AdminTokenCreationTest`: obtains a token (reusing cache/credentials when possible) and validates it against a protected endpoint.
- `AdminInitialBootstrapTest`: forces a fresh bootstrap (useful after a Docker reset, or to validate bootstrap behavior).
- `BootstrapCredentialsExtractionTest`: extracts bootstrap credentials from Docker logs (useful when diagnosing extraction issues).

## Practical guidance

- **Do not rely on test order**: any test should be able to call `getAdminToken()` and proceed.
- **After a Docker reset**: delete `.ezkey-test/*.json` (or run `AdminInitialBootstrapTest`) to rebuild credentials cleanly.
- **After a logout test**: expect the cached token to become invalid; the framework regenerates it on the next `getAdminToken()` call.

## Code references

- `src/test/java/org/ezkey/tests/util/AuthTokenManager.java`
- `src/test/java/org/ezkey/tests/util/AdminBootstrapService.java`

