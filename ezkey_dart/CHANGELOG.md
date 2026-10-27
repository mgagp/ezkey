# Changelog

## 0.3.0

Aligns the SDK with `docs/CLIENT_BEHAVIOUR_CONTRACT.md` (#1052 code follow-up).

- **Decision lock owned by the SDK** (contract §2, Q2/Q10/Q13): `PendingAuthAttempt` carries
  `decisionState` (`AuthAttemptDecisionState.open` / `sending` / `locked`) and `canDecide`.
  `approve` / `deny` refuse locally (nothing sent) when the attempt cannot be decided, which
  also covers racing taps and leave-as-deny after a decision.
- **Unknown respond outcome** (contract §2.3–2.4): a transport failure after the request was
  sent, or an ambiguous 5xx, now throws `EzkeyRespondOutcomeUnknownException` (not retryable,
  attempt locked) instead of a retryable `EzkeyNetworkException` / `EzkeyApiException`. The
  typed 503s (`integration-signing-unavailable`, `audit-chain-heartbeat-degraded`) and 429 stay
  retryable and reopen the attempt.
- **Breaking:** `EzkeyException` has a new sealed subtype; exhaustive `switch` statements over
  it need a new case. A second `approve` / `deny` on the same attempt object now throws
  `EzkeyValidationException` locally instead of reaching the server's 409.
- **Low-S normalization of key-store signatures** (SEC-012): the Auth API rejects high-S ECDSA
  signatures, and Android Keystore returns high-S about half of the time. Every signature from
  `EzkeyDeviceKeyStore.sign` is now normalized before it is sent (verify, pending, respond); a
  malformed one throws `EzkeyLocalStateException` and nothing is sent. New public helpers
  `normalizeEcdsaDerSignatureLowS` and `isLowSEcdsaDerSignature`. The test fake server now
  rejects high-S like the real one.
- `EzkeyProblemKind.resourceNotFound` and `methodNotAllowed`: all 19 Auth API problem types are
  now mapped, with a test pinning the count to the server catalog.
- README: decision lock, unknown outcome and leave-as-deny guidance; removed the known-gap note.
- E2E: the second-decision case now proves both the local lock and the server's 409.

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
