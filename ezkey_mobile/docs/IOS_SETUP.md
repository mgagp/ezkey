# Ezkey Mobile — iOS setup and baseline

iOS rebuild per ADR-MOB-0005 (issue #152, `TB-2026-0004`): the `ios/` folder is a clean React
Native 0.88.1 template scaffold; all TypeScript is shared with Android, only the iOS native layer is
rewritten. Android remains the reference implementation.

## Baseline (recorded 2026-11-01)

| Item | Value |
|------|-------|
| React Native | 0.88.1 (`ios/` regenerated from the 0.88.1 template: Swift `AppDelegate` + `SceneDelegate`) |
| Xcode | 27.0 (27A266a), iOS Simulator SDK 27.0 |
| Minimum iOS | **15.5** (see *Platform support floor*) |
| Bundle identifier | `org.ezkey.mobile` (same as Android `applicationId`) |
| Display name | `Ezkey` |
| Ruby | Homebrew Ruby 4.0 (macOS system Ruby 2.6 is too old) |
| CocoaPods | 1.17.0, pinned by `Gemfile.lock`, installed into `vendor/bundle` |
| Node / Yarn | Node ≥ 24, Yarn 4.10.3 (Corepack) |
| Watchman | Homebrew |

## First-time setup (macOS, Apple Silicon)

```bash
brew install ruby watchman
# Homebrew Ruby is keg-only: put it ahead of system Ruby (e.g. in ~/.zprofile)
export PATH="/opt/homebrew/opt/ruby/bin:/opt/homebrew/lib/ruby/gems/4.0.0/bin:$PATH"

cd ezkey_mobile
yarn install
bundle install                 # CocoaPods into vendor/bundle
cd ios && bundle exec pod install && cd ..
```

## Run on the simulator

```bash
cd ezkey_mobile
yarn start                     # Metro, in its own terminal
yarn ios                       # or open ios/EzkeyMobile.xcworkspace in Xcode (never the .xcodeproj)
```

No physical iPhone / Apple Developer account yet: iOS work is simulator-only. The simulator has no
camera, so QR capture needs a real device later. The iOS 27 simulator on Apple Silicon *does* expose a
working Secure Enclave, but the app deliberately uses a software key there (see *Native crypto*).

## Platform support floor

**iOS 15.5+.** Originally forced by the ML Kit barcode scanner; kept after ML Kit was dropped on iOS
(below) because it costs nothing, matches the 2021 era of the Android 12 floor
([`MOBILE_ANDROID_PLATFORM_SUPPORT.md`](MOBILE_ANDROID_PLATFORM_SUPPORT.md)), and absorbs future
library floors. Set in `ios/Podfile` (`platform :ios`) and the Xcode project
(`IPHONEOS_DEPLOYMENT_TARGET`). The Podfile `post_install` raises any pod below 15.5 to 15.5,
because Xcode 27 rejects targets below iOS 15.0 (e.g. AsyncStorage's resource bundle declares 13.0).
Review alongside the Android floor (annual, next 2027-07).

## Enrollment QR decoding: native AVFoundation on iOS

`react-native-vision-camera-barcode-scanner` depends on Google ML Kit, which ships no arm64
iOS-simulator slice (its pods force `EXCLUDED_ARCHS[sdk=iphonesimulator*] = arm64`), so the app
cannot build for Apple Silicon simulators. iOS therefore:

- does **not** link the ML Kit scanner (`react-native.config.js` → `platforms.ios: null`);
- decodes codes with VisionCamera's native object output (`useObjectOutput`, AVFoundation).

The split lives in one hook, resolved per platform by Metro:

| File | Platform | Backend |
|------|----------|---------|
| `app/components/enrollmentCodeOutput.android.ts` | Android | ML Kit (`useBarcodeScannerOutput`) |
| `app/components/enrollmentCodeOutput.ios.ts` | iOS | AVFoundation (`useObjectOutput`) |
| `app/components/enrollmentCodeOutput.types.ts` / `.d.ts` | both | shared contract / TypeScript entry |

Both accept the same QR-first format set (QR, Aztec, Data Matrix, PDF417, Code 128).

## Enrollment without a camera (simulator, debug only)

The simulator has no camera. Use the existing **F2a controlled enrollment seed bypass** (same as the
Android Maestro harness; see `docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`). It is gated by all of:

- native debug build: iOS `EzkeyCryptoModule` exports `isDebugBuild` from the Swift `DEBUG` condition
  (Xcode Debug configuration only; Release reports `false`);
- `.env`: `EZKEY_ENROLLMENT_SEED_BYPASS_ENABLED=true` and `EZKEY_ENROLLMENT_SEED_BYPASS_ACK=F2A_TEST_ONLY`
  (copy `.env.example` to `.env`; rebuild the native app after any change — `react-native-config`
  reads `.env` at build time).

Then: **+** → paste `enrollmentId|proofToken|authUrl` (or the JSON QR payload) → *Use controlled
test seed*. Bind/verify trust checks still run.

**Local backend:** the iOS simulator shares the Mac network stack, so `http://localhost:8080`
reaches the local Docker stack directly (Android emulator needs `10.0.2.2`). Plain HTTP to localhost
is allowed by ATS (`NSAllowsLocalNetworking`). No ngrok: use localhost.

**Maestro on iOS:** `brew install mobile-dev-inc/tap/maestro`; existing flows using `ezkey.e2e.*`
test IDs (e.g. `maestro/flows/pilot_enrollment_seed_bypass_runtime.yaml`) run on the booted
simulator with `maestro --device <simulator-udid> test <flow>`. Maestro's `hideKeyboard` fails on
iOS: replace it with a tap on a neutral area (e.g. `- tapOn: { point: "50%,8%" }`, the header).
`maestro/flows/ios_licenses.yaml` (no backend needed) walks Settings → Open Source Licenses and
opens two C/C++ entries; Maestro's `back` does nothing on iOS, tap the header back button instead.

**End-to-end against the local stack (step 5, verified 2026-11-02):** create an enrollment in the
Admin UI (`ezkey-admin-ui/start.sh`, http://localhost:3090), enroll the simulator with the F2a seed
`<enrollmentId>|<proofToken>|http://localhost:8080` plus the challenge, then trigger a test
authentication request from the enrollment page and approve it in the app (enrollment card → check
pending → Approve). Pending requests expire after 2 minutes and are not pushed to the home screen.

## Native crypto (`EzkeyCryptoModule`, step 4)

Swift + CryptoKit in `ios/EzkeyMobile/Crypto/`, same methods, wire formats and `EZK_*` codes as
Android (plan: `plans/mobile_ios_phase_4_native_crypto_parity.plan.md`).

- **Keys:** one P-256 key per enrollment, Keychain item `org.ezkey.mobile.enrollment-key` /
  `ezkey_enrollment_<id>`, `WhenUnlockedThisDeviceOnly`. Device → Secure Enclave (tier `STRONG`);
  **simulator → software key (tier `NONE`) by policy**, even though the simulator reports a Secure
  Enclave, so a simulator never claims `STRONG`.
- **Reinstall purge:** Keychain items survive uninstall. Before the first key operation of a process,
  if the UserDefaults marker `ezkeyCryptoInstallMarker` is absent, every enrollment key is deleted;
  until that succeeds, key operations fail closed.
- **Signatures:** SHA256withECDSA, normalized to low-S (the Auth API rejects high-S), DER via
  CryptoKit `derRepresentation`.
- **Build info:** Run Script phase *Ezkey build info* (last phase) writes `EzkeyBuildTimestamp` /
  `EzkeyGitShortSha` into the **built** Info.plist (shown on the About screen). The Info.plist is
  declared as an input only: declaring it as an output collides with Xcode's own Info.plist step
  ("Multiple commands produce"). Script sandboxing is off for this target.
- **Local authentication:** `LAContext` `.deviceOwnerAuthentication` as a gate before signing, like
  Android BiometricPrompt. On the simulator, Features › Face ID › Enrolled / Matching Face, or
  `xcrun simctl spawn <udid> notifyutil -p com.apple.BiometricKit_Sim.pearl.match` (`.nomatch`).
  Note: the simulator reports device-owner authentication as available even without Face ID.

### Native tests (XCTest)

Hosted target `EzkeyMobileTests` (Podfile: `inherit! :search_paths` — `:complete` would link the pods
twice and crash the host app on duplicate Nitro registrations). Metro is not needed.

```bash
cd ezkey_mobile
yarn ios:test     # newest iOS runtime, first iPhone; IOS_SIM_DESTINATION overrides
```

`yarn ios:test` (`scripts/ios-test.sh`) is the command CI runs. The equivalent raw call:

```bash
cd ezkey_mobile/ios
xcodebuild test -workspace EzkeyMobile.xcworkspace -scheme EzkeyMobile \
  -destination 'platform=iOS Simulator,name=iPhone 18 Pro' -derivedDataPath /tmp/ezkey-dd
```

Reinstall-purge probe on the real Keychain service (skipped by default):

```bash
P='-only-testing:EzkeyMobileTests/EzkeyReinstallPurgeProbeTests'
TEST_RUNNER_EZKEY_PURGE_PROBE=seed  xcodebuild test <same args> $P
xcrun simctl uninstall booted org.ezkey.mobile
TEST_RUNNER_EZKEY_PURGE_PROBE=check xcodebuild test <same args> $P
```

### Swift code standards

Same bar as Java (google-java-format + Checkstyle), with the community-standard Swift tools:

| Tool | Role | Config |
|------|------|--------|
| **swift-format** (ships with Xcode) | Formatter and style lint; Apple's tool, defaults follow the Google Swift style guide (2-space indent, 100 columns) | `ios/.swift-format` (pinned defaults) |
| **SwiftLint** (`brew install swiftlint`) | Code-smell rules: naming, force unwrapping, XCTest messages, line length | `ios/.swiftlint.yml` (formatting rules that contradict swift-format are disabled) |

```bash
cd ezkey_mobile
yarn ios:lint                      # check (strict, non-zero exit on any finding)
bash scripts/ios-lint.sh --fix     # format in place, then check
```

Every Swift file starts with the repo-standard header (`Ezkey - Open Source Cryptographic MFA
Platform`, copyright, MIT line, then module and description), mirroring the Kotlin files. Use `///`
doc comments on types and non-trivial functions.

### CI (macOS runner)

`.github/workflows/ezkey-mobile-ios.yml` (job `mobile-ios`, part of `ci-gate`) runs on `macos-26`:
`yarn ios:lint`, `pod install`, two guards, then `yarn ios:test`. The guards: `ios/Podfile.lock`
must be unchanged by `pod install` (it feeds the in-app iOS license list, #1148), and
`scripts/ios-privacy-check.sh` fails on a known telemetry pod or a privacy manifest declaring
tracking or collected data (iOS counterpart of the Android datatransport guard, #1147). It triggers only on the iOS native surface (`ios/**`,
`package.json`, `yarn.lock`, `Gemfile*`, `scripts/ios-*.sh`), its own workflow or `ci.yml`; JS-only
changes stay on the Linux jobs (a Debug simulator build does not bundle JS). It runs on pull
requests and nightly on `main`, not on the post-merge push (~22 min, of which ~18 build). The repository is public, so
standard macOS runner minutes are free.

- **Toolchain gap:** the runner uses its default Xcode 26.x and an iOS 26 simulator, while local
  development uses Xcode 27 / iOS 27 (the Xcode 27 runner image is still preview). CI therefore also
  proves the code builds on the previous Xcode. If swift-format behaves differently between the two,
  the CI result is the reference.
- **SwiftLint** is pinned in the workflow (version + SHA-256). Bump it there and locally together.
- On failure the `.xcresult` bundle is uploaded as an artifact (open it in Xcode).

### Editing the Xcode project

Change `project.pbxproj` and the shared scheme **only through the `xcodeproj` Ruby gem** (installed
with CocoaPods in `vendor/bundle`; run scripts with `bundle exec ruby` from `ezkey_mobile/`), never by
hand. It is how the Crypto group, the test target and the Run Script phase were added.

## Known iOS gaps (expected during the rebuild)

- **Device-only, not yet proven:** Secure Enclave key generation and signing, tier `STRONG`, real
  Face ID / passcode prompts. Needs a physical iPhone (step 6).
- **Orphaned secrets after reinstall:** `react-native-keychain` items (enrollment proof token,
  integration key) also survive uninstall while their AsyncStorage metadata does not. Unreachable;
  removed by the next Danger Zone clear-all, which sweeps every enrollment secret item (ADR-MOB-0007).
- `sealSecret` / `unsealSecret` / `deleteAllSealKeys`: Android-only by design (iOS keeps secrets as
  Keychain items, ADR-MOB-0007); JS fallbacks apply.

Area-by-area status against Android: [IOS_ANDROID_PARITY.md](IOS_ANDROID_PARITY.md).
- `EzkeyPlayUpdateModule`: Google Play in-app updates, Android-only; TypeScript already no-ops on iOS.
- QR capture untestable on the simulator; use the F2a bypass above.
