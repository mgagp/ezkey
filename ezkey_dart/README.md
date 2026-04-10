# ezkey_dart

Pure Dart cryptographic helpers for the EZKey protocol, plus a small CLI bridge for using real
enrollments from a live `clean-start` stack.

## Prerequisites

- Dart SDK 3.11.4 or compatible (`>=3.7.0 <4.0.0`)
- Optional live stack: `ezkey-tests/clean-start.sh`
- Optional Admin API access token for the CLI bridge

## Unit tests

```bash
C:\Tools\flutter\bin\dart test
```

## Live stack bridge

The CLI reads enrollment data from the Admin API, or from pasted Admin UI JSON.

```bash
C:\Tools\flutter\bin\dart run bin/ezkey_dart.dart list-enrollments \
  --admin-api-url http://localhost:9080 \
  --bearer-token <token>

C:\Tools\flutter\bin\dart run bin/ezkey_dart.dart show-enrollment 1 \
  --admin-api-url http://localhost:9080 \
  --bearer-token <token>

C:\Tools\flutter\bin\dart run bin/ezkey_dart.dart use-enrollment \
  --enrollment-id 1 \
  --admin-api-url http://localhost:9080 \
  --bearer-token <token>

C:\Tools\flutter\bin\dart run bin/ezkey_dart.dart use-enrollment \
  --enrollment-json-file enrollment.json
```

The `use-enrollment` command emits a JSON snapshot with real enrollment fields and a few local test
artifacts such as a generated proof token and sample respond payloads.

## Interactive Auth API runner

The package also includes a separate in-memory runner for a live bind/verify/pending/respond
vertical against the Auth API.

The runner loads `.env` from the current directory and supports these keys:

- `EZKEY_AUTH_API_URL` or `EZKEY_API_BASE_URL`
- `EZKEY_AUTH_TIMEOUT_MS`
- `EZKEY_LANGUAGE`

Run it with (from the `ezkey_dart` package directory):

```bash
dart run bin/ezkey_auth_runner.dart
# or, equivalent:
dart run ezkey_dart:ezkey_auth_runner
```

The Admin API bridge is the same package; you can also use `dart run ezkey_dart` instead of
`dart run bin/ezkey_dart.dart`.

It accepts the enrollment code in the same formats as the mobile flow:

- JSON: `{ "enrollmentId": 123, "enrollmentProofToken": "...", "authUrl": "https://..." }`
- Legacy fallback: `123|proof-token`

Nothing is persisted. The runner binds, verifies, checks `pending`, verifies the integration
signatures, asks for approve/deny, calls `respond`, and loops until `stop` or `exit`.

## Enrollment signatures (bind / verify)

The Auth API returns integration-signed bind payloads and verify-result payloads, and expects the
device to sign a **canonical verify string** (not the raw proof token alone). The Dart helpers match
`docs/ENROLLMENT_SIGNATURE_PAYLOAD.md`:

- `buildEnrollmentBindPayload` — string the integration signs in the bind response
- `buildEnrollmentVerifyDevicePayload` — string the device signs with ECDSA-SHA256 for verify
- `buildEnrollmentVerifyResultPayload` — string the integration signs on the verify HTTP response
  (outcome `VERIFIED`)

`EzkeyAuthSession.enroll` verifies the bind signature before generating device keys, signs the verify
payload, POSTs verify, then verifies the verify-response signature. `EzKeyCrypto` exposes the same
builders for tests and CLI code.
