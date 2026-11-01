---
name: mobile-ios-phase-4-native-crypto-parity
overview: Implement the iOS EzkeyCryptoModule (Swift, CryptoKit + Secure Enclave) with wire-format parity to the Android reference, an honest simulator fallback, and focused XCTest coverage.
status: draft — stage 1 (author). Awaiting maintainer decisions D1–D3 and stage-2 cold-agent review.
issue: "#152"
branch: feature/152-mobile-ios
created_at: 2026-11-01
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

## Scope — native methods to implement on iOS

| JS method (`nativeCrypto.ts`) | Android behavior (reference) | iOS implementation |
|---|---|---|
| `generateEnrollmentKeyPair(id, securityLevel?)` | EC P-256 in Keystore, StrongBox preferred; **no-op `true` if alias exists**; `securityLevel` accepted, unused | Secure Enclave P-256 key (`SecureEnclave.P256.Signing.PrivateKey`), opaque `dataRepresentation` stored in Keychain; simulator fallback per **D2**; same "exists → `true`" semantics; `securityLevel` accepted, unused |
| `getPublicKey(id)` | X.509 SPKI DER, standard Base64 (no wrap) | `P256.Signing.PublicKey.derRepresentation` (SPKI DER, iOS 14+), standard Base64 |
| `getEnrollmentPrivateKeyStorageTier(id)` | `STRONG` (StrongBox) / `STANDARD` (TEE) / `NONE` (software) | Secure Enclave → per **D1**; software (simulator) → `NONE` |
| `sign(id, data)` | SHA256withECDSA over UTF-8, DER, **low-S normalized**, standard Base64 | CryptoKit `signature(for: Data(data.utf8))` (hashes SHA-256 internally) → raw `r‖s` → normalize `s` to low-S → encode DER → standard Base64 |
| `verify(data, sig, pub)` | Ed25519 (integration key), raw 32-byte key + 64-byte sig, Base64URL **or** standard Base64, PEM/whitespace tolerant; **returns `false`** (never throws) on malformed input | CryptoKit `Curve25519.Signing.PublicKey(rawRepresentation:)` + `isValidSignature`; same flexible decoding and same `false`-on-malformed semantics |
| `generateProofToken()` | 32 + 16 CSPRNG bytes, Base64URL no padding, `random.salt` | `SecRandomCopyBytes`; same format (already documented in `MOBILE_CRYPTO_REFERENCE.md` as iOS behavior) |
| `deleteKeyPair(id)` | delete alias if present; resolve `true` | delete Keychain item if present; resolve `true` |
| `getBuildTimestamp()` / `getGitShortSha()` | Gradle `BuildConfig` | Info.plist keys `EzkeyBuildTimestamp` / `EzkeyGitShortSha` written into the **built product's** Info.plist by a Run Script build phase (not the source plist); `unknown` when git is unavailable |
| `canUseProtectedSigning()` / `signWithAuthentication()` / `authenticateSecurityPreferenceDowngrade()` | BiometricPrompt (`BIOMETRIC_STRONG \| DEVICE_CREDENTIAL`) as a **gate**, then sign with the same non-auth-bound key | Per **D3**: `LAContext` `.deviceOwnerAuthentication` as the same gate; error mapping `EZK_AUTH_CANCELLED` (user/system/app cancel) vs `EZK_AUTH_UNAVAILABLE`; adds `NSFaceIDUsageDescription` |

**Out of scope (deliberately not implemented on iOS):**

- `sealSecret` / `unsealSecret` / `deleteAllSealKeys`: `secureStorage.ts:56` routes iOS to the Keychain
  (`react-native-keychain`, `AFTER_FIRST_UNLOCK_THIS_DEVICE_ONLY`); sealed secrets are Android-only
  (MOB-017). Leaving the methods absent makes `nativeCrypto.ts` use its fallbacks (`deleteAllSealKeys`
  → `false`; seal/unseal throw but are never called on iOS). Document this in `NATIVE_MODULES.md`.
- Key attestation (App Attest / DeviceCheck): tier stays client-reported, as on Android
  (`docs/CRYPTO.md` § storage tier).
- Binding the key itself to user presence (`.userPresence` access control): Android does not do it
  either (MOB-001 Track B deferred). Parity first.

## Design details

### Key storage

- Keychain item per enrollment: `kSecClassGenericPassword`, service `org.ezkey.mobile.enrollment-key`,
  account `ezkey_enrollment_<enrollmentId>` (same naming as the Android alias), value = Secure Enclave
  `dataRepresentation` (an opaque blob only usable by this device's SE) or, for the software
  fallback, the P-256 `rawRepresentation`, prefixed by a 1-byte kind tag (`0x01` SE, `0x02` software)
  so the tier is derived from what is stored, never guessed.
- Accessibility `kSecAttrAccessibleWhenUnlockedThisDeviceOnly` (no iCloud/backup migration; parity
  with Android `setUnlockedDeviceRequired` intent). SE key created with `SecAccessControl`
  `[.privateKeyUsage]` and the same accessibility.
- **Reinstall hygiene (iOS-specific gap):** Keychain items survive app uninstall, Android Keystore
  keys do not. Without mitigation, a reinstalled app would silently reuse a previous installation's
  key for a colliding `enrollmentId` (exists → `true`). Mitigation: on module init, if the
  `UserDefaults` marker `ezkeyCryptoInstallMarker` is absent (UserDefaults is wiped on uninstall),
  delete every item of service `org.ezkey.mobile.enrollment-key`, then set the marker. Fail-closed
  stance: if the purge fails, key generation rejects with `EZK_KEY_GENERATION_ERROR`.

### Signature encoding (wire parity)

- CryptoKit ECDSA output is not guaranteed low-S. Take `rawRepresentation` (32-byte `r` ‖ 32-byte `s`),
  if `s > n/2` replace with `n − s` (n = secp256r1 order
  `FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551`), using a small fixed-width
  256-bit big-endian compare/subtract helper (no BigInt dependency).
- Encode DER `SEQUENCE { INTEGER r, INTEGER s }` with minimal positive INTEGER encoding (strip leading
  zeros, prepend `0x00` when the high bit is set). Standard Base64.
- Pure helpers live in `ios/EzkeyMobile/Crypto/EzkeyCryptoCodec.swift` (no React, no Keychain) so
  they are unit-testable.

### Error codes

Reuse the Android codes verbatim (`EZK_KEY_GENERATION_ERROR`, `EZK_PUBLIC_KEY_ERROR`, `EZK_SIGN_ERROR`,
`EZK_KEY_NOT_FOUND`, `EZK_DELETE_ERROR`, `EZK_VERIFY_ERROR`, `EZK_PROOF_TOKEN_ERROR`,
`EZK_STORAGE_TIER_ERROR`, `EZK_BUILD_TIMESTAMP_ERROR`, `EZK_GIT_SHA_ERROR`, `EZK_AUTH_CANCELLED`,
`EZK_AUTH_UNAVAILABLE`). Missing key → `EZK_KEY_NOT_FOUND` for `getPublicKey`, tier and `sign`.

### Bridge

Keep the legacy interop (`RCT_EXTERN_METHOD` with `RCTPromiseResolveBlock` / `RCTPromiseRejectBlock`),
`requiresMainQueueSetup = false`; operations run off the main thread except `LAContext` UI.
A TurboModule/codegen spec is **not** introduced in this step (Android also uses the legacy module).

## Maintainer decisions needed before execution

- **D1 — Secure Enclave tier.** Recommendation: **`STRONG`**. The SE is a dedicated, isolated
  coprocessor with its own TRNG and key storage, the iOS counterpart of StrongBox (Android
  `STANDARD` = TEE on the main CPU). Alternative: `STANDARD` (more conservative wording).
  Either way the tier stays client-reported, never "verified".
- **D2 — Software fallback.** Recommendation: allow a software P-256 key (tier `NONE`) **only when
  `SecureEnclave.isAvailable == false`**, which in practice means the simulator (every iPhone able to
  run iOS 15.5 has a Secure Enclave). On a device where SE generation *fails*, reject (fail closed)
  rather than silently downgrading. Matches Android's honest `NONE` on emulators.
- **D3 — Local authentication in this step.** Recommendation: **include** (small, Face ID can be
  simulated: Simulator › Features › Face ID › Enrolled / Matching Face). Alternative: defer to step 7.

## Execution order

1. Codec helpers + XCTest target (`EzkeyMobileTests`, hosted in the app) with RED tests first.
2. Key lifecycle (generate / public key / tier / delete / reinstall purge) + `sign`.
3. `verify` (Ed25519), `generateProofToken`, build info (Run Script phase).
4. Local authentication methods (if D3 = include) + `NSFaceIDUsageDescription`.
5. Docs: `IOS_SETUP.md` (gaps), `NATIVE_MODULES.md`, `MOBILE_CRYPTO_REFERENCE.md` § iOS (honest,
   conservative wording), TB/plan status.

## Proof ladder

**Native unit tests (XCTest, simulator, `xcodebuild test`):**

- Low-S: a crafted high-S `r‖s` is normalized to `n − s`; a low-S input is unchanged; `s == n/2`
  boundary unchanged.
- DER: round-trip encode/decode; high-bit `r`/`s` get the `0x00` pad; short (leading-zero) integers
  are minimally encoded.
- SPKI: exported public key is 91 bytes and starts with the fixed P-256 SPKI prefix
  `3059301306072a8648ce3d020106082a8648ce3d030107034200`.
- Sign → verify round trip (software key on simulator): `sign()` output, decoded from Base64 DER,
  verifies with `P256.Signing.PublicKey.isValidSignature` over the same UTF-8 bytes, and is low-S —
  over ≥ 200 iterations (high-S occurs ~50% of the time, so normalization is exercised).
- Ed25519 `verify`: RFC 8032 test vector 1 → `true`; flipped signature byte → `false`; wrong key →
  `false`; malformed Base64, 31-byte key, 63-byte signature → `false` (no throw); Base64URL and
  standard Base64 both accepted; PEM-wrapped key accepted.
- Proof token: two dot-separated Base64URL parts, no `=`; decoded lengths 32 and 16; two calls differ.
- Key lifecycle: generate twice → same public key (exists → `true`); delete → `getPublicKey` rejects
  `EZK_KEY_NOT_FOUND`; delete of a missing key resolves `true`; tier on simulator = `NONE`.
- Reinstall purge: with the marker absent, pre-existing items of the service are removed; other
  Keychain services (e.g. `react-native-keychain` entries) are **untouched** (collateral invariance).

**Server-format parity:** byte-level parity with the JDK verifier is covered by the SPKI-prefix,
DER and low-S tests above; acceptance by the real Auth API (`SignatureService`) is the step-5 exit
gate, not this one.

**JS layer:** `yarn test`, `yarn typecheck`, `yarn lint` stay green (no JS contract change expected).

**App-level (simulator, Maestro):** home → **+** → F2a seed → `bind` fails as before without a
backend (unchanged behavior). The About screen shows the build timestamp and git SHA.

**Device-only, explicitly NOT proven in this step:** Secure Enclave generation/signing, `STRONG`
tier, real Face ID / passcode prompts. Record them as device-required in `IOS_SETUP.md`.

## Exit gate

All XCTest cases green on the iOS 27 simulator; JS suite green; app builds and launches; docs
updated with conservative iOS wording; device-only items listed. Step 5 then proves bind/verify/
pending/respond against the local Docker Auth API.

## Main risks

- Silent wire-format drift (DER/SPKI/low-S) → covered by codec tests; final proof in step 5.
- Overclaiming Secure Enclave on simulator → tier derived from the stored kind tag, `NONE` on simulator.
- Keychain persistence across reinstall → install-marker purge, tested.
- Simulator-only proof hides SE-specific failures → listed as device-required; step 6 (real device).
