# Mobile Memo - ESLint 10 and Jest 30 Unblock Conditions

Date: 2026-06-18
Scope: `ezkey_mobile`

## Purpose

Keep dependency hygiene disciplined while avoiding premature major upgrades that are not yet supported by the current React Native line.

## Current baseline (validated)

- React Native: `0.86.0`
- `@react-native/eslint-config`: `0.86.0`
- `@react-native/jest-preset`: `0.86.0`
- ESLint: `9.39.4`
- Jest: `29.7.0`

## Why ESLint 10 is deferred

- Observed failure during trial:
  - `eslint-comments/no-aggregating-enable`: `context.getSourceCode is not a function`
- Root cause:
  - The RN lint stack is not fully aligned with ESLint 10 yet.
  - `@react-native/eslint-config@0.86.0` peer range is `^8 || ^9`, not `^10`.

## Why Jest 30 is deferred

- Observed failure during trial:
  - `TypeError: this._moduleMocker.clearMocksOnScope is not a function`
- Root cause:
  - The RN preset and its Jest runtime components are pinned to the 29.x ecosystem.
  - `@react-native/jest-preset@0.86.0` depends on Jest packages in `^29.7.0`.

## Unblock gates (all must pass)

1. RN ecosystem gate:
   - A React Native version used by this project explicitly supports ESLint 10 and/or Jest 30.
2. Peer dependency gate:
   - `@react-native/eslint-config` peer range includes ESLint 10.
   - `@react-native/jest-preset` and related runtime packages align with Jest 30.
3. Trial gate (isolated lot):
   - Upgrade one major at a time.
   - If first critical command fails, immediate rollback.
4. Validation gate:
   - `yarn lint`
   - `yarn typecheck`
   - `yarn test --runInBand`
   - Android `installDebug` on device with JDK 17.

## Trial protocol (recommended)

1. Create isolated branch/lot.
2. Upgrade only one major target (`eslint` or `jest`).
3. Run validation ladder in order:
   - lint -> typecheck -> tests -> installDebug.
4. On failure:
   - Revert package and lockfile immediately.
   - Keep baseline stable.
5. On success:
   - Commit isolated lot with explicit validation evidence.

## Known Windows caveat during Android validation

- In this workspace path, `gradlew installDebug` can fail with CMake/Ninja path length (`MAX_PATH`) for native modules.
- This is infrastructure-path related and can be independent from JS dependency quality.
- Prefer a short-root workspace path for Android native validation runs.

## Monitoring checklist

- Watch RN release notes for lint/test tooling alignment.
- Re-check these files before retry:
  - `node_modules/@react-native/eslint-config/package.json`
  - `node_modules/@react-native/jest-preset/package.json`
- Retry majors only when gates above are satisfied.

## Test command reliability note (PowerShell)

- Prefer running `yarn test --runInBand` directly for pass/fail truth.
- If a summarized view is needed, do not rely on a filtering pipeline for the final exit code.
- Recommendation:
   1. Run the raw command first and capture its exit code.
   2. Then parse or filter logs as a second, non-blocking step.
