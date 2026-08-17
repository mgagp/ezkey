# Ezkey Mobile — Play Release Readiness Audit

Focused audit for deciding whether Ezkey Mobile can move toward a Play Store release from the current workspace state.

**Last refreshed:** 2026-08-14 (official Play-release work order).

## Audit Scope

This audit is intentionally practical. It focuses on the current repository state rather than on deleted migration plans or hypothetical target stacks.

## Executive Summary

Status: **listing-close remaining; in-app official copy and version policy are in the workspace.**

The Android project has a Play-oriented foundation (`targetSdk` 36, `minSdk` 31, upload signing hooks, aligned `versionName`). Experimental in-app messaging is retired. Flexible Play in-app updates ship in release builds (fail-open). Remaining blockers are **Console assets and a final signed GA AAB**, not product-copy or stack-baseline indecision.

React Native **0.86.2** is the accepted first-listing baseline; 0.87 is post-listing debt. See [`MOBILE_RELEASE_DECISION_MEMO.md`](MOBILE_RELEASE_DECISION_MEMO.md).

## What Looks Ready

### Android target and build posture

- `compileSdkVersion = 36` / `targetSdkVersion = 36` / `minSdkVersion = 31` in `ezkey_mobile/android/build.gradle`
- Matches product floor (Android 12+; [`MOBILE_ANDROID_PLATFORM_SUPPORT.md`](MOBILE_ANDROID_PLATFORM_SUPPORT.md))
- Android Gradle Plugin `8.12.0` and Kotlin `2.1.20`

### Version identifiers

- `ezkey_mobile/package.json` `"version": "1.0.0"` — Gradle `versionName` is injected from this file
- Settings / About show `APP_VERSION` from the same source
- `versionCode` **2** in `android/app/build.gradle` (assumes experimental Play/`org.ezkey.mobile` used `1`; **confirm in Play Console App bundle explorer before upload**)
- Bump recipe: [`MOBILE_PLAY_PUBLISHING.md`](MOBILE_PLAY_PUBLISHING.md) § Version bump recipe

### Release signing path exists

- `EZKEY_UPLOAD_*` Gradle properties, documented in [`MOBILE_RELEASE_SIGNING.md`](MOBILE_RELEASE_SIGNING.md)
- A signed AAB has been built on this workstation (2026-08-14):
  `ezkey_mobile/android/app/build/outputs/bundle/release/app-release.aab` (~48 MB, gitignored).
  `jarsigner -verify` reported `jar verified` (upload key is self-signed, as expected; Play App
  Signing re-signs on upload). Matching release APK dumps `versionCode=2` `versionName=1.0.0`
  `minSdk=31` `targetSdk=36` label **Ezkey**.
- Wireless adb dropped before `adb install -r` of that APK. Re-run
  `./scripts/build-install-release-clean.sh` (or `adb install -r` of the APK) when the Pixel is
  connected for on-device smoke of Home / Release Notes / Settings / Coming Soon.

### Permission surface is narrow

`AndroidManifest.xml` currently declares:

- `android.permission.INTERNET`
- `android.permission.CAMERA`
- `android.permission.USE_BIOMETRIC` (optional local confirmation)

Data Safety draft: [`MOBILE_PLAY_DATA_SAFETY.md`](MOBILE_PLAY_DATA_SAFETY.md).

### Official in-app messaging

Home banner, Release Notes, and Settings no longer use invited-audience / activation-code copy
(`TB-2026-08-14-mobile-exit-experimental-messaging`). Coming Soon remains a sober roadmap.

### Monochrome adaptive icon

`mipmap-anydpi-v26/ic_launcher.xml` includes a `monochrome` drawable.

### Client update mechanism

Release builds include Play Core **flexible** in-app updates (`EzkeyPlayUpdateModule`). Debug and
sideload fail-open (no prompt). Hard min-version gate is out of scope.

## Findings That Still Need Action

### 2. On-device smoke of the 2026-08-14 candidate

A signed AAB and matching release APK exist on this workstation (see above). Wireless adb dropped
before install. Reconnect the Pixel and install (`adb install -r` of the APK, or
`./scripts/build-install-release-clean.sh`) then smoke Home, Release Notes, Settings, Coming Soon
in EN and FR.

### 3. Console listing assets

Privacy URL and support email are decided (`https://ezkey.org/privacy.html`, `support@ezkey.org`).
Still needed in Play Console: screenshots, feature graphic (1024×500), paste Data Safety and
What's new from [`MOBILE_PLAY_STORE_LISTING.md`](MOBILE_PLAY_STORE_LISTING.md).

### 4. Confirm `versionCode` in Play Console before upload

Workspace uses `2` based on the known experimental `versionCode` `1`. If Console already has a
higher code, bump before upload. License snapshot regenerated 2026-08-14 (`yarn license:app-data`).

## Release Recommendation Matrix

### Ready enough to continue toward release now

Yes, on the current RN 0.86.2 stack, once Console screenshots/feature graphic are uploaded and the
Pixel smoke of the 2026-08-14 candidate is done.

### Not yet ready to call “Play release complete”

Until screenshots/feature graphic are in Console and the signed candidate is smoked on a device.

## Suggested Go/No-Go Checklist

- [x] Upload key configured (workstation `~/.gradle`; not in git)
- [x] Signed GA AAB + matching release APK built 2026-08-14 (`versionCode` 2 / `1.0.0`)
- [ ] On-device smoke of that candidate (adb dropped before install; reconnect Pixel and install)
- [x] `package.json` version aligned with Android `versionName` (`1.0.0`)
- [x] `versionCode` selected for next public upload (`2`, confirm in Console)
- [x] Privacy-policy URL decided and published
- [x] Support contact decided and visible
- [x] Data Safety answers prepared from the real permission/data behavior
- [x] Product name for the Play listing finalized (**Ezkey**)
- [x] Explicit decision taken on release-now versus upgrade-first (**release now**, RN 0.86.2)
- [x] Confirm [`MOBILE_ANDROID_PLATFORM_SUPPORT.md`](MOBILE_ANDROID_PLATFORM_SUPPORT.md) (floor API 31)
- [x] In-app experimental messaging retired
- [x] Flexible Play in-app update check present in release builds
- [ ] Screenshots + feature graphic uploaded
- [ ] Store What's new pasted from [`MOBILE_PLAY_STORE_LISTING.md`](MOBILE_PLAY_STORE_LISTING.md)
