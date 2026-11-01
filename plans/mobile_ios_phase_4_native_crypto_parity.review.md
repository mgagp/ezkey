# Critical review — iOS step 4 native crypto plan

- **Plan reviewed:** `plans/mobile_ios_phase_4_native_crypto_parity.plan.md` (status: draft, D1–D3 recorded)
- **Review frame:** `product-docs/templates/cold-agent-plan-review.prompt.md` (stage 2, cold-agent hardening)
- **Reference read:** `ezkey_mobile/android/app/src/main/java/org/ezkey/mobile/crypto/`
  (`EzkeyCryptoModule.kt`, `EcdsaLowS.kt`, `EcdsaDerCodec.kt`, `IntegrationKeyVerifier.kt`),
  JS contract `ezkey_mobile/app/services/crypto/nativeCrypto.ts`, and its JS callers
- **Date:** 2026-11-01 · Issue #152 · branch `feature/152-mobile-ios`
- **Protocol:** HITL — the plan file has **not** been edited. Amendments wait for the maintainer's
  answers (end of this file).

## Verdict

The plan is sound and well scoped. D1–D3 are coherent, the low-S, SPKI and kind-tag ideas are
right, and the out-of-scope list is honest. It is **not yet safe to hand to a cold agent**. Four
gaps would make an agent guess or stall: the reinstall purge, the build-info Run Script, the
XCTest target setup, and local authentication having no proof at all. There are also a few
simplifications.

## 1. Product intent and posture

- **The reinstall purge is justified, and more so than the plan says.** `localEnrollmentId` is
  deterministic: `deriveLocalEnrollmentId(installationId, serverEnrollmentId)`
  (`ezkey_mobile/app/hooks/useEnrollmentWizard.ts:222`). With the step-5 setup
  (`http://localhost:8080`, a clean-start stack, server ids restarting at 1), colliding ids are the
  normal case, not a theory. Say so in the plan; it makes the purge non-optional.
  - Side note, not this step: the same stale-key reuse can happen on Android after a stack
    clean-start without reinstalling, because the "exists → `true`" rule applies there too. One
    sentence plus an `I-*` pointer would be enough.
- **The purge does not cover what survives next to it.** The enrollment metadata lives in
  AsyncStorage (`enrollmentStorage.ts:170`), which is wiped on uninstall. The `react-native-keychain`
  secrets survive (`secureStorage.ts`, service `serviceFor(key)`). After a reinstall they are
  orphaned, which is harmless but leaves data behind. The plan's "keep react-native-keychain entries
  untouched" is the right call for this step, but say it is deliberate and record the orphan
  residue as a known iOS gap in `IOS_SETUP.md`.
- **The fail-closed scope is incomplete.** If the purge fails, only `generate` rejects. `sign`,
  `getPublicKey` and the tier call would then run on a key that may be stale.
  - **Amendment:** run the purge lazily, once per process, on a serial queue before any key
    operation. Set the marker only after a successful purge, treating `errSecItemNotFound` as
    success. Until it succeeds, every key operation fails with its own error code.
  - This also removes a race with "on module init". Legacy modules are created lazily, and with
    `requiresMainQueueSetup = false` that can happen on any thread.

## 2. Gaps that would make a cold agent stall or guess

1. **Flexible Base64 decoding is underspecified for Swift.** Java's decoders
   (`IntegrationKeyVerifier.decodeFlexibleBase64ToBytes`) accept unpadded input.
   `Data(base64Encoded:)` rejects missing padding, `-`/`_` and whitespace.
   - **Specify:** strip PEM headers and whitespace, map `-_` to `+/`, re-pad to a multiple of 4,
     then decode.
   - Note one accepted difference: mixed alphabets would decode on iOS but not in the JDK. That's
     acceptable because the result is still `false` on a bad signature.
2. **Don't hand-roll DER.** `P256.Signing.ECDSASignature(rawRepresentation:).derRepresentation`
   already produces minimal DER, and `init(derRepresentation:)` parses it. The plan's custom DER
   encoder and "round-trip encode/decode" test (which needs a decoder too) are accidental
   complexity.
   - Keep only `normalizeLowS(raw64) -> raw64` with the fixed-width compare/subtract helper. Its
     tests still prove minimal encoding, `0x00` padding and leading-zero handling, just on
     CryptoKit's output.
3. **The build-info Run Script is the most fragile item.** Writing into the built product's
   Info.plist needs:
   - the phase placed after Info.plist processing (Xcode signs last);
   - `$(TARGET_BUILD_DIR)/$(INFOPLIST_PATH)` declared as both input and output, or
     `ENABLE_USER_SCRIPT_SANDBOXING = NO` for the target;
   - "Based on dependency analysis" unchecked, so the timestamp refreshes on every build;
   - `/usr/libexec/PlistBuddy` used to write the keys;
   - the same rules as `ezkey_mobile/android/app/build.gradle:118-157`: format
     `yyyy-MM-dd'T'HH:mm:ss'Z'` in UTC, and the SHA only if it matches `[0-9a-f]{7,}` (cut to 7),
     else `unknown`.

   **Alternative to decide:** generate an xcconfig or Swift constant instead of patching the
   plist. I still lean towards the plist, but the agent needs the details above either way.
4. **Setting up the XCTest target isn't trivial.** `EzkeyMobileTests` doesn't exist (0 hits in
   `project.pbxproj`) and the Podfile has no test target. The agent needs:
   - the target created in the pbxproj with `TEST_HOST`/`BUNDLE_LOADER` set (hosted, so the
     Keychain works without error -34018);
   - a `target 'EzkeyMobileTests' do inherit! :complete end` block, then `pod install`;
   - the scheme's Test action enabled;
   - an exact command, for example:
     `xcodebuild test -workspace ios/EzkeyMobile.xcworkspace -scheme EzkeyMobile -destination 'platform=iOS Simulator,name=iPhone 18 Pro'`
     (iPhone 18 Pro is the booted simulator on Marc's Mac).

   Editing a pbxproj by hand is where cold agents fail. Allow a one-time HITL "create the target
   in Xcode" step, or name a tool for it.
5. **Make the Keychain parameters injectable.** The Keychain service and the `UserDefaults` suite
   must be passed in, so the purge test runs on its own service and suite. The host app's own
   module init also runs during tests.
6. **Write down the concurrency rules.**
   - Give the module a dedicated serial `methodQueue`.
   - If `SecItemAdd` returns `errSecDuplicateItem` (a concurrent generate), resolve `true`, which is
     the "exists" semantics.
   - Other `OSStatus` failures map to the operation's own code, for example
     `errSecInteractionNotAllowed` → `EZK_SIGN_ERROR`.
7. **Local authentication details are missing:**
   - **Order:** check availability, then check the key exists, then prompt, then sign. Never
     prompt for a missing key (this is Android's order, `EzkeyCryptoModule.kt:376-436`).
   - **`localizedReason`:** reuse the Android subtitle strings
     (`BIOMETRIC_PROMPT_SUBTITLE`, `SECURITY_DOWNGRADE_PROMPT_SUBTITLE`).
   - **Error mapping:** `LAError` cancel codes (`userCancel`, `systemCancel`, `appCancel`) →
     `EZK_AUTH_CANCELLED`; everything else (`passcodeNotSet`, `biometryNotAvailable`,
     `notInteractive`, …) → `EZK_AUTH_UNAVAILABLE`. These two codes matter because the JS branches
     on them in `usePendingAuth.ts:230` and `SecurityScreen.tsx:91`.
   - **Threading:** `evaluatePolicy` doesn't need the main thread. Drop "except LAContext UI".
8. **Documentation drift:**
   - The comment in `nativeCrypto.ts:31` says "iOS uses bundle mtime proxy", which contradicts the
     plan. Fix it; this is a JS-side change, so "no JS contract change" should read "comment only".
   - `docs/CRYPTO.md:24` and `MOBILE_DEVELOPER_GUIDE.md` (the tier derivation) are missing from the
     docs list.
   - `PrivacyInfo.xcprivacy` already declares UserDefaults `CA92.1`. Note it as checked.
9. **Set the module header and the `isDebugBuild` constant as invariants.**
   `EzkeyCryptoModule.swift`'s header still says "constants only" and needs updating.
   `constantsToExport`/`isDebugBuild` must stay byte-identical, because the F2a gate depends on it.

## 3. Proof ladder and collateral invariance

- **No RED baseline at the bridge level.** The current unit tests target helpers only, so they
  never touch argument conversion or promise error codes.
  - **Add** hosted XCTests that create `EzkeyCryptoModule` and call the `@objc` methods with
    resolve/reject closures.
  - Assert the codes: `EZK_KEY_NOT_FOUND` for public key, tier, sign and `signWithAuthentication`;
    `deleteKeyPair` resolves `true` on a missing key; `verify` resolves `false` and never rejects.
  - RED = these fail today, because the methods don't exist.
- **The "200 iterations exercise normalization" claim is not proven.** If CryptoKit already emits
  low-S, the loop never touches the normalization branch. Replace it with a deterministic
  malleability test:
  1. Sign.
  2. Flip `s` to `n − s` (now high-S).
  3. Assert CryptoKit still accepts the flipped signature.
  4. Normalize it.
  5. Assert it equals the original and still verifies.

  Keep the loop, but add one assertion: every output is low-S.
- **Add a cheap check against the real JDK verifier, as PR evidence, not code.** An XCTest prints
  a vector (SPKI, data, signature). A `jshell` snippet then verifies it with `SHA256withECDSA`, plus
  the `SignatureService` low-S check if it can be reached. That turns "byte-level parity" from
  inferred into observed before step 5. The Ed25519 direction is already covered by the RFC 8032
  vector.
- **Prove the purge for real, not just in a unit test.** On the simulator: `xcrun simctl
  uninstall`, reinstall, relaunch, and show the old item is gone (the simulator Keychain also
  survives uninstall). Pair it with an invariance check: an existing `react-native-keychain` item
  is still readable afterwards.
- **D3 has no proof at all, while the exit gate says "all green".** Add:
  - a unit test of the pure `LAError` → code mapping;
  - a short manual simulator pass: Features › Face ID › Enrolled, then Matching and Non-matching,
    through the Security screen toggle and the downgrade confirmation;
  - a check that `canUseProtectedSigning` returns `false` when nothing is enrolled.
- **Pre-existing path conditions to list:**
  - the enrollment wizard deletes the key after `verify` fails (`useEnrollmentWizard.ts`, the
    `deleteEnrollmentKeyPair` catch);
  - `enrollmentStorage.ts:192` (`deleteKeyPair` on removal);
  - the JS `deleteAllSealKeys` fallback returning `false`.

  For each, state the iOS behaviour after the change.
- **The app-level proof is weak.** Because `bind` fails without a backend, no live crypto runs in
  this step except the About screen. That's acceptable, since step 5 is the gate, but say it
  plainly in the exit gate.

## 4. Proposed amendments (pending approval)

| # | Amendment | Section |
|---|---|---|
| A1 | Lazy purge on a serial queue, fail-closed for every key operation, marker set only after success; note the deterministic id; injectable service and suite | Key storage |
| A2 | Drop the hand-written DER; use CryptoKit's DER + a raw low-S helper only | Signature encoding |
| A3 | Exact Base64 decoding steps | Scope / `verify` |
| A4 | Full Run Script spec (phase order, I/O, sandboxing, format, SHA regex) | Scope / build info |
| A5 | XCTest target recipe + exact `xcodebuild` command; optional HITL step for creating the target | Execution order 1 |
| A6 | Concurrency and `OSStatus` → error-code table | Error codes / Bridge |
| A7 | Local auth order, strings, `LAError` mapping | Scope / D3 |
| A8 | Proof: bridge-level RED tests, malleability test, `jshell` vector, simctl reinstall, LA mapping test + manual Face ID pass | Proof ladder |
| A9 | Docs: `nativeCrypto.ts:31` comment, `CRYPTO.md:24`, `MOBILE_DEVELOPER_GUIDE.md`, module header, orphan-secret gap | Execution order 5 |
| A10 | Invariants: `isDebugBuild` unchanged, Android untouched, JS suite green | Proof ladder |

## 5. Questions for the maintainer

1. **A2:** OK to drop the custom DER encoder in favour of CryptoKit's `derRepresentation`?
2. **A4:** keep the post-processed Info.plist, or switch to a generated xcconfig or Swift constant?
3. **A5:** may the agent edit the pbxproj by hand to create the test target, or do you prefer a
   one-time step where you create it in Xcode?
4. **A8:** do you want the `jshell` JDK check (evidence in the PR, nothing committed), or keep
   server parity strictly for step 5?

Answers (2026-11-01, Marc):

1. **A2 — yes.** CryptoKit `derRepresentation`, no custom encoder.
2. **A4 — recommendation: keep the plist Run Script** (D4 in the plan): an xcconfig is read before
   any build phase runs, so the stamp would lag one build. Accepted.
3. **A5 — recommendation: neither hand edit nor manual Xcode step; script it with the `xcodeproj`
   gem** already in `vendor/bundle` (D5). Accepted.
4. **A8 — recommendation: replace `jshell` with a POST to the local Crypto API `/validate`**, which
   runs the server's own `SignatureService.validateSignature` incl. low-S (D6). Accepted.

All amendments A1–A10 integrated in the plan on 2026-11-01.
