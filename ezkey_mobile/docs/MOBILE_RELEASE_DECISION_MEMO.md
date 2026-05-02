# Ezkey Mobile — Release Decision Memo

Decision memo for the next product discussion about Android Play Store release timing versus React Native stack upgrade timing.

## Purpose

This note does not replace the technical audit. It frames the decision that remains once the current workspace state is understood.

## Current Workspace Facts

These points are based on the current workspace, not on previously deleted upgrade-planning notes.

- The current mobile manifest is based on React Native `0.85.2` and React `19.2.3` in `ezkey_mobile/package.json`.
- The Android app is configured with `compileSdkVersion = 36` and `targetSdkVersion = 36` in `ezkey_mobile/android/build.gradle`.
- The Android release path already supports signed release artifacts via external `EZKEY_UPLOAD_*` Gradle properties in `ezkey_mobile/android/app/build.gradle`.
- The current package and Android version identifiers are not yet aligned: `package.json` uses `0.0.1`, while Android currently uses `versionName "1.0"` and `versionCode 1`.
- The app permission surface remains narrow in `AndroidManifest.xml`: `INTERNET` and `CAMERA` only.

## Version-Convergence Constraint To Preserve

Team context for future sessions matters here: the current dependency set was not chosen randomly. It was the result of convergence work around compatibility between the React stack and the camera stack, especially `react-native-vision-camera` and its related dependencies.

Practical implication:

- future modernization should treat React, React Native, camera, and camera-adjacent runtime packages as a coupled upgrade slice,
- avoid partial upgrades that change the core React stack first and assume the camera stack will simply follow later,
- expect convergence work rather than a single mechanical package bump.

This does not mean the current stack should remain frozen. It means the upgrade path should explicitly budget for compatibility validation rather than pretending that the earlier convergence problem never existed.

## The Real Decision

The main release question is not Android target API compliance. The current Android configuration already looks compatible with a near-term Play submission.

The real question is whether the first public Play release should ship on a React Native stack that is already archived and therefore likely to create avoidable maintenance pressure soon after release.

## Option A — Release Soon On The Current Stack

When this option makes sense:

- the immediate goal is to publish a credible first Play listing quickly,
- the current Android release build is stable on real devices,
- the team accepts a clearly scheduled post-release React Native upgrade as technical debt.

Advantages:

- shorter path to first publication,
- Android target API posture already looks usable,
- release signing and AAB workflow are already documented,
- the mobile product scope is intentionally narrow, which reduces pre-release surface area.

Costs and risks:

- React Native `0.85.2` is healthier than the old archived baseline but still recent enough that upgrade drift should be managed intentionally,
- the current CLI is aligned on the stable `20.1.3` line in the workspace,
- dependency and ecosystem drift will continue immediately after release,
- a post-release upgrade could collide with bug-fix work from the first public listing.
- the later upgrade still needs to respect the known React/camera convergence constraint, so the debt is not just version age but also upgrade coordination complexity.

## Option B — Upgrade Before First Public Release

When this option makes sense:

- the team wants the first public listing to start from a still-supported stack,
- there is enough schedule margin to absorb a native and dependency upgrade safely,
- release confidence matters more than short-term publication speed.

Advantages:

- healthier maintenance posture after launch,
- lower chance of near-term ecosystem breakage around React Native tooling,
- easier to present the release as a stronger baseline rather than as a provisional build.

Costs and risks:

- larger scope before publication,
- potential churn in camera, native modules, build tooling, and validation,
- higher chance of delaying the first Play release.
- the React and camera stacks should be upgraded as a coordinated compatibility exercise rather than as isolated package bumps.

## Recommendation

Use a gated decision instead of a default ideological stance.

Choose **release now on the current stack** if all of the following are true:

- a signed release candidate works reliably on real target devices,
- the Play checklist items outside the runtime stack are completed,
- the team is willing to open and prioritize the React Native upgrade immediately after the first release.

Choose **upgrade before release** if any of the following are true:

- there is no urgent publication deadline,
- release confidence is not yet high on the current build,
- the team wants the first public listing to represent a more durable technical baseline.

## Practical Closure For This Documentation Phase

For the revised documentation corpus, the important closure point is simple:

- the current workspace should be documented as it actually is today,
- the upgrade decision should be treated as an explicit next-session engineering decision,
- the release discussion should use the companion audit document rather than deleted historical migration notes.

See [MOBILE_PLAY_RELEASE_READINESS_AUDIT.md](MOBILE_PLAY_RELEASE_READINESS_AUDIT.md) for the evidence-based release audit.
