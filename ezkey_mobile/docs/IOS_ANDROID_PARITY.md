# Ezkey Mobile — iOS / Android parity checklist

Status of the iOS rebuild (issue #152, ADR-MOB-0005) against the Android reference app, area by
area. Written for phase 7 (`plans/mobile_ios_phase_7_auth_flow_parity_and_hardening.plan.md`),
recorded 2026-11-03. Parity is judged on **trust boundaries** (keys, secrets, signatures, wipe),
not on screens: all TypeScript (screens, flows, protocol, signed-payload checks) is shared, so
behavior differs only where the native layer or the OS differs.

Legend: **=** same behavior · **≈** equivalent by a different mechanism (reason given) · **—** not
applicable on this platform · **open** not proven yet.

## Core flow and protocol

| Area | Android | iOS | Status | Evidence |
|------|---------|-----|--------|----------|
| `bind` / `verify` / `pending` / `respond` | Shared TypeScript | Shared TypeScript | **=** | iOS simulator against the local stack, 2026-11-02: enroll (F2a seed), test authentication request, Approve ([IOS_SETUP.md](IOS_SETUP.md) § step 5) |
| Signature format (SHA256withECDSA, DER, low-S) | Kotlin | Swift / CryptoKit, normalized to low-S | **=** | XCTest `EzkeyCryptoCodecTests`; the Auth API rejects high-S, and the e2e run passed |
| Signed-result trust checks (Ed25519 verify, fail-closed algorithm checks, ADR-MOB-0003) | Shared TypeScript + native `verify` | Shared TypeScript + native `verify` | **=** | XCTest bridge tests (`EZK_*` codes), Jest |
| Proof token (CSPRNG) | `SecureRandom` | `SecRandomCopyBytes` | **=** | Same wire format, XCTest |
| Leave-as-deny, pending TTL, no background polling (ADR-MOB-0001) | Shared TypeScript | Shared TypeScript | **=** | Jest (FSM) |

## Keys and local secrets

| Area | Android | iOS | Status | Evidence |
|------|---------|-----|--------|----------|
| Enrollment key (EC P-256, one per enrollment) | Android Keystore, StrongBox when available | Keychain item holding a Secure Enclave key (device) or a software key (simulator) | **≈** | XCTest key lifecycle |
| Storage tier reported to the Auth API | `STRONG` (StrongBox) / `STANDARD` (TEE) / `NONE` | `STRONG` (Secure Enclave) / `NONE` (simulator, by policy) | **≈** | iOS has no TEE-without-SE tier; a simulator never claims `STRONG` |
| Local authentication before signing | BiometricPrompt | `LAContext` `.deviceOwnerAuthentication` | **≈** / **open** on device | Simulator only (Face ID simulation); real prompt needs step 6 |
| Long-lived secrets (proof token, integration public key) | AES/GCM seal key per installation, ciphertext in AsyncStorage (ADR-MOB-0006, MOB-017) | One Keychain item per secret per enrollment, `AfterFirstUnlockThisDeviceOnly` (ADR-MOB-0007) | **≈** | Isolation is per enrollment on iOS (the item name contains the installation-scoped local enrollment id), finer than per installation |
| Clear-all (Danger Zone) is a true local reset (MOB-015) | Deletes metadata, sealed payloads, every `ezkey_seal_*` key | Deletes metadata and every enrollment secret item in the Keychain, orphans included (prefix sweep) | **=** | Jest `enrollmentStorage` / `secureStorage` (sweep, fail-open) |
| Reinstall | The OS deletes the app's Keystore keys | Keychain survives uninstall: enrollment keys are purged before first use (fail-closed); orphaned secret items stay unreachable until the next clear-all | **≈** | XCTest purge probe (seed → uninstall → check) |

## Platform features

| Area | Android | iOS | Status | Note |
|------|---------|-----|--------|------|
| QR enrollment capture | VisionCamera + ML Kit | VisionCamera + AVFoundation object output | **≈** / **open** on device | The simulator has no camera; F2a bypass for tests |
| No-telemetry guard | CI: no `datatransport` in the APK (#1147, #1150) | CI: no telemetry pod, privacy manifests declare no tracking or collection (`ios-privacy-check.sh`) | **≈** | iOS does not link ML Kit |
| Open source notices | Maven + JS + C/C++ in `react-android` | CocoaPods (RN, Hermes) + JS + C/C++ in `ReactNativeDependencies` | **=** | #1149, #1153; Maestro `ios_licenses.yaml` |
| Appearance (Light / Dark / System default, #1156) | Per-app night mode (`UiModeManager`), kept by the system: splash, system dialogs and first frame match from the next cold start | Choice kept in `UserDefaults`, applied to the window by `SceneDelegate` before React Native starts; plain launch screen (`AppBackground` color, light / dark variants) | **≈** | #1188; XCTest `EzkeyAppearanceModuleTests`, Maestro `ios_appearance.yaml`. iOS limit: the launch screen follows the system appearance (no API applies an app's choice there), so an app on Light with the phone in dark shows a dark launch screen, then the light app with no flash |
| Build info on About | `BuildConfig` | Built Info.plist (`EzkeyBuildTimestamp`, `EzkeyGitShortSha`) | **=** | |
| In-app update prompt | Google Play flexible update | — | **—** | The App Store updates apps itself; TypeScript no-ops |
| 16 KB page-size alignment | CI check | — | **—** | Android-only constraint |
| OS floor | `androidOsSupport.ts` | iOS 15.5 | **≈** | [IOS_SETUP.md](IOS_SETUP.md) § platform support floor |

## Tests and CI

| Layer | Android | iOS |
|-------|---------|-----|
| Shared logic | Jest (`yarn validate`, Linux CI) | same |
| Native unit tests | JVM unit tests (`android-jvm-unit-tests`) | Hosted XCTest `EzkeyMobileTests` (`yarn ios:test`, CI `mobile-ios`) |
| Native lint | Detekt (curated pass) | swift-format + SwiftLint, strict (`yarn ios:lint`, CI) |
| UI flows | Maestro | Maestro (`back` and long texts behave differently, see [IOS_SETUP.md](IOS_SETUP.md)) |
| Real device | Documented churn and StrongBox checklists | **open** (step 6) |

## Follow-up backlog

**Blocks iPhone confidence (core):**

1. Step 6 on a physical iPhone (`plans/mobile_ios_phase_6_real_device_bind_verify.plan.md`):
   Secure Enclave key, tier `STRONG`, real Face ID / passcode prompt, QR capture, reinstall purge.

**Later (not core):**

- App Store publication: privacy "nutrition label" (counterpart of Play Data safety), signing,
  listing. Only when App Store distribution is in scope.
- Optional: sweep orphaned Keychain secrets at first launch after a reinstall, not only on
  clear-all. Low value: the items are unreachable without their metadata and enrollment key.
- `android:com.parse.bolts:bolts-tasks` has a license link but no bundled text (Android, #1149).
