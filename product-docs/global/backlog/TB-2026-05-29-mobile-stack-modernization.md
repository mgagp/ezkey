# Tracer Bullet Brief — `TB-2026-05-29` Mobile stack modernization

## Metadata

- **ID:** `TB-2026-05-29-mobile-stack-modernization`
- **Status:** `done`
- **Posture:** `multi-pass` (one PR per ladder step; this TB tracks the full program)
- **Related idea:** `I-2026-05-29-mobile-stack-modernization` (`done`)
- **Method log:** `ML-2026-05-29-mobile-stack-modernization.md` — append after each PR/session
- **Lane:** `B` (materialized from plan incubation)
- **GitHub issue:** `#177`
- **Git branch:** `feature/177-i-2026-05-29-mobile-stack-modernization`
- **Incubation source:** `.cursor/plans/mobile_stack_upgrade_a92a7f09.plan.md`
- **Created at:** `2026-05-29`
- **Updated at:** `2026-05-31`

## Objective

Modernize the `ezkey_mobile` toolchain and native-adjacent dependencies so that:

1. GitHub Actions runs **`yarn validate`** (lint + typecheck + Jest) on every relevant mobile change.
2. The RN **0.85.x** line receives a safe **patch** bump (0.85.3 target) with coupled `@react-native/*` packages.
3. **Async Storage 3.x** and **Vision Camera 5.x (Nitro)** are validated on Android with release bundles and Maestro.
4. A **Lane E retrospective** captures methodology lessons in `ML-*` and optional `methodology/decisions/`.

## Why this is not a “simple dependency bump”

The May 2026 archived review (S01–S23) already upgraded navigation, HTTP, i18n, Gradle/Kotlin/SDK 36, and most
patch-level JS deps. Remaining items are **architectural**:

| Package | Current | Target | Blocker history |
|---------|---------|--------|-----------------|
| `react-native-vision-camera` | 4.7.2 | 5.x | Nitro modules; custom ML Kit frame processor; worklets stub |
| `@react-native-async-storage/async-storage` | 2.2.0 | 3.x | S08 rollback at Kotlin 2.0.21 — retry at 2.1.20 |
| GitHub CI | JVM crypto only | + `yarn validate` | Gap identified 2026-05-29 |

## Boundaries in scope

- `ezkey_mobile/package.json`, `yarn.lock`
- `.github/workflows/ezkey-mobile-unit-tests.yml` (or companion workflow)
- `ezkey_mobile/android/**` when native deps change
- `ezkey_mobile/.eslintrc.js`, `jest.config.js`, `tsconfig.json`
- `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md` (stack program status)
- `product-docs/global/backlog/**` artifacts for this program (not index on feature branch)

## Out of scope

- Full S01–S23 re-execution
- iOS `Podfile.lock` (macOS)
- OpenAPI spec hand-edits
- native-stack navigator migration

---

## PR ladder (recommended sequence)

Each row = **one reviewable PR** unless noted.

| Step | PR label | Track | Content |
|------|----------|-------|---------|
| **0** | `step-0-methodology` | — | I/TB/ML + stack status doc + Phase 0 baseline (this PR) |
| **1** | `step-1-ci-validate` | B | GitHub `yarn validate`; path filters; Node 20.19.4+ in CI |
| **2** | `step-2-node-engines` | B | `package.json` engines; README prerequisites |
| **3** | `step-3-rn-0853` | B | RN 0.85.3 + `@react-native/*` 0.85.3 slice — **first functional gate** (Pixel) |
| **4** | `step-4-eslint-9` | B | ESLint 9 migration (if preset green) |
| **5** | `step-5-async-storage-3` | A | Async Storage 3.x + storage tests + device smoke |
| **6** | `step-6-vision-camera-spike` | A | VC 5 + Nitro POC; QR on device |
| **7** | `step-7-vision-camera-complete` | A | Remove worklets stub if possible; docs; manual device smoke |
| **8** | `step-8-methodology-retro` | E | ML synthesis; decisions; I/TB closeout |

**Rule:** append **`ML-2026-05-29`** at end of each step (see method log template).

---

## Phase 0 — Baseline inventory

### Checklist

- [x] GitHub issue **#177** opened
- [x] Feature branch `feature/177-i-2026-05-29-mobile-stack-modernization` created
- [x] I/TB/ML artifacts on branch
- [x] Record baseline validation results below (2026-05-29 on feature branch)

### Baseline commands

```bash
cd ezkey_mobile
corepack yarn install --immutable
yarn validate
cd android && ./gradlew :app:testDebugUnitTest --no-daemon
```

### Baseline snapshot (workspace manifest)

| Item | Version / note |
|------|----------------|
| `react-native` | 0.85.3 |
| `react` / `react-test-renderer` | 19.2.3 |
| `@react-native-community/cli` | 20.1.3 |
| `react-native-vision-camera` | 5.0.11 + `react-native-vision-camera-barcode-scanner` 5.0.11 |
| `@react-native-async-storage/async-storage` | 3.1.1 |
| Node `engines` (manifest) | `>=20.19.4` |
| CI | `yarn validate:ci` + JVM crypto tests; `actions/cache@v5` |
| Jest suites | 26 test files / 172 tests |
| Gradle | 8.13 / AGP 8.12 / Kotlin 2.1.20 / SDK 36 |

### Phase 0 validation results

Recorded on branch `feature/177-i-2026-05-29-mobile-stack-modernization` (Windows workstation).

| Gate | Command | Result | Date |
|------|---------|--------|------|
| JS validate | `yarn validate` | **PASS** — lint 2 warnings (`no-void` in `PendingAuthScreen.tsx`); typecheck OK; **26 suites / 172 tests** pass | 2026-05-29 |
| JVM crypto | `:app:testDebugUnitTest` | **PASS** — BUILD SUCCESSFUL (~32s) | 2026-05-29 |

**Non-blocking observations:** `baseline-browser-mapping` stale-data warnings during lint/test; Jest console noise (`act(...)`, enrollment wizard logs); Gradle deprecation warnings for Gradle 9.

---

## Functional validation (Android device — Pixel 7 Pro)

**Policy:** stay on `feature/177-...` until unit + functional gates are complete. Prefer **functional smoke** after each step that touches runtime.

**Before first debug install** (release/side-loaded build may block `INSTALL_FAILED_UPDATE_INCOMPATIBLE`):

```bash
adb uninstall org.ezkey.mobile   # ignore error if absent
cd ezkey_mobile && ./scripts/install-debug-after-uninstall.sh
```

**Vision Camera 5.x:** use `react-native-vision-camera-barcode-scanner` (`useBarcodeScannerOutput`) on Android and iOS — not `useObjectOutput` (iOS-only). Temporary `EZKEY_DIAG_TEMP` strip removed at program close (2026-05-31).

### Maestro (out of scope for this program phase)

Do **not** invest in Maestro flows or pilot scripts during steps 1–7 on branch `#177`.

- The only existing pilot (`TB-2026-0002`) assumes **pre-enrolled** `ENROLLMENT_ID` — it does not cover enrollment/QR.
- That gap is intentional backlog for a **later session**; a failed or skipped Maestro run here is **not** a regression signal for stack upgrades.
- Gates for this branch: **`yarn validate`**, JVM tests, **manual** functional smoke (launch, enroll via QR, pending/respond).

---

## Validation gates (every Track A/B PR)

| Gate | When |
|------|------|
| `yarn validate` | All JS/TS changes |
| `./gradlew :app:testDebugUnitTest` | Native or crypto touch |
| `yarn android:bundle:release` (JDK 17) | Native dep bumps |
| `yarn license:check` + `yarn license:app-data` | Runtime `dependencies` change |
| Device smoke enroll + pending/respond | Track A/B (manual; see below) |
| Maestro `scripts/run-real-device-pilot-maestro.sh` | **Deferred** — see Maestro note |
| `yarn generate:api` | Only if Auth OpenAPI contract changes |

---

## Execution notes

_(Append per PR — short bullets; full methodology narrative goes to ML log.)_

### 2026-05-29 — step-0-methodology

- Opened GitHub **#177**, branch `feature/177-i-2026-05-29-mobile-stack-modernization`.
- Materialized I/TB/ML from plan incubation (Lane B).

### 2026-05-29 — steps 1–3 + TEMP diagnostics

- CI: `js-validate` job + `validate:ci`; Node **20.19.4** in workflow; `engines >=20.19.4`.
- TEMP: `EZKEY_DIAG_TEMP` log strip; RN **0.85.3** on Pixel.

### 2026-05-29 — step-3 functional validation (operator)

- **PASS** — manual enrollment (QR) + authentication on Pixel 7 Pro; `EZKEY_DIAG_TEMP` in logcat.

### 2026-05-29 — step-5 Async Storage 3.x — **PASS**

- Bump **2.2.0 → 3.0.2**; Gradle fix: `android/build.gradle` `local_repo` Maven for `storage-android:1.0.0` (S08 blocker was missing repo, not only Kotlin).
- Jest: mock path `@react-native-async-storage/async-storage/jest`; `transformIgnorePatterns` includes package (v3 mock is ESM).
- Gates: `yarn validate:ci` **PASS** (26 suites / 172 tests); `yarn license:check` + `license:app-data` updated.
- Device: `installDebug` on Pixel 7 Pro; operator smoke **PASS** (langue, préférences, auth).

### 2026-05-29 — step-6 Vision Camera 5.x — **PASS**

- **5.0.10** + Nitro stack + `react-native-vision-camera-barcode-scanner@5.0.11`; removed `react-native-worklets-core` and custom `EzkeyQrFrameProcessorPlugin`.
- Enrollment scanner: `useBarcodeScannerOutput` (ML Kit, Android + iOS). Spike `useObjectOutput` reverted — iOS-only, caused Android Error Boundary.
- Android: `newArchEnabled=true` for Nitro codegen; RNWorklets CMake stub removed.
- Gates: `yarn validate:ci` PASS; operator enrollment QR + auth **PASS** on Pixel 7 Pro.

### 2026-05-29 — step-7 closeout

- Worklets stub removed; barcode via official ML Kit package (no custom frame processor).
- TEMP diag (`EZKEY_DIAG_TEMP`) removed 2026-05-31.

### 2026-05-31 — step-4 ESLint 9

- `eslint` 9.28.0; flat config via `@react-native/eslint-config/flat` (`eslint.config.js`); TS/TSX only (`.js` ignored — `eslint-plugin-ft-flow` incompatible with ESLint 9); `yarn validate:ci` PASS.

### 2026-05-31 — step-8 methodology (Lane E)

- ML retrospective R1–R7 filled; I/TB marked `done`. Operator push when ready.

### 2026-05-31 — post-merge closeout (operator + agent)

- **Merged to `main`**; operator post-merge manual smoke **PASS** (all screens / flows).
- Conservative patch bumps (vision-camera, svg, zustand, react-query, nitro-modules, navigation native).
- Prudent runtime commit: axios, i18n, navigation stacks, screens, async-storage; separate Orval **8.14** + client regen.
- Canonical Android debug install: `resolve-android-jdk.sh`, `build-install-debug-clean.sh`, AGENTS + Cursor rule.
- Backlog index updated on `main` (per I scope rule).

---

## Methodology outcomes (Step 8 — Lane E)

See `ML-2026-05-29-mobile-stack-modernization.md` retrospective synthesis. Highlights:

- Plan incubation + I/TB/ML + issue `#177` worked; archived S01–S23 review prevented duplicate work.
- **`yarn validate:ci`** caught Jest/ESM issues; **device smoke** caught iOS-only `useObjectOutput` on Android.
- Maestro intentionally deferred (enrollment/QR not in pilot harness).
- Promote: document `newArchEnabled=true` for Nitro codegen on RN 0.82+ when third-party libs still gate on it.

---

## Related

- [`I-2026-05-29-mobile-stack-modernization.md`](ideas/I-2026-05-29-mobile-stack-modernization.md)
- [`ML-2026-05-29-mobile-stack-modernization.md`](method-logs/ML-2026-05-29-mobile-stack-modernization.md)
- [`TB-2026-0002`](TB-2026-0002-android-real-device-functional-pilot.md) — Maestro pilot
- Prior May 2026 dependency review: [`ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`](../../../ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md) (do not redo)
