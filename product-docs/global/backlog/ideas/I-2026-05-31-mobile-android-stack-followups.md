# Backlog Idea — `I-2026-05-31` Mobile Android stack follow-ups (post-#177)

## Metadata

- **ID:** `I-2026-05-31-mobile-android-stack-followups`
- **Status:** `captured`
- **Priority:** `P2`
- **Created at:** `2026-05-31`
- **Updated at:** `2026-05-31`
- **Phase tags:** `P2-maintainability`, `toolchain`, `mobile-quality`, `android`
- **Component tags:** `mobile`, `android`
- **Lane:** `A` (promote per-slice `TB-*` when execution starts)
- **Spawned from:** `I-2026-05-29-mobile-stack-modernization` (GitHub **#177**, `done` 2026-05-31)
- **Captured by:** Marc (post-merge planning session after mobile stack closeout)

## Intent

Track **Android-only** follow-up work after the mobile stack modernization program (#177). The baseline
(RN 0.85.3, VC5+Nitro, AS3, CI `validate:ci`, ESLint 9, canonical debug build scripts) is **done**.
This idea groups remaining upgrades and hygiene so they can be scheduled slice-by-slice without reopening
#177 or mixing iOS / Play release scope.

## Problem and value

### Problem

- Residual dependency and tooling gaps were explicitly **deferred** at #177 closeout (majors, dev-tooling
  noise, Maestro enrollment gap).
- Without a single follow-up artifact, session findings (stack evaluation, agent build friction) risk
  scattering across chat and completed TB/ML notes.

### Expected value

- One place to **prioritize**, **check off**, and **promote to TB** when a slice is ready.
- Clear boundary: **Android app + Android CI/device validation** only.

## Scope

### In scope

- `ezkey_mobile/` Android dependencies, Gradle toolchain (when RN-aligned), JS dev tooling affecting mobile CI.
- Android device validation (`validate:ci`, `./scripts/build-install-debug-clean.sh`, manual Pixel smoke).
- Extension of **`TB-2026-0002`** for Maestro **enrollment / QR** on Android (not a duplicate Maestro program).

### Out of scope

- **iOS** (`I-2026-0027`, Podfile.lock, Xcode) — separate milestone.
- **Google Play release** — open a dedicated idea when targeting publication (`MOBILE_PLAY_*` docs).
- Re-running archived S01–S23 dependency review.
- Broad Maestro coverage of every screen (stay pilot-sized).

## Execution slices

Promote a row to its own **`TB-*`** (or extend **`TB-2026-0002`**) when starting a branch. Gates for
any runtime slice: `corepack yarn validate:ci`, `./scripts/build-install-debug-clean.sh`, operator smoke
on Pixel 7 Pro (or documented equivalent).

### Tier 1 — execute next

| ID | Slice | Target / note | TB when started | Status |
|----|-------|---------------|-----------------|--------|
| F1 | Maestro **enrollment + QR** (Android) | Extend `TB-2026-0002`; VC5 + `useBarcodeScannerOutput` baseline | `TB-2026-0002` (section added) | `pending` |
| F2 | **react-native-gesture-handler** 3.x | Major; navigation / gestures regression risk | `TB-2026-…-gesture-handler-3` (create) | `pending` |
| F3 | **react-native-nitro-image** 0.15.x | Minor Nitro; validate with native build | Optional sub-step of F2 or own TB | `pending` |

### Tier 2 — when convenient (tooling / hygiene)

| ID | Slice | Target / note | Status |
|----|-------|---------------|--------|
| H1 | **ESLint 10** + `@react-native/eslint-config` compat | Dev + CI lint path | `pending` |
| H2 | Jest **`act()` / React Query** timer noise | Local `validate:ci` exit code noise; tests pass | `pending` |
| H3 | **baseline-browser-mapping** devDep refresh | Lint/test warning only | `pending` |
| H4 | **lint-staged** 17.x | Dev tooling | `pending` |
| H5 | **@rnx-kit/third-party-notices** 3.x | License generation tooling | `pending` |

### Tier 3 — parked until next RN / Android toolchain line

Do **not** open TB until an explicit RN-line or AGP migration program is chosen.

| ID | Slice | Blocker |
|----|-------|---------|
| P1 | **React** 19.2.6+ | RN renderer exact-match (`react-native` line bump) |
| P2 | **Jest 30** / **@types/jest** 30 | `@react-native/jest-preset` on Jest 29 |
| P3 | **TypeScript 6** | Intentional stay on TS 5.9.x until RN preset moves |
| P4 | **AGP 9** / **Gradle 9** | Major Android migration; couple with RN upgrade planning |
| P5 | **react-native-gesture-handler** aside from F2 | Listed in F2 when ready; P5 reserved if F2 splits |

## Related artifacts

- [`I-2026-05-29-mobile-stack-modernization.md`](I-2026-05-29-mobile-stack-modernization.md) — closed program
- [`TB-2026-05-29-mobile-stack-modernization.md`](../TB-2026-05-29-mobile-stack-modernization.md)
- [`ML-2026-05-29-mobile-stack-modernization.md`](../method-logs/ML-2026-05-29-mobile-stack-modernization.md)
- [`TB-2026-0002-android-real-device-functional-pilot.md`](TB-2026-0002-android-real-device-functional-pilot.md) — F1 owner
- [`I-2026-0019-android-real-device-mobile-functional-tests.md`](I-2026-0019-android-real-device-mobile-functional-tests.md) — parent Maestro idea
- [`2026-05-31-mobile-android-build-jdk-resolution.md`](../../../methodology/decisions/2026-05-31-mobile-android-build-jdk-resolution.md)
- `ezkey_mobile/AGENTS.md` § Android debug build
- Archived dependency review: `.github/prompts/archived/2026-05/plan-mobileDependencyReview.prompt.md`

## Status transitions

- `captured` → `incubating` when the first Tier-1 slice is scheduled (issue + branch named).
- `incubating` → `active` when F1 or F2 TB execution starts.
- `active` → `done` when Tier 1 is complete and Tier 2/3 are explicitly deferred, parked, or closed item-by-item in this file.

## Grill Me (inline — 2026-05-31)

| # | Question | Answer / decision |
|---|----------|-------------------|
| G1 | One I or many I? | **One I** with tiered table; TB per execution slice. |
| G2 | iOS Podfile.lock here? | **No** — `I-2026-0027`. |
| G3 | Maestro new I? | **No** — extend **`TB-2026-0002`** (F1). |
| G4 | Index now? | **Yes** — `captured` entry for operator tracking. |
