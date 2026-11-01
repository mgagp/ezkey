---
name: mobile-ios-phase-4-native-crypto-parity
overview: Implement the iOS EzkeyCryptoModule (Swift, CryptoKit + Secure Enclave) with wire-format parity to the Android reference, an honest simulator fallback, and focused XCTest coverage.
status: approved 2026-11-01 — stage 2 review integrated (A1–A10); D1–D6 and A2 decided. Execution in progress.
issue: "#152"
branch: feature/152-mobile-ios
created_at: 2026-11-01
review: plans/mobile_ios_phase_4_native_crypto_parity.review.md
supersedes: archived mobile_ios_phase_4_native_crypto_parity.plan.md (R-2026-0003, 2026-05-24)
---

# iOS step 4 — Native crypto parity (`EzkeyCryptoModule`, Swift)

## Context (state at plan time)

- `ezkey_mobile/ios/` is a clean RN 0.88.1 scaffold (TB-2026-0004), iOS **15.5+**, bundle id
  `org.ezkey.mobile`. Baseline and decisions: `ezkey_mobile/docs/IOS_SETUP.md`.
- `ios/EzkeyMobile/Crypto/EzkeyCryptoModule.swift` + `.m` exist and export **only** the
  `isDebugBuild` constant (legacy-module interop via `RCT_EXTERN_MODULE`).
- JS contract: `ezkey_mobile/app/services/crypto/nativeCrypto.ts` (`NativeModuleShape`). Each JS
  method calls the native method when present, else a JS fallback (mostly throwing).
- Reference implementation: `ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/`
  (`EzkeyCryptoModule.kt`, `EcdsaLowS.kt`, `EcdsaDerCodec.kt`, `IntegrationKeyVerifier.kt`).
- Wire canon: `docs/CRYPTO.md`. The Auth API **rejects high-S ECDSA** signatures
  (`ezkey-core/.../signature/SignatureService.java:219`, SEC-012).
- No physical iPhone, no Apple Developer account: everything in this step must be provable on the
  **simulator**, which has **no Secure Enclave**.
- Enrollment order (`useEnrollmentWizard.ts`): `bind` HTTP call first, then key generation /
  public key / tier / signing for `verify`. End-to-end proof against the Auth API is **step 5**
  (local Docker stack via `http://localhost:8080`; no ngrok).
- No XCTest target exists yet (`EzkeyMobileTests`: 0 hits in `project.pbxproj`; no test target in
  the `Podfile`).

## Scope — native methods to implement on iOS

| JS method (`nativeCrypto.ts`) | Android behavior (reference) | iOS implementation |
|---|---|---|
| `generateEnrollmentKeyPair(id, securityLevel?)` | EC P-256 in Keystore, StrongBox preferred; **no-op `true` if alias exists**; `securityLevel` accepted, unused | Secure Enclave P-256 key (`SecureEnclave.P256.Signing.PrivateKey`), opaque `dataRepresentation` stored in Keychain; simulator fallback per **D2**; same "exists → `true`" semantics; `securityLevel` accepted, unused |
| `getPublicKey(id)` | X.509 SPKI DER, standard Base64 (no wrap) | `P256.Signing.PublicKey.derRepresentation` (SPKI DER, iOS 14+), standard Base64 |
| `getEnrollmentPrivateKeyStorageTier(id)` | `STRONG` (StrongBox) / `STANDARD` (TEE) / `NONE` (software) | Secure Enclave → `STRONG` (**D1**); software (simulator) → `NONE` |
| `sign(id, data)` | SHA256withECDSA over UTF-8, DER, **low-S normalized**, standard Base64 | CryptoKit `signature(for: Data(data.utf8))` (hashes SHA-256 internally) → `rawRepresentation` → low-S normalize → `ECDSASignature(rawRepresentation:).derRepresentation` → standard Base64 (**A2**) |
| `verify(data, sig, pub)` | Ed25519 (integration key), raw 32-byte key + 64-byte sig, Base64URL **or** standard Base64, PEM/whitespace tolerant; **returns `false`** (never throws) on malformed input | CryptoKit `Curve25519.Signing.PublicKey(rawRepresentation:)` + `isValidSignature`; flexible decoding per *Flexible Base64*; same `false`-on-malformed semantics |
| `generateProofToken()` | 32 + 16 CSPRNG bytes, Base64URL no padding, `random.salt` | `SecRandomCopyBytes`; same format (already documented in `MOBILE_CRYPTO_REFERENCE.md` as iOS behavior) |
| `deleteKeyPair(id)` | delete alias if present; resolve `true` | delete Keychain item if present (`errSecItemNotFound` = success); resolve `true` |
| `getBuildTimestamp()` / `getGitShortSha()` | Gradle `BuildConfig` (`android/app/build.gradle:118-157`) | Info.plist keys `EzkeyBuildTimestamp` / `EzkeyGitShortSha` written into the **built product's** Info.plist by a Run Script phase (**D4**, spec below); read via `Bundle.main.infoDictionary` |
| `canUseProtectedSigning()` / `signWithAuthentication()` / `authenticateSecurityPreferenceDowngrade()` | BiometricPrompt (`BIOMETRIC_STRONG \| DEVICE_CREDENTIAL`) as a **gate**, then sign with the same non-auth-bound key | Included (**D3**): `LAContext` `.deviceOwnerAuthentication` as the same gate; spec in *Local authentication*; adds `NSFaceIDUsageDescription` |

**Out of scope (deliberately not implemented on iOS):**

- `sealSecret` / `unsealSecret` / `deleteAllSealKeys`: `secureStorage.ts:56` routes iOS to the Keychain
  (`react-native-keychain`, `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`); sealed secrets are Android-only
  (MOB-017). Leaving the methods absent makes `nativeCrypto.ts` use its fallbacks (`deleteAllSealKeys`
  → `false`; seal/unseal throw but are never called on iOS). Document this in `NATIVE_MODULES.md`.
- Key attestation (App Attest / DeviceCheck): tier stays client-reported, as on Android
  (`docs/CRYPTO.md` § storage tier).
- Binding the key itself to user presence (`.userPresence` access control): Android does not do it
  either (MOB-001 Track B deferred). Parity first.
- Purging `react-native-keychain` secrets on reinstall (see *Reinstall hygiene*): deliberately left.

## Design details

### Key storage

- Keychain item per enrollment: `kSecClassGenericPassword`, service `org.ezkey.mobile.enrollment-key`,
  account `ezkey_enrollment_<enrollmentId>` (same naming as the Android alias), value = Secure Enclave
  `dataRepresentation` (an opaque blob only usable by this device's SE) or, for the software
  fallback, the P-256 `rawRepresentation`, prefixed by a 1-byte kind tag (`0x01` SE, `0x02` software)
  so the tier is derived from what is stored, never guessed. Unknown tag → the operation's own error
  code (fail closed).
- Accessibility `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (no iCloud/backup migration; parity
  with Android `setUnlockedDeviceRequired` intent). SE key created with `SecAccessControl`
  `[.privateKeyUsage]` and the same accessibility.
- **Injectable storage (A1):** the key store takes the Keychain service and the `UserDefaults`
  instance as init parameters (production defaults: service above, `UserDefaults.standard`). Tests
  use their own service (e.g. `org.ezkey.mobile.enrollment-key.tests`) and suite, because the host
  app's own module also initializes during hosted tests.

### Reinstall hygiene (A1)

- **Why it is mandatory:** Keychain items survive app uninstall (simulator included); Android
  Keystore keys do not. `localEnrollmentId` is deterministic —
  `deriveLocalEnrollmentId(installationId, serverEnrollmentId)` (`useEnrollmentWizard.ts:222`) —
  and with a clean-start stack server ids restart at 1, so after a reinstall colliding ids are the
  **normal case**. Without a purge, "exists → `true`" would silently reuse a previous
  installation's key.
- **Mechanism:** a purge runs **lazily, once per process, on the module's serial queue, before the
  first key operation** (not in `init`: legacy modules are created lazily, on any thread with
  `requiresMainQueueSetup = false`). If the `UserDefaults` marker `ezkeyCryptoInstallMarker` is
  absent (UserDefaults is wiped on uninstall), delete every item of the enrollment-key service
  (`errSecItemNotFound` = success), then set the marker. The marker is set **only after** a
  successful purge.
- **Fail closed:** until the purge has succeeded, every key operation (`generate`, `getPublicKey`,
  tier, `sign`, `signWithAuthentication`, `deleteKeyPair`) rejects with its own error code. The
  purge is retried on the next key operation.
- **Deliberately untouched:** `react-native-keychain` secrets (`secureStorage.ts`, service
  `serviceFor(key)`) also survive uninstall while their AsyncStorage metadata
  (`enrollmentStorage.ts:170`) does not. They become orphaned: harmless (unreachable without
  metadata) but residual. Recorded as a known iOS gap in `IOS_SETUP.md`, not purged in this step.
- **Side note (not this step):** the same stale-key reuse can happen on Android after a stack
  clean-start without reinstalling (same "exists → `true`" rule). Mention it in the close-out; an
  `I-*` only if the maintainer wants it tracked.

### Signature encoding (wire parity, A2)

- CryptoKit ECDSA output is not guaranteed low-S. Take `rawRepresentation` (32-byte `r` ‖ 32-byte
  `s`); if `s > n/2` replace with `n − s` (n = secp256r1 order
  `FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551`), using a small fixed-width
  256-bit big-endian compare/subtract helper (no BigInt dependency).
- **No hand-written DER.** Re-wrap with `P256.Signing.ECDSASignature(rawRepresentation:)` and use
  `.derRepresentation` (minimal DER, `0x00` padding handled by CryptoKit). Parsing, where tests need
  it, uses `init(derRepresentation:)`. Standard Base64.
- Pure helpers (`normalizeLowS(raw64) -> raw64`, the 256-bit helper, flexible Base64, `LAError`
  mapping) live in `ios/EzkeyMobile/Crypto/EzkeyCryptoCodec.swift` (no React, no Keychain) so they
  are unit-testable.

### Flexible Base64 (A3)

Mirror `IntegrationKeyVerifier.decodeFlexibleBase64ToBytes`: strip PEM header/footer lines and all
whitespace → map `-`→`+`, `_`→`/` → re-pad with `=` to a multiple of 4 → `Data(base64Encoded:)`.
Failure → `verify` resolves `false`. Accepted difference: mixed alphabets decode on iOS but not in
the JDK; harmless because the result is still `false` on a bad signature.

### Local authentication (D3, A7)

- **Order (Android `EzkeyCryptoModule.kt:376-436`):** check availability (`canEvaluatePolicy`) →
  check the key exists → prompt (`evaluatePolicy`) → sign. Never prompt for a missing key (missing →
  `EZK_KEY_NOT_FOUND`).
- **`localizedReason`:** reuse the Android subtitle strings (`BIOMETRIC_PROMPT_SUBTITLE`,
  `SECURITY_DOWNGRADE_PROMPT_SUBTITLE`) verbatim.
- **Error mapping (pure function, tested):** `LAError.userCancel`, `.systemCancel`, `.appCancel` →
  `EZK_AUTH_CANCELLED`; every other `LAError` (`passcodeNotSet`, `biometryNotAvailable`,
  `biometryNotEnrolled`, `biometryLockout`, `notInteractive`, `authenticationFailed`, …) →
  `EZK_AUTH_UNAVAILABLE`. The JS branches on these two codes (`usePendingAuth.ts:230`,
  `SecurityScreen.tsx:91`).
- **`canUseProtectedSigning()`:** `canEvaluatePolicy(.deviceOwnerAuthentication)`; `false` when no
  passcode/biometry is enrolled.
- **Threading:** `evaluatePolicy` does not need the main thread. Its completion arrives on a private
  queue; dispatch back to the module's serial queue before signing.

### Build info Run Script (D4, A4)

- Script phase `Ezkey build info`, placed **last** in the app target's build phases (after Info.plist
  processing and the RN bundle phase; Xcode signs after all phases).
- Writes with `/usr/libexec/PlistBuddy` into `"$TARGET_BUILD_DIR/$INFOPLIST_PATH"` (never the source
  `Info.plist`): `Add` or `Set` `EzkeyBuildTimestamp` and `EzkeyGitShortSha` (string).
- Declares `$(TARGET_BUILD_DIR)/$(INFOPLIST_PATH)` as both input and output file (works with user
  script sandboxing; if the build still fails on a sandbox denial, set
  `ENABLE_USER_SCRIPT_SANDBOXING = NO` on the app target and say so in the PR).
- "Based on dependency analysis" **unchecked** (`alwaysOutOfDate = 1`) so the timestamp refreshes on
  every build.
- Same rules as Android `build.gradle:118-157`: timestamp `date -u +%Y-%m-%dT%H:%M:%SZ`; SHA =
  `git -C "$SRCROOT" rev-parse --short=7 HEAD`, kept only if it matches `^[0-9a-f]{7,}$` (cut to 7),
  else `unknown`. The script never fails the build.
- Native getters reject with `EZK_BUILD_TIMESTAMP_ERROR` / `EZK_GIT_SHA_ERROR` only if the key is
  absent from the bundle.

### Error codes and concurrency (A6)

Reuse the Android codes verbatim (`EZK_KEY_GENERATION_ERROR`, `EZK_PUBLIC_KEY_ERROR`, `EZK_SIGN_ERROR`,
`EZK_KEY_NOT_FOUND`, `EZK_DELETE_ERROR`, `EZK_VERIFY_ERROR`, `EZK_PROOF_TOKEN_ERROR`,
`EZK_STORAGE_TIER_ERROR`, `EZK_BUILD_TIMESTAMP_ERROR`, `EZK_GIT_SHA_ERROR`, `EZK_AUTH_CANCELLED`,
`EZK_AUTH_UNAVAILABLE`).

| Situation | Result |
|---|---|
| Missing key on `getPublicKey`, tier, `sign`, `signWithAuthentication` | reject `EZK_KEY_NOT_FOUND` |
| Missing key on `deleteKeyPair` | resolve `true` |
| `SecItemAdd` → `errSecDuplicateItem` on generate | resolve `true` ("exists" semantics) |
| Any other `OSStatus` failure (e.g. `errSecInteractionNotAllowed` while locked) | reject with the operation's own code (e.g. `EZK_SIGN_ERROR`), `OSStatus` in the message |
| Purge not yet successful | reject with the operation's own code |
| `verify` with any malformed input | resolve `false`, never reject |

Concurrency: the module exposes a dedicated serial `methodQueue`
(`DispatchQueue(label: "org.ezkey.mobile.crypto")`); every method runs on it.

### Bridge

Keep the legacy interop (`RCT_EXTERN_METHOD` with `RCTPromiseResolveBlock` / `RCTPromiseRejectBlock`),
`requiresMainQueueSetup = false`, serial `methodQueue` (above). A TurboModule/codegen spec is **not**
introduced in this step (Android also uses the legacy module).

**Invariants (A10):** `constantsToExport()` / `isDebugBuild` stay byte-identical (the F2a gate depends
on them); the module header comment is updated (no longer "constants only"); Android code untouched.

### XCTest target (D5, A5)

Created **with the `xcodeproj` gem**, never by hand-editing `project.pbxproj` and without a manual
Xcode step. The gem (1.28.1) is already in `ezkey_mobile/vendor/bundle` as a CocoaPods dependency and
was used in step 3 to add the Crypto group.

1. A one-shot Ruby script (scratchpad, run with `bundle exec ruby` from `ezkey_mobile/`) that:
   - creates target `EzkeyMobileTests` (`:unit_test_bundle`, iOS 15.5) with group
     `EzkeyMobileTests/` holding the test sources;
   - sets `TEST_HOST = $(BUILT_PRODUCTS_DIR)/EzkeyMobile.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/EzkeyMobile`,
     `BUNDLE_LOADER = $(TEST_HOST)`, `PRODUCT_BUNDLE_IDENTIFIER = org.ezkey.mobile.tests`,
     `GENERATE_INFOPLIST_FILE = YES`, `SWIFT_VERSION` = the app's value;
   - adds a target dependency on `EzkeyMobile` (hosted tests: the Keychain works, no `-34018`);
   - adds the target as a testable in the **shared** scheme
     `EzkeyMobile.xcodeproj/xcshareddata/xcschemes/EzkeyMobile.xcscheme` (`Xcodeproj::XCScheme`).
   The script is idempotent (no-op if the target exists) and is not committed; the committed
   artifacts are the resulting `project.pbxproj` and scheme.
2. `Podfile`: nested `target 'EzkeyMobileTests' do inherit! :complete end` inside the app target,
   then `bundle exec pod install`.
3. Tests use `@testable import EzkeyMobile`.
4. Command (Metro not required; a red-box in the host app does not affect the tests):

   ```bash
   cd ezkey_mobile/ios
   xcodebuild test -workspace EzkeyMobile.xcworkspace -scheme EzkeyMobile \
     -destination 'platform=iOS Simulator,name=iPhone 18 Pro' \
     -derivedDataPath <scratchpad>/dd
   ```

5. Durable lesson for `IOS_SETUP.md`: project/scheme changes go through the `xcodeproj` gem.

## Maintainer decisions

- **D1 — Secure Enclave tier. Decided: `STRONG`.** The SE is a dedicated, isolated
  coprocessor with its own TRNG and key storage, the iOS counterpart of StrongBox (Android
  `STANDARD` = TEE on the main CPU). Rejected alternative: `STANDARD`.
  Either way the tier stays client-reported, never "verified".
- **D2 — Software fallback. Decided:** allow a software P-256 key (tier `NONE`) **only when
  `SecureEnclave.isAvailable == false`**, which in practice means the simulator (every iPhone able to
  run iOS 15.5 has a Secure Enclave). On a device where SE generation *fails*, reject (fail closed)
  rather than silently downgrading. Matches Android's honest `NONE` on emulators.
- **D3 — Local authentication in this step. Decided: include** (small; Face ID can be simulated:
  Simulator › Features › Face ID › Enrolled / Matching Face).
- **A2 — DER. Decided (2026-11-01): use CryptoKit's `derRepresentation`**; no custom encoder.
- **D4 — Build info. Decided (2026-11-01):** keep the Run Script that writes the built
  Info.plist. A generated xcconfig is read when the build starts, before any phase runs, so its
  timestamp/SHA would lag one build behind unless generated in a scheme pre-action (silent on
  failure, invisible in logs); a generated Swift file adds a gitignored source to the project. The
  plist route is the common iOS pattern for git SHA / build stamps and has one moving part.
- **D5 — Test target creation. Decided (2026-11-01):** scripted with the `xcodeproj`
  gem (above). Rejected: hand-editing the pbxproj (fragile), a manual Xcode step (not reproducible by
  a cold agent), XcodeGen/Tuist (new tool, conflicts with RN template upgrades).
- **D6 — Server-verifier evidence. Decided (2026-11-01):** instead of `jshell`, POST
  the XCTest-printed vector to the local Crypto API `POST http://localhost:9090/api/v1/crypto/validate`
  (`{data, signature, publicKey}`), which calls the server's own
  `SignatureService.validateSignature` (low-S check included). Evidence in the PR; nothing
  committed. Requires the clean-start stack (already running).

## Execution order

0. Create the XCTest target (D5) and check that an empty test runs green.
1. Codec helpers (low-S, flexible Base64, `LAError` mapping) + bridge-level tests — **RED first**.
2. Key store (injectable) + lazy purge + generate / public key / tier / delete + `sign`.
3. `verify` (Ed25519), `generateProofToken`, build info Run Script (D4).
4. Local authentication (D3) + `NSFaceIDUsageDescription`.
5. Docs (A9): `IOS_SETUP.md` (gaps incl. orphaned `react-native-keychain` secrets, device-only list,
   `xcodeproj` gem rule, test command), `NATIVE_MODULES.md`, `MOBILE_CRYPTO_REFERENCE.md` § iOS,
   `docs/CRYPTO.md:24`, `MOBILE_DEVELOPER_GUIDE.md` (tier derivation), the `nativeCrypto.ts:31`
   comment ("iOS uses bundle mtime proxy" → Info.plist build stamp), module header; TB/plan status.
   `PrivacyInfo.xcprivacy` already declares UserDefaults `CA92.1`: checked, no change.

## Proof ladder (A8)

**Native unit tests (XCTest, simulator, `xcodebuild test`):**

- **Bridge level (RED baseline — fail today because the methods do not exist):** instantiate
  `EzkeyCryptoModule` (test Keychain service and suite) and call the `@objc` methods with
  resolve/reject closures. Assert `EZK_KEY_NOT_FOUND` for `getPublicKey`, tier, `sign`,
  `signWithAuthentication` on a missing key; `deleteKeyPair` resolves `true` on a missing key;
  `verify` resolves `false` (never rejects) on malformed input.
- Low-S helper: a crafted high-S `r‖s` → `n − s`; a low-S input unchanged; `s == n/2` unchanged.
- **Malleability test (deterministic):** sign → flip `s` to `n − s` (high-S) → assert CryptoKit
  still accepts the flipped signature → normalize → assert it equals the original and verifies.
- DER: `derRepresentation` of a signature whose `r`/`s` have the high bit set carries the `0x00` pad;
  short (leading-zero) integers are minimally encoded.
- SPKI: exported public key is 91 bytes and starts with the fixed P-256 SPKI prefix
  `3059301306072a8648ce3d020106082a8648ce3d030107034200`.
- Sign → verify round trip (software key on simulator), ≥ 200 iterations: each `sign()` output,
  decoded from Base64 DER, verifies over the same UTF-8 bytes **and is low-S**.
- Ed25519 `verify`: RFC 8032 test vector 1 → `true`; flipped signature byte → `false`; wrong key →
  `false`; malformed Base64, 31-byte key, 63-byte signature → `false` (no throw); Base64URL,
  unpadded and standard Base64 accepted; PEM-wrapped key accepted.
- Proof token: two dot-separated Base64URL parts, no `=`; decoded lengths 32 and 16; two calls differ.
- Key lifecycle: generate twice → same public key (exists → `true`); delete → `getPublicKey` rejects
  `EZK_KEY_NOT_FOUND`; tier on simulator = `NONE`.
- Purge: with the marker absent, pre-existing items of the test service are removed and the marker
  is set; a simulated purge failure leaves the marker unset and key operations reject; items of
  another service are **untouched** (collateral invariance).
- `LAError` mapping: the three cancel codes → `EZK_AUTH_CANCELLED`; a sample of the others →
  `EZK_AUTH_UNAVAILABLE`.

**Server-verifier evidence (D6):** one XCTest prints a vector (SPKI, data, signature); POST it to the
local Crypto API `/validate` → `valid: true`; the same vector with `s` flipped to high-S → `valid:
false`. Pasted in the PR. Full acceptance by the Auth API (`bind`/`verify`) remains the step-5 gate.

**Reinstall purge on the simulator (manual, evidence in the PR):** enroll a key (or create it via a
debug test), `xcrun simctl uninstall booted org.ezkey.mobile`, reinstall, relaunch, trigger a key
operation → the old item is gone. Invariance: a `react-native-keychain` item written before is still
readable afterwards (it is the documented orphan).

**Local authentication (manual simulator pass):** Features › Face ID › Enrolled, then Matching /
Non-matching, through the Security screen toggle and the downgrade confirmation; with Face ID not
enrolled and no passcode, `canUseProtectedSigning` → `false`.

**Pre-existing path conditions (iOS behaviour after the change):**

- enrollment wizard deletes the key when `verify` fails (`useEnrollmentWizard.ts`,
  `deleteEnrollmentKeyPair` catch) → native `deleteKeyPair` now runs and resolves `true`;
- enrollment removal (`enrollmentStorage.ts:192`, `deleteKeyPair`) → same;
- JS `deleteAllSealKeys` fallback → still `false` (native method absent by design).

**JS layer:** `yarn test`, `yarn typecheck`, `yarn lint` stay green. JS change limited to the
`nativeCrypto.ts:31` comment.

**App-level (simulator, Maestro):** home → **+** → F2a seed → `bind` fails as before without a
backend, so **no live enrollment crypto runs in this step** (step 5 is the real gate). The About
screen shows the build timestamp and git SHA.

**Device-only, explicitly NOT proven in this step:** Secure Enclave generation/signing, `STRONG`
tier, real Face ID / passcode prompts. Record them as device-required in `IOS_SETUP.md`.

## Exit gate

All XCTest cases green on the iOS 27 simulator; Crypto API evidence (D6), reinstall-purge and Face ID
manual passes recorded in the PR; JS suite green; `isDebugBuild` unchanged; app builds and launches;
docs updated with conservative iOS wording; device-only items listed. Live enrollment crypto is
**not** exercised here; step 5 proves bind/verify/pending/respond against the local Docker Auth API.

## Main risks

- Silent wire-format drift (DER/SPKI/low-S) → codec + malleability tests, Crypto API evidence; final
  proof in step 5.
- Overclaiming Secure Enclave on simulator → tier derived from the stored kind tag, `NONE` on simulator.
- Keychain persistence across reinstall → lazy fail-closed purge, unit-tested and simctl-proven.
- Fragile project edits (test target, Run Script) → `xcodeproj` gem only; exact Run Script spec.
- Simulator-only proof hides SE-specific failures → listed as device-required; step 6 (real device).
