# Changelog

## 0.2.0

Protocol refresh against the current Auth API and mobile app; the module is now a device-side SDK.

- **SDK layer** (`lib/src/sdk/`): `EzkeyAuthenticator` with two-step enrollment
  (`bindEnrollment` / `completeEnrollment`), instance-info, `checkPending` / `pollPending` /
  `watchPending`, `approve` / `deny`; storage hooks `EzkeyDeviceKeyStore` and
  `EzkeyEnrollmentStore`; `EzkeyUrlPolicy`; typed errors with RFC 9457 problem classification.
- Bind payload is the current 12-segment form (`isSystemIntegration`, `adminType`); bind optional
  strings keep raw server values (they are signed).
- New instance-info payload builder and signature check.
- Verify sends `devicePrivateKeyStorageTier`; `challengeResponse` is mandatory.
- Respond without an integration signature now fails closed (#844); result id / value
  consistency checks.
- Exact `ed25519` algorithm check; bind id / token echo check; integer ids throughout.
- Auth-attempt challenge codes (enrollment-level and per-attempt), local refusal when missing.
- Expiry / cancel / supersede surfaced as `EzkeyApiException.isAttemptNoLongerActionable`,
  advisory `PendingAuthAttempt.latestPossibleExpiry`.
- PKCS#8 import / export for P-256 device keys; deterministic Ed25519 signing helper for tests.
- **Lab layer** (`lib/lab.dart`): Admin / Integration / crypto-api clients, owner-only file stores,
  Demo Device import, endpoint config.
- **CLIs**: `bin/ezkey_dart.dart` (software authenticator: enroll, list, check, instance-info,
  forget) and `bin/ezkey_e2e.dart` (22-case end-to-end suite). `bin/` was previously ignored by the
  repository `.gitignore` and never committed; `!bin/` fixes that.
- Removed the old `lib/src/live/` bridge and `ezkey_auth_runner`.
- Docker-only toolchain: `scripts/dart-docker.sh`, `scripts/test.sh`, `scripts/e2e.sh`.
- README rewritten as a self-contained integration guide for Flutter authenticator apps.

## 0.1.0

- Initial crypto helpers and payload builders.
