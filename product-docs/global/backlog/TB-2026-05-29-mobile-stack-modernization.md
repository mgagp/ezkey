# Tracer Bullet Brief — `TB-2026-05-29` Mobile stack modernization

## Metadata

- **ID:** `TB-2026-05-29-mobile-stack-modernization`
- **Status:** `active`
- **Posture:** `multi-pass` (one PR per ladder step; this TB tracks the full program)
- **Related idea:** `I-2026-05-29-mobile-stack-modernization` (`incubating`)
- **Method log:** `ML-2026-05-29-mobile-stack-modernization.md` — append after each PR/session
- **Lane:** `B` (materialized from plan incubation)
- **GitHub issue:** `#177`
- **Git branch:** `feature/177-i-2026-05-29-mobile-stack-modernization`
- **Incubation source:** `.cursor/plans/mobile_stack_upgrade_a92a7f09.plan.md`
- **Created at:** `2026-05-29`
- **Updated at:** `2026-05-29`

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
| **3** | `step-3-rn-0853` | B | RN 0.85.3 + `@react-native/*` 0.85.3 slice |
| **4** | `step-4-eslint-9` | B | ESLint 9 migration (if preset green) |
| **5** | `step-5-async-storage-3` | A | Async Storage 3.x + storage tests + device smoke |
| **6** | `step-6-vision-camera-spike` | A | VC 5 + Nitro POC; QR on device |
| **7** | `step-7-vision-camera-complete` | A | Remove worklets stub if possible; docs; Maestro |
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
| `react-native` | 0.85.2 |
| `react` / `react-test-renderer` | 19.2.3 |
| `@react-native-community/cli` | 20.1.3 |
| `react-native-vision-camera` | 4.7.2 (freeze → 5.x program) |
| `@react-native-async-storage/async-storage` | 2.2.0 (freeze → 3.x program) |
| Node `engines` (manifest) | `>=18` (to raise → `>=20.19.4`) |
| CI | `testDebugUnitTest` only — no `yarn validate` yet |
| Jest suites | ~26 test files (`react-test-renderer`) |
| Gradle | 8.13 / AGP 8.12 / Kotlin 2.1.20 / SDK 36 |

### Phase 0 validation results

Recorded on branch `feature/177-i-2026-05-29-mobile-stack-modernization` (Windows workstation).

| Gate | Command | Result | Date |
|------|---------|--------|------|
| JS validate | `yarn validate` | **PASS** — lint 2 warnings (`no-void` in `PendingAuthScreen.tsx`); typecheck OK; **26 suites / 172 tests** pass | 2026-05-29 |
| JVM crypto | `:app:testDebugUnitTest` | **PASS** — BUILD SUCCESSFUL (~32s) | 2026-05-29 |

**Non-blocking observations:** `baseline-browser-mapping` stale-data warnings during lint/test; Jest console noise (`act(...)`, enrollment wizard logs); Gradle deprecation warnings for Gradle 9.

---

## Validation gates (every Track A/B PR)

| Gate | When |
|------|------|
| `yarn validate` | All JS/TS changes |
| `./gradlew :app:testDebugUnitTest` | Native or crypto touch |
| `yarn android:bundle:release` (JDK 17) | Native dep bumps |
| `yarn license:check` + `yarn license:app-data` | Runtime `dependencies` change |
| Device smoke enroll + pending/respond | Track A |
| Maestro `scripts/run-real-device-pilot-maestro.sh` | After Vision Camera step |
| `yarn generate:api` | Only if Auth OpenAPI contract changes |

---

## Execution notes

_(Append per PR — short bullets; full methodology narrative goes to ML log.)_

### 2026-05-29 — step-0-methodology

- Opened GitHub **#177**, branch `feature/177-i-2026-05-29-mobile-stack-modernization`.
- Materialized I/TB/ML from plan incubation (Lane B).

---

## Methodology outcomes (Step 8 — Lane E)

_To be completed after Track A/B execution._

---

## Related

- [`I-2026-05-29-mobile-stack-modernization.md`](ideas/I-2026-05-29-mobile-stack-modernization.md)
- [`ML-2026-05-29-mobile-stack-modernization.md`](method-logs/ML-2026-05-29-mobile-stack-modernization.md)
- [`TB-2026-0002`](ideas/TB-2026-0002-android-real-device-functional-pilot.md) — Maestro pilot
- Archived review: `.github/prompts/archived/2026-05/plan-mobileDependencyReview.prompt.md`
