# Ezkey Mobile — Play Release Readiness Audit

Focused audit for deciding whether Ezkey Mobile can move toward a Play Store release from the current workspace state.

## Audit Scope

This audit is intentionally practical. It focuses on the current repository state rather than on deleted migration plans or hypothetical target stacks.

## Executive Summary

Status: **partially ready, not yet release-complete**.

The Android project already has a credible Play-oriented foundation, especially around target API level and release signing hooks. The main unresolved items are release operations, listing/compliance inputs, and the product decision about whether React Native `0.85.2` is an acceptable first-public-release baseline.

## What Looks Ready

### Android target and build posture

- `compileSdkVersion = 36` in `ezkey_mobile/android/build.gradle`
- `targetSdkVersion = 36` in `ezkey_mobile/android/build.gradle`
- `minSdkVersion = 24` in `ezkey_mobile/android/build.gradle`
- Android Gradle Plugin `8.12.0` and Kotlin `2.1.20` are already configured in the workspace

Conclusion: the Android API posture does not look like the immediate blocker for Play submission.

### Release signing path exists

- `ezkey_mobile/android/app/build.gradle` already supports release signing from external `EZKEY_UPLOAD_*` Gradle properties
- fallback behavior is explicit and loud when credentials are missing
- `ezkey_mobile/docs/MOBILE_RELEASE_SIGNING.md` already documents the upload-key workflow

Conclusion: the release-signing process is designed and has now been exercised locally with a real signed AAB and device installation. Operational release readiness still depends on keeping credentials managed safely and repeating the same flow for the final publication candidate.

### Permission surface is narrow

`AndroidManifest.xml` currently declares only:

- `android.permission.INTERNET`
- `android.permission.CAMERA`

Conclusion: the permission profile is easier to justify in Play Data Safety than many mobile apps.

### Monochrome adaptive icon support exists

`ezkey_mobile/android/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` already includes a `monochrome` drawable.

Conclusion: the Android 13+ themed icon requirement is already covered.

## Findings That Still Need Action

### 1. Versioning is not aligned yet

Evidence:

- `ezkey_mobile/package.json` uses `"version": "0.0.1"`
- `ezkey_mobile/android/app/build.gradle` uses `versionCode 1`
- `ezkey_mobile/android/app/build.gradle` uses `versionName "1.0"`

Why it matters:

- release artifacts and in-app surfaced version information should tell the same story,
- Play uploads require disciplined `versionCode` progression,
- the current mismatch is small but signals that release versioning has not been finalized.

Required action:

- choose the first public version string,
- align `package.json` and Android `versionName`,
- set a release-ready `versionCode` policy.

### 2. Release signing is supported and now proven locally, but not yet operationally closed

Evidence:

- the build falls back to the debug keystore if `EZKEY_UPLOAD_*` properties are absent,
- the docs explicitly state that such an artifact cannot be uploaded to Play.
- on this workstation, the real `EZKEY_UPLOAD_*` properties are configured outside the repo,
- a signed release AAB was built successfully,
- the signed AAB was converted to device-specific APKs with `bundletool` and installed on a connected Pixel 7 Pro.

Why it matters:

- proving the local signed path reduces uncertainty around release tooling,
- but Play readiness still depends on repeatability, key management hygiene, and final candidate validation rather than a single successful local installation.

Required action:

- preserve and document the upload-key operational process,
- repeat the signed AAB build and device validation for the final publication candidate,
- keep the final Play upload artifact and its validation logs as release evidence.

### 3. Listing and compliance inputs are not evidenced in the workspace

Evidence in the current workspace is limited to the checklist in `MOBILE_PLAY_PUBLISHING.md`. There is no discovered mobile-specific privacy-policy URL, support-contact artifact, or completed Data Safety mapping in the mobile corpus.

Why it matters:

- Play publication is blocked as much by listing/compliance inputs as by code,
- these items tend to slip if they are treated as external paperwork rather than release deliverables.

Required action:

- define the public privacy-policy URL,
- define the support contact used in the listing,
- complete the Play Data Safety answers from the real app behavior.

### 4. Product naming still needs an explicit release decision

Evidence:

- `ezkey_mobile/app.json` currently uses `displayName: "Ezkey"`
- the publishing checklist uses `Ezkey Authenticator` only as an example, not as the chosen final name.

Why it matters:

- the Play listing, launcher name, and brand positioning should be intentional and consistent.

Required action:

- choose the release name for the Play listing,
- decide whether launcher and listing names stay identical.

### 5. The stack is publishable in principle, but not ideal as a long-lived baseline

Evidence:

- `ezkey_mobile/package.json` uses React Native `0.85.2`
- the same file uses React `19.2.3`
- the React Native CLI dependencies are aligned on `20.1.3`
- the current runtime also depends on `react-native-vision-camera` `4.7.2` and `react-native-worklets-core` `1.6.3`

Why it matters:

- this is not the same as a Play API-level blocker,
- but it is a meaningful maintainability and support-risk discussion for the first public release.
- team context also indicates that the present versions were reached after real compatibility churn between the React stack and the camera stack, so future upgrades should not assume independent version movement.

Required action:

- make an explicit product/engineering decision: release now and upgrade later, or upgrade before first public launch.
- when planning modernization, treat React, React Native, Vision Camera, and related camera/runtime packages as a single convergence workstream with explicit validation time.

See [MOBILE_RELEASE_DECISION_MEMO.md](MOBILE_RELEASE_DECISION_MEMO.md).

## Release Recommendation Matrix

### Ready enough to continue toward release now

The project looks ready enough to continue toward a release candidate if the team completes:

- release signing setup,
- aligned versioning,
- privacy policy and support contact,
- Data Safety answers,
- release-mode device validation.

### Not yet ready to call “Play release complete”

The project is not yet ready to declare Play release readiness complete because:

- operational release inputs are still incomplete,
- publication metadata is not yet evidenced,
- the stack-baseline decision is still open.

## Suggested Go/No-Go Checklist

- [ ] Upload key configured and verified with a signed AAB
- [ ] Release-mode device test passed on the AAB path
- [ ] `package.json` version aligned with Android `versionName`
- [ ] `versionCode` selected for first public upload
- [ ] Privacy-policy URL decided and published
- [ ] Support contact decided and visible
- [ ] Data Safety answers prepared from the real permission/data behavior
- [ ] Product name for the Play listing finalized
- [ ] Explicit decision taken on release-now versus upgrade-first
