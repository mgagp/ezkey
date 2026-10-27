# ezkey_dart — Ezkey device-side protocol SDK for Dart / Flutter

**Status: experimental lab module** (version 0.2.0, `publish_to: none`). This repository's
production authenticator is the React Native app in `../ezkey_mobile/`. This package reimplements
that app's protocol logic in pure Dart, so a **Flutter** authenticator app can act as an Ezkey
device. It is tested against the real server stack (see [§6 Running the tests](#6-running-the-tests)).

This README is self-contained: you can integrate Ezkey into a Flutter app with only this file and
the code in this folder. For application-independent **client behaviour** (decision lock, unknown
`respond` outcome, leave-as-deny, approve-only local confirmation, error presentation), also read
[`../docs/CLIENT_BEHAVIOUR_CONTRACT.md`](../docs/CLIENT_BEHAVIOUR_CONTRACT.md). Deeper protocol
references in `../docs/` (`MOBILE_DEVELOPER_GUIDE.md`, `CRYPTO.md`,
`ENROLLMENT_SIGNATURE_PAYLOAD.md`, `AUTH_ATTEMPT_SIGNATURE_PAYLOAD.md`) remain optional for wire
detail. Test commands: [§6 Running the tests](#6-running-the-tests).

---

## 1. What Ezkey is (one paragraph)

Ezkey is a self-hosted passwordless / MFA server. An **integrating application** (a web portal,
an SSH PAM module, the Ezkey Admin UI itself, …) asks the Ezkey **Integration API** to create an
**auth attempt** for a user ("Alice wants to log in"). The user's phone runs an **authenticator
app** that has previously **enrolled** with the Ezkey **Auth API**. During enrollment it created an
EC P-256 key pair whose private key never leaves the phone. The phone fetches the pending attempt,
shows its context, and the user **approves or denies** it. The phone signs that decision with its
device key. The integrating application then gets the result (approved, denied, failed, expired,
cancelled). Every server answer the phone receives is signed by the integration's **Ed25519** key,
and the phone pins that key at enrollment, so a network attacker can neither forge prompts nor
results. This package is the phone side of that protocol.

### Glossary

| Term | Meaning |
|------|---------|
| Auth API | Device-facing HTTP API (`/api/v1/enrollments/*`, `/api/v1/auth-attempts/*`). The **only** API an authenticator calls. |
| Integration API | Server-to-server API the integrating application uses to create, wait for and cancel auth attempts. Never called by the device. |
| Admin API / Admin UI | Operator console; creates integrations, API keys and enrollment invitations. Never called by the device. |
| Integration | A relying application registered in Ezkey, with its own Ed25519 signing key pair (server-side). |
| Enrollment | One (user × integration × device) binding. Identified by `enrollmentId` (int). |
| Enrollment proof token | Secret bearer string identifying the enrollment. It comes from the QR code and stays valid for the enrollment's life. **Secret.** |
| Enrollment challenge | Number (6 digits) shown to the user out of band (Admin UI, e-mail, …) and typed into the phone to finish enrollment. **Not** in the QR code. |
| Auth attempt | One login / approval request. Identified by `authAttemptId` (int); carries a secret `authAttemptProofToken`. |
| Auth-attempt challenge | Optional number displayed by the integrating application. When `challengeRequired` is true the user must type it on the phone to approve (protects against "push fatigue" approvals). |
| Device proof token | Fresh random string the phone signs on every `pending` call (proof of key possession). |

---

## 2. Protocol overview

### 2.1 Cryptography

| Who signs | Algorithm | Key format on the wire | Signature format |
|-----------|-----------|------------------------|------------------|
| Device (phone) | ECDSA P-256 / SHA-256 | SPKI DER, **standard** Base64 (with padding) | ASN.1 DER, **standard** Base64 |
| Integration (server) | Ed25519 (`integrationKeyAlgorithm` must be exactly `ed25519`) | raw 32 bytes, **Base64URL without padding** | raw 64 bytes, Base64URL without padding |

Signed data is always a **canonical UTF-8 string** of `|`-separated segments (not JSON):
null becomes the empty string, booleans are `true` / `false`, numbers are decimal, and
free-text segments are Unicode **NFC**-normalized before signing. The library builds and checks
all of them. You never assemble these strings yourself.

| Payload | Segments | Signed by |
|---------|----------|-----------|
| Bind | `enrollmentProofToken\|enrollmentId\|integrationPublicKey\|integrationKeyAlgorithm\|integrationName\|integrationDescription\|enrollmentName\|tenantId\|tenantName\|tenantDescription\|isSystemIntegration\|adminType` | integration |
| Verify (device) | `enrollmentProofToken\|enrollmentId\|enrollmentChallenge\|devicePublicKey` | device |
| Verify result | `enrollmentProofToken\|enrollmentId\|VERIFIED\|enrollmentVerifyMessage` | integration |
| Instance info | `enrollmentProofToken\|enrollmentId\|INSTANCE_INFO\|authApiPublicBaseUrl\|instanceName\|instanceDescription\|aboutUrl` | integration |
| Pending (device) | the raw `deviceProofToken` string | device |
| Pending (server) | `authAttemptProofToken\|challengeRequired\|contextTitle\|contextMessage` | integration |
| Respond (device) | `authAttemptProofToken\|accepted` | device |
| Respond result | `authAttemptProofToken\|authAttemptId\|result\|authAttemptMessage` | integration |

### 2.2 Enrollment sequence

```text
Admin UI / app           User                 Phone (this SDK)                 Auth API
     | create invitation   |                        |                              |
     |-- QR code --------->|-- scan --------------->|                              |
     |-- challenge (6 dg)->|                        |                              |
     |                     |                        |-- POST /enrollments/bind --->|  {enrollmentId, enrollmentProofToken}
     |                     |                        |<-- integration key + names --|  signed (bind payload, Ed25519)
     |                     |                        |  verify signature, pin key   |
     |                     |<- "Enroll with ACME?" -|                              |
     |                     |-- types challenge ---->|                              |
     |                     |                        |  generate P-256 key in       |
     |                     |                        |  platform keystore           |
     |                     |                        |-- POST /enrollments/verify ->|  {challengeResponse, devicePublicKey,
     |                     |                        |                              |   enrollmentProofTokenSigned, tier}
     |                     |                        |<-- active=true, signed ------|  (verify-result payload)
     |                     |                        |  verify signature, persist   |
```

QR code content (one of):

```json
{"enrollmentId": 42, "enrollmentProofToken": "…", "authUrl": "https://auth.example.com"}
```

or the legacy `42|<enrollmentProofToken>`. `authUrl` is optional; the app may configure a default.

Server rules you must design the UI around:

- **Bind is one-shot.** A second bind of the same invitation fails with `enrollment-already-bound`.
  If the user abandons after the bind, the administrator must issue a new invitation.
- **A wrong enrollment challenge burns the invitation** (`enrollment-verify-failed`; the server
  invalidates it). Ask the user to double-check the code before submitting.
- Invitations expire (`enrollment-invitation-expired`).

### 2.3 Auth-attempt sequence

```text
Integrating app          Integration API        Auth API                 Phone (this SDK)
  |-- create attempt -->| (ttlSeconds 15..120, optional title/message, challengeRequested)
  |<- id, challenge, expiresAt                  |                              |
  |  shows challenge code to the user (if any)  |                              |
  |-- wait (long poll) >|                       |<-- POST /auth-attempts/pending (signed fresh device proof token)
  |                     |                       |--- 200 attempt (signed) / 204 none ------>|
  |                     |                       |                              | verify signature, show prompt
  |                     |                       |<-- POST /auth-attempts/respond (accepted, signed, [challenge])
  |                     |                       |--- 200 result (signed) ----------------->| verify signature
  |<- ACCEPTED/REJECTED/INVALID/EXPIRED/CANCELLED                              |
```

Server rules:

- **`pending` claims the attempt (one-shot).** The newest `PENDING` attempt moves to `READ` and
  is never returned again. If your app loses it (crash, process death), the user must restart the
  login. Keep the claimed `PendingAuthAttempt` in memory until the user decides.
- **Only the newest attempt is delivered.** Creating a new attempt supersedes older open ones.
- **Expiry.** Per-request applied lifetime is 15–120 s (`ttlSeconds` floor 15, hard clamp 120);
  configured property default is typically 120 (Bean Validation 30–600, startup clamp → effective
  configured default 30–120). The device API does not expose `expiresAt`. The SDK therefore gives
  an advisory upper bound (`PendingAuthAttempt.latestPossibleExpiry` = claim time + 120 s).
  Expired attempts are not delivered; responding to one gives HTTP 409 `auth-attempt-expired`.
- **Cancel.** The integrating application may cancel at any time. Before the claim, the phone
  simply sees nothing (204). After the claim, respond returns 409 `auth-attempt-state-conflict`,
  the same answer as "superseded" or "already answered". All three are final: show "This request
  is no longer active".
- **Challenge.** When `challengeRequired` is true, approving requires the number the integrating
  application displays. A wrong number makes the server return a signed `FAILED` and
  invalidates the attempt (the application sees `INVALID`). Denying never needs the number.
- Respond results: `APPROVED`, `DENIED`, `FAILED`, `EXPIRED`.

---

## 3. Library API surface

Import `package:ezkey_dart/ezkey_dart.dart` (pure Dart, no `dart:io`; usable from Flutter on
Android and iOS). `package:ezkey_dart/lab.dart` contains `dart:io` lab tools (file stores, Admin / Integration
API clients for tests). **Do not ship `lab.dart` in an app.**

### 3.1 `EzkeyAuthenticator` (main entry point)

```dart
final authenticator = EzkeyAuthenticator(
  keyStore: myPlatformKeyStore,          // EzkeyDeviceKeyStore (you implement; §4.2)
  enrollmentStore: mySecureEnrollmentStore, // EzkeyEnrollmentStore (you implement; §4.3)
  // optional:
  httpClient: http.Client(),             // package:http client (inject for tests / pinning)
  requestTimeout: const Duration(seconds: 15),
  urlPolicy: const EzkeyUrlPolicy(),     // https only + plain http for localhost/10.0.2.2
  clock: DateTime.now,
  keyAliasFactory: (enrollmentId) => '…',
);
```

| Method | What it does | Typical errors |
|--------|--------------|----------------|
| `bindEnrollment(EnrollmentCode code, {String? authApiBaseUrlOverride})` → `EzkeyBoundEnrollment` | Bind step. Checks the id / token echo, the algorithm (`ed25519`) and the bind signature. Exposes `integrationName`, `tenantName`, `enrollmentName`, `isSystemIntegration`, `adminType` for the confirmation screen. | API `enrollmentAlreadyBound`, `enrollmentInvitationExpired`, `enrollmentBindingFailed`; integrity `bindSignature`, `bindConsistency`, `unsupportedIntegrationKeyAlgorithm`; validation (no URL, already enrolled locally) |
| `completeEnrollment(EzkeyBoundEnrollment bound, {required int enrollmentChallenge})` → `EzkeyEnrollment` | Generates the device key through your key store, signs and sends verify, checks the verify-result signature and `active`, then saves the record. On **any** failure it deletes the new key and saves nothing. | API `enrollmentVerifyFailed` (invitation now dead); integrity `verifySignature`, `verifyNotActive`; network |
| `enroll(code, {required enrollmentChallenge, authApiBaseUrlOverride})` | `bindEnrollment` + `completeEnrollment`. | same |
| `fetchInstanceInfo(enrollment)` → `EzkeyInstanceInfo` | Signed installation branding (`instanceName`, `instanceDescription`, `aboutUrl`, `authApiPublicBaseUrl`). | integrity `instanceInfoSignature`, `instanceInfoConsistency` |
| `listEnrollments()` / `forget(enrollment)` | Local list / local delete (record + key). There is no server-side unenroll endpoint for the device. | — |
| `checkPending(enrollment)` → `PendingAuthAttempt?` | One `pending` call with a fresh signed device proof token. `null` means nothing pending. A non-null result is **claimed** (one-shot). | API `authAttemptBindingFailed` (enrollment revoked / bad proof); integrity `pendingSignature`; network |
| `pollPending(enrollment, {interval: 2 s, timeout: 60 s})` | Repeats `checkPending` until an attempt arrives or the timeout passes (then `null`). Network errors are retried; other errors are thrown. | same |
| `watchPending(enrollment, {interval})` → `Stream<PendingAuthAttempt>` | Polls while listened to; cancel the subscription to stop. | same |
| `approve(enrollment, attempt, {int? challengeResponse})` → `AuthAttemptOutcome` | Signs and sends approve. Throws locally (sends nothing) if a challenge is required and missing. | API `authAttemptStateConflict` / `authAttemptExpired` (`isAttemptNoLongerActionable`), `integrationSigningUnavailable` (retryable); integrity `respondSignature`, `respondConsistency` |
| `deny(enrollment, attempt)` → `AuthAttemptOutcome` | Signs and sends deny. | same |

`AuthAttemptOutcome.result` is an `AuthAttemptResult` (`approved`, `denied`, `failed`,
`expired`); `failed` after an approve means "wrong challenge code, the attempt is now invalid".

### 3.2 Models (`lib/src/sdk/models.dart`)

- `EnrollmentCode.parse(String qr, {EzkeyUrlPolicy policy})` takes the JSON or `id|token` form.
  An `authUrl` that the policy rejects fails closed.
- `parseChallengeCode(String typed)` → `int`. Accepts 1–9 digits and ignores spaces and hyphens
  (`"123 456"`).
- `EzkeyEnrollment` is the persisted record: `toJson()` / `EzkeyEnrollment.fromJson(...)`,
  `localId` (`"<authApiBaseUrl>#<enrollmentId>"`), `deviceKeyAlias`, display metadata. It contains
  the enrollment proof token, so **store it as a secret**.
- `PendingAuthAttempt` holds `authAttemptId`, `challengeRequired`, `contextTitle`,
  `contextMessage` (all authenticated), `receivedAt`, `latestPossibleExpiry`,
  `isCertainlyExpired(now)`.
- `DevicePrivateKeyStorageTier` is `none` / `standard` / `strong` (sent to the server as
  self-reported metadata).
- `maxAuthAttemptTtl` = 120 s.

### 3.3 Storage hooks

- `EzkeyDeviceKeyStore` has `storageTier`, `generateKeyPair(alias)` → SPKI Base64,
  `sign(alias, payload)` → DER Base64, `publicKey(alias)`, `deleteKeyPair(alias)`.
  The bundled `SoftwareDeviceKeyStore` is for tests only (tier `NONE`, keys in Dart memory).
- `EzkeyEnrollmentStore` has `save`, `load(localId)`, `list`, `delete(localId)`.
  `InMemoryEnrollmentStore` is for tests only.

### 3.4 Errors (`lib/src/sdk/errors.dart`)

All thrown errors extend the sealed `EzkeyException` (`message`, `retryable`). `toString()` never
contains tokens, keys, signatures or response bodies.

| Type | Meaning | UI guidance |
|------|---------|-------------|
| `EzkeyValidationException` | Bad local input (malformed QR, bad URL, missing challenge, already enrolled). Nothing was sent. | Show an input error. |
| `EzkeyApiException` | Non-2xx answer. `statusCode`, `kind` (`EzkeyProblemKind`, from the RFC 9457 `type`), `problem`, `isAttemptNoLongerActionable`, `retryable`. | Map `kind` to your own localized copy. Do not show `problem.detail` to end users. |
| `EzkeyIntegrityException` | Signature or consistency check failed (`check` names which one). | Fail closed: show "Security check failed", do not display server text, approve nothing, persist nothing. |
| `EzkeyNetworkException` | Timeout, DNS, TLS or connection failure (`cause`). Retryable. | "Can't reach the server"; offer Retry. |
| `EzkeyLocalStateException` | Stored record or key missing / corrupt. | Offer to remove the enrollment and re-enroll. |

### 3.5 Low-level crypto (usually not needed)

`generateEcP256KeyPair`, `signEcdsaSha256` (RFC 6979 deterministic, low-S), `verifyEcdsaSha256`,
`ecPublicKeyToSpkiBase64`, `ecKeyPairFromPkcs8Base64` / `ecPrivateKeyToPkcs8Base64`,
`verifyEd25519`, `generateProofToken` / `generateSecureRandomBytes` (backed by `Random.secure()`),
and the payload builders `buildEnrollmentBindPayload`, `buildEnrollmentVerifyDevicePayload`,
`buildEnrollmentVerifyResultPayload`, `buildEnrollmentInstanceInfoPayload`, `buildPendingPayload`,
`buildRespondPayload`, `buildRespondResultPayload`. `AuthApiClient` (the raw transport behind
`EzkeyAuthApi`) is exported for custom flows.

---

## 4. Integrating into an existing Flutter authenticator app

### 4.1 Add the dependency

Copy or vendor this folder, or reference it by path or git:

```yaml
dependencies:
  ezkey_dart:
    path: ../ezkey_dart        # or: git: {url: …, path: ezkey_dart}
```

Runtime dependencies: `cryptography`, `pointycastle`, `http`, `unorm_dart`, `args` (CLI only).
Dart SDK `>=3.7.0`.

### 4.2 Implement `EzkeyDeviceKeyStore` on the platform keystore

The private key must never enter Dart memory. Use a small platform channel (or an existing plugin
that exposes P-256 sign-only keys):

- **Android:** use `KeyPairGenerator.getInstance("EC", "AndroidKeyStore")` with
  `KeyGenParameterSpec.Builder(alias, PURPOSE_SIGN)`,
  `.setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))` and `.setDigests(DIGEST_SHA256)`.
  Try `.setIsStrongBoxBacked(true)` first and fall back if `StrongBoxUnavailableException`
  (report `strong` vs `standard`). Sign with `Signature.getInstance("SHA256withECDSA")`; the
  output is already DER. `publicKey.encoded` is already SPKI DER: Base64-encode it with
  `Base64.NO_WRAP` (standard alphabet, with padding).
- **iOS:** use `SecKeyCreateRandomKey` with `kSecAttrKeyTypeECSECPrimeRandom`, 256 bits,
  `kSecAttrTokenIDSecureEnclave` and `kSecAttrIsPermanent`. Sign with
  `SecKeyCreateSignature(.ecdsaSignatureMessageX962SHA256)`; the output is DER and the API
  hashes the message for you. `SecKeyCopyExternalRepresentation` returns a 65-byte X9.63 point:
  prepend the 26-byte P-256 SPKI header
  `3059301306072a8648ce3d020106082a8648ce3d030107034200` before Base64.
- `sign(alias, payload)` receives the canonical **string**. Sign its UTF-8 bytes and do not
  pre-hash.
- Optional: require user presence (biometric) before deciding. `pending` also signs with the
  same key, so a key that demands biometrics for *every* signature prompts on every poll. Prefer
  an **app-level** biometric / device-credential confirmation on **Approve only** (per-enrollment
  policy plus a device-wide preference). Deny and leave-as-deny **SHOULD NOT** prompt (**Q11**,
  decided; reference app code may still prompt on Deny until that follow-up ships — see
  [`CLIENT_BEHAVIOUR_CONTRACT.md`](../docs/CLIENT_BEHAVIOUR_CONTRACT.md) §4).

The SDK hands you a random alias (`ezkey-device-<id>-<random>`) and deletes the key itself when
enrollment fails or when `forget` is called.

### 4.3 Implement `EzkeyEnrollmentStore` on secure storage

Serialize `EzkeyEnrollment.toJson()` into `flutter_secure_storage` (Keychain /
EncryptedSharedPreferences), keyed by `localId`. Never put records in plain
`SharedPreferences`, files or logs: the enrollment proof token is a bearer credential.

### 4.4 Screens and flow

1. **Scan.** Scan the QR and call `EnrollmentCode.parse(raw)`. On `EzkeyValidationException`,
   show "Invalid code".
2. **Confirm.** Call `bindEnrollment(code)` and show `integrationName`, `tenantName` and
   `enrollmentName`. If `isSystemIntegration` is true and `adminType` is set (`GLOBAL_ADMIN` /
   `TENANT_ADMIN`), this enrollment protects an **Ezkey administrator** login; say so.
3. **Challenge entry.** The user types the 6-digit enrollment code. Call
   `parseChallengeCode(text)`, then `completeEnrollment(bound, enrollmentChallenge: n)`. Warn
   before submitting: a wrong code invalidates the invitation.
4. **Home / approvals.** Call `checkPending` on user action ("Check now"), when the app returns to
   the foreground, or with `pollPending` / `watchPending` while an approvals screen is visible.
   Do not poll forever in the background (battery, rate limits); the protocol has no push channel
   yet.
5. **Prompt.** Show `contextTitle` / `contextMessage` (authenticated by the server signature)
   and the integration name. If `challengeRequired`, show a numeric field: "Type the code
   shown on your screen". Hide the prompt once `isCertainlyExpired(DateTime.now())`.
6. **Decide.** Call `approve(...)` or `deny(...)`.
   - `approved`: "Approved".
   - `denied`: "Denied".
   - `failed`: "Wrong code. Start the login again on your computer".
   - `EzkeyApiException.isAttemptNoLongerActionable`: "This request is no longer active" or
     "expired" (`kind == authAttemptExpired`). Offer Back only, never Retry.
   - **UNKNOWN outcome:** if `respond` was **sent** but you get **no HTTP response** (timeout /
     transport cut), treat as unknown (**contract MUST**, **Q10**): **lock that `authAttemptId`**,
     never send a second decision, point the user at the integrating application, Back only.
   - Ambiguous **5xx** after send **SHOULD** use the same UNKNOWN posture (**extension of Q10**).
     **503** `integrationSigningUnavailable` remains **retryable**, but a retry **may** return
     terminal **409** (e.g. wrong challenge already committed `INVALID` before signing failed).
     Do **not** offer “answer again” for UNKNOWN cases.
   - **Known gap (SDK and RN app):** today a lost `respond` surfaces as `EzkeyNetworkException`
     with `retryable: true`, and many 5xx kinds are also `retryable` / unlocked. Do not follow
     those flags for post-send uncertainty; code follow-up (#1052 plan).
   - Pre-send `retryable` errors and classified **4xx** / **429** with a body: offer Retry per
     catalog without treating them as UNKNOWN.
7. **Settings.** Use `fetchInstanceInfo` for "About this server", and `forget` to remove an
   enrollment locally (tell the user an administrator must also revoke it server-side).

### 4.5 Testing your integration

Use `SoftwareDeviceKeyStore` + `InMemoryEnrollmentStore` + a `MockClient` (see
`test/support/fake_ezkey_server.dart`, a protocol-faithful fake Auth API with real signatures)
for widget and unit tests. Then run `bin/ezkey_e2e.dart` against a real stack (§6).

---

## 5. Security pitfalls (read before shipping)

- **Key storage.** Hardware-backed, non-exportable P-256 keys only. Never generate the
  production key with `SoftwareDeviceKeyStore`. Report `storageTier` honestly (`none` / `standard`
  / `strong`); the server records it for operators but cannot attest it.
- **Secrets never logged.** Never log, print, crash-report or analytics-track any of: the
  enrollment proof token, the QR content, the `authAttemptProofToken`, device proof tokens,
  signatures, private keys, or raw HTTP bodies. SDK models and exceptions redact these in
  `toString()`. Don't defeat that with `jsonEncode(enrollment.toJson())` in a log statement.
  Disable HTTP logging interceptors in release builds.
- **Secure randomness.** Device proof tokens and key aliases use `Random.secure()`. Never replace
  them with `Random()`, timestamps or counters: replayed proofs are rejected, and predictable
  ones weaken the binding.
- **TLS.** Use HTTPS only. `EzkeyUrlPolicy` allows plain HTTP only for `localhost`, `127.0.0.1`
  and `10.0.2.2` (emulator). Never ship a build with extra `insecureHttpHosts`. Do not install a
  permissive `badCertificateCallback`. Certificate / SPKI pinning is optional: inject a pinned
  `http.Client` (e.g. an `IOClient` over a custom `HttpClient`). Trust in the integration key is
  **trust on first use** at bind time, so TLS is what protects the first contact.
- **QR codes are attacker-controllable.** An `authUrl` inside a QR is validated by the URL policy.
  Consider showing the host on the confirmation screen, or ignoring `authUrl` and using a fixed
  server for single-tenant deployments (`authApiBaseUrlOverride`).
- **Fail closed on integrity errors.** Never display server-provided context or approve after an
  `EzkeyIntegrityException`.
- **Clock skew.** The SDK never trusts the device clock for security decisions. The server
  enforces expiry. `latestPossibleExpiry` uses the local clock only to hide stale prompts. Do
  not compute countdowns from server timestamps (the device API returns none). Assume the phone
  clock can be minutes off.
- **Challenge codes.** Never pre-fill or auto-detect the auth-attempt challenge (e.g. from the
  clipboard or notifications). The point is that the human copies it from the screen they are
  logging into.
- **One-shot claims.** Don't call `checkPending` from several isolates or background tasks at
  once for the same enrollment: whichever call claims the attempt owns it.
- **Error copy.** Map `EzkeyProblemKind` / problem `type` and HTTP status to local strings. **Never
  display** `problem.detail`, `title`, or exception `message` to end users (same rule as
  [`CLIENT_BEHAVIOUR_CONTRACT.md`](../docs/CLIENT_BEHAVIOUR_CONTRACT.md) §7).
- **Decision lock.** After any `respond` that completed or became undecidable at the HTTP layer,
  lock that attempt id and do not send another approve/deny for it (including leave-as-deny).
  See contract §§2–3 (**Q2**, **Q5**, **Q10**, **Q13**).

---

## 6. Running the tests

The host only needs **Docker** (and Bash: Git Bash on Windows). The Dart toolchain runs in the
official `dart:stable` image. Run from the repository root.

### 6.1 Offline gate (format, analyze, unit tests)

```bash
ezkey_dart/scripts/test.sh
```

This runs `dart pub get`, `dart format --set-exit-if-changed`, `dart analyze --fatal-infos` and
`dart test`. The unit tests in `test/` cover:

| File | Coverage |
|------|----------|
| `authenticator_test.dart` | Full SDK flows against the fake server: enrollment (happy path, two-step, URL override / policy, local duplicate, rebind, tampered bind fields, key substitution, wrong algorithm, id echo, wrong challenge → key deleted, verify tamper, `active=false`, network failure), forget, instance-info, pending (fresh proof per poll, one-shot claim, NFC context, tampered title / challenge flag), approve / deny, respond tamper / missing signature / id mismatch / unknown result / 503 retryable / double respond, enrollment-level and per-attempt challenge (local refusal, wrong code → FAILED, deny without code), cancel before / after claim, supersede, expiry, `pollPending` / `watchPending` including network retries, `toString` redaction |
| `crypto_test.dart` | P-256 keys, PKCS#8 round trip, RFC 6979 determinism, low-S, DER/SPKI formats, JVM interop vectors (ECDSA verify, Ed25519 byte-for-byte), tamper rejection |
| `payload_test.dart` | Golden canonical strings for all eight payloads (shared with the mobile app's tests), NFC normalization, null / boolean encoding |
| `models_test.dart`, `errors_test.dart` | QR parsing, challenge parsing, record JSON round trip, URL policy, RFC 9457 classification, retryability |
| `auth_api_client_test.dart` | Paths under a base-path prefix, 204 handling, problem parsing, timeout / transport mapping, malformed bodies |
| `stores_test.dart`, `encoding_test.dart`, `secure_random_test.dart` | Store hooks, lab file stores (0600 permissions), env parsing, Demo Device import, Base64 variants, randomness |

Run a single file: `ezkey_dart/scripts/dart-docker.sh test test/authenticator_test.dart`.

### 6.2 End-to-end against the real stack

```bash
ezkey_dart/scripts/e2e.sh --clean-start     # runs ezkey-tests/clean-start.sh, then the suite
ezkey_dart/scripts/e2e.sh                   # stack already up
ezkey_dart/scripts/e2e.sh -- --skip-expiry  # extra args go to bin/ezkey_e2e.dart
```

What it does:

1. Optionally runs the repository's standard clean start (`ezkey-tests/clean-start.sh`). This
   gives an empty database, the bootstrap Global Admin `admin.docker`, and the **Demo Device**
   (a simulated phone) enrolled for that admin.
2. Waits until the admin, auth, integration, crypto and demo-device containers are healthy.
3. Copies the Demo Device's enrollment record (which holds the bootstrap admin's device key) into
   an owner-only temp directory. It mounts that directory read-only into the Dart container and
   deletes it on exit; the record is never printed.
4. Runs `bin/ezkey_e2e.dart` in `dart:stable` on the stack's Docker network
   (`ezkey_ezkey-network`), so it reaches `auth-api:8080`, `admin-api:9080`,
   `integration-api:7080` and `crypto-api:9090` by service name.

The suite logs into the Admin API **through the SDK itself**: it approves the admin's own
passwordless login with the imported Demo Device key. It then provisions a throwaway integration
(`dart-e2e-<tag>`), an API key and invitations, and runs 22 cases. Each prints `PASS` / `FAIL` /
`SKIP`; the exit code is non-zero on any failure.

- Oracle parity: 7 canonical payload types vs the server's crypto-api `/payload-helper`, with NFD input,
  plus ECDSA / Ed25519 signature interop.
- Admin passwordless login via the SDK.
- Enrollment: plain, challenge-required, `TENANT_ADMIN` onboarding (system integration), wrong
  challenge (invitation burnt), rebind refused.
- Instance-info, empty pending, device proof token single-use after a claim.
- Approve with NFC context (integration sees ACCEPTED), deny (REJECTED), double respond
  (conflict).
- Challenge: local refusal without a code, wrong code (FAILED / INVALID), right code (ACCEPTED),
  per-attempt `challengeRequested`.
- Cancel after claim (conflict), cancel before claim (nothing delivered), supersede.
- Expiry (TTL 15 s, ~17 s wait).
- CLI subprocess: `ezkey_dart enroll` + `check --wait --decide approve`, then empty `check`
  exits 3.

Endpoint overrides: `EZKEY_AUTH_API_URL`, `EZKEY_ADMIN_API_URL`, `EZKEY_INTEGRATION_API_URL`,
`EZKEY_CRYPTO_API_URL` (environment or `--env-file`; see `.env.example`). Other variables:
`EZKEY_DOCKER_NETWORK` and `EZKEY_DEMO_DEVICE_CONTAINER`.

### 6.3 Interactive CLI (software authenticator)

`bin/ezkey_dart.dart` is a lab authenticator that keeps its state (records and PKCS#8 keys,
chmod 600) in `--state-dir` (default `.ezkey-state/`, gitignored):

```bash
D="ezkey_dart/scripts/dart-docker.sh --network ezkey_ezkey-network --"
$D run bin/ezkey_dart.dart --allow-insecure-http-host auth-api \
   enroll --code-file qr.json --challenge 123456 --auth-api-url http://auth-api:8080
$D run bin/ezkey_dart.dart list
$D run bin/ezkey_dart.dart --allow-insecure-http-host auth-api check --wait 30 --decide ask
$D run bin/ezkey_dart.dart --allow-insecure-http-host auth-api instance-info
$D run bin/ezkey_dart.dart forget --enrollment 7
```

`check --decide` accepts `ask` | `approve` | `deny` | `none`, and `--challenge N` supplies the
code. Exit codes: `0` ok, `1` error / FAILED / no longer active, `2` usage, `3` nothing pending.
The QR JSON is the Admin UI invitation content (`enrollmentId`, `enrollmentProofToken`, optional
`authUrl`); the challenge is shown next to it.

---

## 7. Known limits and protocol gaps

- **No `expiresAt` on the device API.** Only the advisory 120 s bound is available on the device.
  The integrating application sees the exact `expiresAt`.
- **Cancel is indistinguishable** from superseded / already answered (all return 409
  `auth-attempt-state-conflict`). This is by design: the outcome is the same for the user.
- **No push channel.** Polling only. No device-initiated unenroll and no key rotation endpoint;
  re-enroll instead.
- **Trust on first use.** The integration key is pinned at bind. There is no out-of-band
  verification of it, and there is no SDK-side recovery if the server rotates it (re-enroll).
- **Storage tier is self-reported** and not attested (no Android Key Attestation / App Attest).
- **Empty polls do not consume the device proof token** server-side (only a claim persists it).
  The SDK uses a fresh token per call anyway.
- **At-rest encryption (Tink keysets) is server-internal** and has no wire impact.
- Not covered here: Flutter UI widgets, platform-channel key store implementations (sketched in
  §4.2), localization, and certificate pinning helpers.
- Lab layer (`lib/lab.dart`, `bin/`) uses software keys and Docker-network plain HTTP. It is for
  testing only.

## 8. Layout

```text
lib/ezkey_dart.dart        public SDK entry (pure Dart)
lib/src/*.dart             crypto, encoding, canonical payloads
lib/src/sdk/               authenticator, transport, models, errors, storage hooks, URL policy
lib/lab.dart, lib/src/lab/ dart:io lab tools (file stores, Admin/Integration/crypto API clients)
bin/ezkey_dart.dart        software authenticator CLI
bin/ezkey_e2e.dart         end-to-end protocol suite
scripts/                   dart-docker.sh, test.sh, e2e.sh
test/                      unit tests (+ test/support fake Auth API, JVM vectors)
```
