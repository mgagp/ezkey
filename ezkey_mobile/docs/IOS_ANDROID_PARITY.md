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
| QR enrollment capture | VisionCamera + ML Kit | VisionCamera + AVFoundation object output; `NSCameraUsageDescription` restored 2026-11-06 (lost in the rebuild, iOS ends an app that requests the camera without it) | **≈** / **open** on device | The simulator has no camera; F2a bypass for tests |
| No-telemetry guard | CI: no `datatransport` in the APK (#1147, #1150) | CI: no telemetry pod, privacy manifests declare no tracking or collection (`ios-privacy-check.sh`) | **≈** | iOS does not link ML Kit |
| App language (#1157) | Settings → App languages (Android 13+), `LocaleManager` | Settings → Ezkey → Language, `AppleLanguages` in the app's own defaults; declared in `CFBundleLocalizations` | **≈** | XCTest `EzkeyLocaleModuleTests`, Maestro `ios_language.yaml`, `ios_language_system_settings.yaml`. iOS relaunches the app after a change in Settings, hides the row on a phone with one language, and treats the phone's first language as no choice (the app then shows System default). Permission prompts (`fr.lproj/InfoPlist.strings`) follow an in-app change at the next launch. Time format seen on the simulator with a fr-CA phone: `09:02` (Android: `09 h 02`) |
| Screenshots on Approve / Deny (#1154) | Blocked (`FLAG_SECURE`) | **Not blocked**: iOS has no public API for this | **open** (decision) | The request is short-lived and the key signature carries the guarantee. iOS-only workaround not used: a secure text field layer, undocumented behavior |
| App switcher / Recents thumbnail on Approve / Deny (#1154) | Hidden (`FLAG_SECURE`) | Hidden: cover window shown when the scene deactivates | **≈** | Simulator, 2026-11-06: snapshot in `Library/SplashBoard` is blank on Approve / Deny, normal on other screens; XCTest `EzkeyWindowSecurityModuleTests` |
| Screen recording / mirroring on Approve / Deny (#1154) | Black in the recording, the user keeps the screen | Cover with a notice on the phone too; Approve and Deny are blocked until the capture stops | **≈** / **open** on device | XCTest only: the simulator does not report a capture (`UIScreen.isCaptured`). Real capture needs step 6 |
| Third-party overlays on Approve / Deny (#1154) | Hidden, obscured touches dropped | No third-party overlays on iOS | **—** | |
| Backup and device transfer of enrollment metadata (#1154) | Excluded (`data_extraction_rules.xml`) | AsyncStorage directory excluded from backup by the library's default; Keychain items are `ThisDeviceOnly` | **=** | Simulator, 2026-11-06: attribute `com_apple_backup_excludeItem` on `RCTAsyncLocalStorage_V1`. `Library/Preferences` is backed up: no secret there (install marker, language, appearance) |
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
   Secure Enclave key, tier `STRONG`, real Face ID / passcode prompt, QR capture, reinstall purge,
   cover during a real screen recording.

**Later (not core):**

- App Store publication: privacy "nutrition label" (counterpart of Play Data safety), signing,
  listing. Only when App Store distribution is in scope.
- Optional: sweep orphaned Keychain secrets at first launch after a reinstall, not only on
  clear-all. Low value: the items are unreachable without their metadata and enrollment key.
- `android:com.parse.bolts:bolts-tasks` has a license link but no bundled text (Android, #1149).
