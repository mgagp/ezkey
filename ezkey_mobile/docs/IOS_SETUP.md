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
camera and no Secure Enclave, so QR capture and hardware-backed keys need a real device later.

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

## Known iOS gaps (expected during the rebuild)

- `EzkeyCryptoModule`: no iOS native implementation yet (Swift / Secure Enclave rewrite planned).
- `EzkeyPlayUpdateModule`: Google Play in-app updates, Android-only; TypeScript needs an iOS no-op path.
- QR capture untestable on the simulator; a dev-only QR injection path is planned.
