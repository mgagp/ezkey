# Mobile Dependency Hygiene Ceremony (Pragmatic)

Date: 2026-06-18
Scope: `ezkey_mobile`

## Intent

Define a lightweight, repeatable dependency-update routine that keeps momentum without adding ceremony overhead.

## Principles

- Keep updates incremental and isolated.
- Prefer low-risk value first.
- Defer ecosystem-blocked majors explicitly (do not force them).
- Maintain evidence for each lot (commands and outcomes).

## Minimal ceremony (5 steps)

1. **Monitor**
   - Run `yarn deps:monitor`.
   - Optionally archive run history with `yarn deps:monitor:history`.
   - Split result into:
     - actionable now
     - deferred by ecosystem gates

2. **Pick one lot only**
   - One dependency or one tightly coupled slice per lot.
   - Avoid mixed-risk bundles.

3. **Validate with the same ladder**
   - `yarn lint`
   - `yarn typecheck`
   - `yarn test --runInBand`
   - Android debug install path (`./scripts/build-install-debug-clean.sh`)
   - **Toolchain lot** (React, React Native, Gradle wrapper, Android Gradle Plugin, or Kotlin): also the cold release gate below. A warm debug or release APK does not satisfy it.

4. **Decide fast**
   - If green: commit and push.
   - If red: rollback immediately and capture reason.

5. **Update traceability**
   - Record the result in PR notes and relevant memo docs.
   - Use `MOBILE_DEPENDENCY_PR_TEMPLATE.md` for consistent lot reporting.
   - Keep deferred majors documented with explicit unblock conditions.

## Cadence recommendation

- Run the ceremony at least once per maintenance iteration.
- For active periods, prefer small weekly lots over large monthly batches.

## Deferred-major policy

Use deferred mode for majors blocked by current RN ecosystem alignment.

- ESLint 10: wait for RN lint-stack compatibility gate.
- Jest 30: wait for RN jest preset/runtime compatibility gate.

Reference: `MOBILE_ESLINT10_JEST30_UNBLOCK_MEMO.md`.

## Cold release gate (React / toolchain lots)

Trigger: any lot that changes React, React Native, the Gradle wrapper, Android Gradle Plugin, or the Kotlin pin. JS tests and a debug install are not enough.

`scripts/build-install-release-clean.sh` deletes `android/app/build`, `android/build`, and `.cxx`, then runs `assembleRelease`. It does **not** delete the included React Native Gradle plugin build under `node_modules/@react-native/gradle-plugin`. A previous successful compile stays `UP-TO-DATE`. That cache hid a real break on 2026-09-22: Pixel 7 Pro got `versionName` 1.0.0 / `versionCode` 2 from a warm `assembleRelease` (PR #601, React Native 0.87.1, Gradle 9.4.1, AGP 9.2.1), while a later cold `bundleRelease` failed. The plugin compiles with Kotlin 2.1.20; Gradle 9.4.1 ships Kotlin 2.3 stdlib metadata, which that compiler rejects. AGP 9.2.1 refuses Gradle older than 9.4.1, so the wrapper cannot simply be rolled back.

Before calling the lot green:

1. Delete `node_modules/@react-native/gradle-plugin/**/build` (the included plugin), then run `assembleRelease` and `bundleRelease` with `--no-daemon` and `--rerun-tasks` on that plugin compile if it still reports `UP-TO-DATE`.
2. Confirm both artifacts exist: release APK and `android/app/build/outputs/bundle/release/app-release.aab`.
3. Record `versionName`, `versionCode`, and the device install time in the PR. CI (`ezkey-mobile-unit-tests`) does not build either artifact.

Do not paper over the Kotlin metadata error with a Gradle init script that configures every task. That path finalizes the Android DSL too early and breaks library plugins (`finalizeDsl` on `react-native-config`, `react-native-gesture-handler`).

## Operational notes

- For test pass/fail truth in PowerShell, rely on raw `yarn test --runInBand` exit code.
- For Android Windows builds, run path preflight and follow short-root guidance when MAX_PATH risk is flagged.
