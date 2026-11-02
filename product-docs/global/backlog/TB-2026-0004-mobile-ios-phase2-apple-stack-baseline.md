# Tracer Bullet Brief — `TB-2026-0004` iOS rebuild — Apple stack baseline and clean scaffold

## Metadata

- **ID:** `TB-2026-0004`
- **Status:** `in-progress`
- **Related idea:** `I-2026-0027`
- **Lane:** `A`
- **GitHub issue:** `#152`
- **GitHub branch:** `feature/152-mobile-ios`
- **GitHub PR:** _(à créer)_
- **Created at:** `2026-05-24`
- **Updated at:** `2026-11-01`

## Objective

Establish the clean iOS starting point and the verified Apple toolchain baseline so that all subsequent iOS phases can proceed without stack-level uncertainty.

This tracer bullet covers what was originally Phase 1 + Phase 2 of the iOS plan. Phase 1 (audit and keep/rewrite decision) is resolved by ADR-MOB-0005: existing iOS-specific code is discarded. Phase 2 (Apple stack baseline) is the first real execution step.

## Boundaries in scope

- Delete or archive `ezkey_mobile/ios/` iOS-specific native content that predates the rebuild
- Confirm Xcode + CocoaPods version compatible with React Native 0.88.1
- Validate or adjust minimum iOS deployment target (default: 15.1)
- Regenerate clean iOS native scaffold via `npx react-native init` or equivalent RN CLI
- Verify a minimal iOS simulator build launches without stack-level errors
- Document the chosen baseline (Xcode version, min iOS target, CocoaPods version) in `ezkey_mobile/docs/`

## Out of scope

- Native crypto implementation
- Screen wiring and navigation
- QR path (camera or simulator injection)
- Any product feature beyond a blank shell that launches

## Critical flows

- **Nominal:** delete stale iOS code → scaffold clean iOS project → install pods → launch simulator → app shell appears
- **Replan trigger:** if RN 0.88.1 forces a minimum iOS target above 15.1, or if a required dependency (vision-camera, etc.) introduces toolchain incompatibility, document and flag before proceeding to Phase 3

## Evidence plan

- Simulator screenshot or run log showing the clean shell launching on iPhone simulator
- Updated `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md` — iOS baseline section
- New or updated `ezkey_mobile/docs/IOS_SETUP.md` — developer setup instructions (Xcode version, CocoaPods, simulator target)

## Quality gates

- **Analysis gate:** ADR-MOB-0005 accepted, R-2026-0003 integrated ✅
- **Design gate:** toolchain compatibility matrix confirmed during execution
- **Implementation gate:** clean simulator launch with no unresolved native dependency errors

## Execution notes (2026-11-01)

- Stale `ios/` deleted; regenerated from the RN 0.88.1 template (Swift `AppDelegate` + `SceneDelegate`),
  bundle id `org.ezkey.mobile`, display name `Ezkey`. Toolchain: Xcode 27.0, CocoaPods 1.17.0, Ruby 4.0.
- **Replan trigger fired:** the ML Kit barcode scanner requires iOS 15.5 and ships no arm64 simulator
  slice. Decisions (maintainer): minimum iOS **15.5**; iOS decodes enrollment QR with VisionCamera's
  native AVFoundation object output, ML Kit stays Android-only (platform hook `useEnrollmentCodeOutput`).
- Xcode 27 rejects pod targets below iOS 15.0 → Podfile `post_install` raises pods to 15.5.
- Evidence: simulator build succeeds; the app shell (home screen) launches on iPhone 18 Pro / iOS 27.
  Jest 324/324, `tsc` clean, lint 0 errors; Android and iOS release bundles both build.
- Baseline documented in `ezkey_mobile/docs/IOS_SETUP.md`.

## Execution notes — step 4, native crypto (2026-11-02)

- `EzkeyCryptoModule` implemented in Swift/CryptoKit per
  `plans/mobile_ios_phase_4_native_crypto_parity.plan.md` (stage-2 reviewed, D1–D6 decided).
- **Decision D2 revised (maintainer, 2026-11-01):** the iOS 27 simulator on Apple Silicon exposes a
  working Secure Enclave; the app still forces a software key (tier `NONE`) on the simulator so a
  simulator never reports `STRONG`. The Secure Enclave path is exercised by XCTest via injection.
- Evidence: 23/23 XCTest green; RED baseline captured (32 compile errors against the old module);
  Crypto API `/validate` accepts the iOS low-S signature and rejects its high-S twin; reinstall purge
  proven on the simulator (key survived uninstall, then purged; other service intact); Face ID pass
  (prompt, no-match + cancel → `EZK_AUTH_CANCELLED`, match → downgrade); About screen shows build
  SHA/timestamp. Jest 324/324, `tsc` clean, lint 0 errors.
- Not proven (device-only): Secure Enclave keys and real biometrics on a physical iPhone.

## Execution notes — step 5, real enrollment and approval on the simulator (2026-11-02)

- Local clean-start stack + Admin UI (`ezkey-admin-ui/start.sh`, port 3090). The maintainer created a
  demo integration ("Administration") with end user `jean.martin` and an enrollment (#2); the iOS
  simulator enrolled through the F2a seed (`2|<proofToken>|http://localhost:8080`) and challenge.
- Server evidence: enrollment #2 `VERIFIED` (Auth API: "Signature validation passed"), device public
  key stored as SPKI, `device_private_key_storage_tier = NONE` (simulator policy, D2).
- Approval: the maintainer triggered a test authentication request from the enrollment page (Admin UI)
  and approved it in the simulator → auth attempt #4 `ACCEPTED`, device proof token recorded, no
  Auth/Admin API errors. An earlier attempt (#3) expired after 2 minutes (pending requests are pulled
  from the enrollment detail screen, by design, same as Android).
- The app's native Ed25519 `verify` gates bind, verify-result and respond (integration signatures):
  all three passed against real server signatures.
- Maestro on iOS: `hideKeyboard` fails; the iOS run used a copy of `pilot_enrollment_full_runtime.yaml`
  that taps the header instead.
- With "Confirm before approvals" enabled: the app showed the Face ID prompt before sending; a
  simulated matching face let `signWithAuthentication` sign → auth attempt #5 `ACCEPTED`, app shows
  "Latest response: Approved", no Auth API errors.
- Still open: Secure Enclave and real biometrics on a physical iPhone (step 6).
