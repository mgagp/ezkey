# Backlog Idea — `I-2026-05-29` Mobile stack modernization

## Metadata

- **ID:** `I-2026-05-29-mobile-stack-modernization`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-05-29`
- **Updated at:** `2026-05-31`
- **Phase tags:** `P2-maintainability`, `toolchain`, `mobile-quality`
- **Component tags:** `mobile`
- **Lane:** `B` (plan incubation → canonical materialization)
- **GitHub issue:** `#177`
- **Captured by:** Marc (stack evaluation session, materialized 2026-05-29)

## Intent

Execute a **bounded but ambitious** modernization program for `ezkey_mobile` after discovering that a
routine “component upgrade” pass was largely completed in May 2026 (archived dependency review S01–S23).
The remaining work raises product confidence and maintenance posture through:

1. **Track B** — GitHub CI `yarn validate`, Node/RN patch alignment, ESLint 9 path, test hygiene.
2. **Track A** — `@react-native-async-storage/async-storage` 3.x and `react-native-vision-camera` 5.x
   (Nitro), treated as a **coupled native slice** with device and Maestro validation.

Close with a **Lane E methodology retrospective** using an explicit method log (`ML-2026-05-29`) so
real execution informs methodology tuning while it is still being refined.

## Incubation source

- Working plan: `.cursor/plans/mobile_stack_upgrade_a92a7f09.plan.md` (Plan mode incubation; not canonical)
- Tracer bullet: `TB-2026-05-29-mobile-stack-modernization.md`
- Method log: `ML-2026-05-29-mobile-stack-modernization.md`
- Historical evidence (do not redo): `.github/prompts/archived/2026-05/plan-mobileDependencyReview.prompt.md`

## Outcome (2026-05-31)

Delivered on GitHub **#177**, merged to **`main`**. Operator confirmed full functional smoke on
Pixel 7 Pro after merge (all screens / flows revisited).

**Delivered**

- Track B: GitHub **`yarn validate:ci`**, Node **20.19.4**, RN **0.85.3**, ESLint **9** flat config.
- Track A: Async Storage **3.x**, Vision Camera **5.x** + Nitro + official barcode scanner package.
- Post-ladder: conservative patch bumps; prudent runtime line (axios, i18n, navigation, screens);
  mobile Orval **8.14** + client regen; `actions/cache@v5` in CI.
- Agent ergonomics: `scripts/resolve-android-jdk.sh`, `scripts/build-install-debug-clean.sh`,
  `ezkey_mobile/AGENTS.md` § Android debug build, `.cursor/rules/ezkey-mobile-android-build.mdc`.
- Lane E: `ML-2026-05-29` retrospective; methodology decision on Android JDK resolution for agents.

**Deferred (explicit, non-blocking)**

- Maestro enrollment/QR harness extension (`TB-2026-0002` follow-up).
- iOS `Podfile.lock` reproducibility (macOS pass).
- Majors: gesture-handler 3, ESLint 10, Jest 30, TypeScript 6, AGP 9.

## Problem and value

### Problem

- The mobile manifest is on a **healthy RN 0.85.2** baseline, but **GitHub CI** only runs Android JVM
  crypto unit tests — not `yarn lint`, `yarn typecheck`, or Jest.
- **Vision Camera 4.7.2** and **Async Storage 2.2.0** are intentionally frozen; known majors (VC 5 +
  Nitro, AS 3) were deferred with documented blockers.
- `package.json` still advertises `node >= 18` while React Native 0.85 requires **Node >= 20.19.4**.
- Methodology for toolchain programs is maturing; this chantier is a **real-world pilot** for
  plan-incubation → branch → multi-pass PRs → Lane E retro.

### Expected value

- Stronger **PR gates** on mobile JS/TS quality without waiting for manual local runs.
- Reduced near-term drift risk on camera and persistence layers before Play-scale exposure.
- Repeatable playbook for future RN-line upgrades (coupled slices, Maestro gate, method log).
- Actionable **methodology decisions** from executed work, not theoretical process edits.

## Scope

### In scope

- Canonical artifacts `I-*`, `TB-*`, `ML-*` on feature branch `feature/177-i-2026-05-29-mobile-stack-modernization`
- Track B: workflow CI, engines, RN 0.85.3 patch slice, ESLint 9 when preset-aligned
- Track A: Async Storage 3.x; Vision Camera 5.x + Nitro; Android-native validation
- Manual functional smoke on Android (launch, QR enrollment, pending/respond) — **not** Maestro on this branch (pilot requires pre-enrolled id; separate session)
- Stack status note in `ezkey_mobile/docs/MOBILE_STACK_AND_ARCHITECTURE.md`
- Lane E retrospective session and 0..n `methodology/decisions/` entries

### Out of scope

- Re-running the full S01–S23 dependency review
- iOS `Podfile.lock` commit (macOS pass — separate follow-up)
- Migrating to `@react-navigation/native-stack` (optional backlog)
- Hand-editing `ezkey_mobile/openapi-spec.json`
- Broad Detox adoption

## Key assumptions

- **Android-first** remains the validation surface of record (`ezkey_mobile/AGENTS.md`).
- React, React Native, `react-test-renderer`, and camera-adjacent packages upgrade as **coupled slices**.
- JDK **17** for Android Gradle builds; Yarn **4** with `yarn install --immutable`.
- Index files under `product-docs/global/backlog/index.md` are updated on **`main` post-merge** only.

## Risks

| Risk | Severity | Mitigation |
|------|----------|------------|
| Vision Camera 5 / Nitro migration | **High** | Dedicated PRs; spike first; Maestro + manual QR |
| Async Storage 3 Gradle/Kotlin | **Medium** | Retry with Kotlin 2.1.20; documented S08 rollback |
| React/renderer version mismatch | **High** | Never bump React alone; follow RN release notes |
| CI Jest flakiness | **Medium** | `--runInBand` in CI |
| Scope creep vs “simple upgrade” | **Medium** | TB ladder; ML log each PR |

## Related artifacts

- `TB-2026-05-29-mobile-stack-modernization.md` — execution ladder and gates
- `ML-2026-05-29-mobile-stack-modernization.md` — running methodology notes (Lane E input)
- `I-2026-0019` / `TB-2026-0002` — real-device functional pilot (post-upgrade validation)
- `ezkey_mobile/docs/MOBILE_RELEASE_DECISION_MEMO.md` — release vs upgrade framing

## Grill Me (inline — 2026-05-29)

| # | Question | Answer / decision |
|---|----------|-------------------|
| G1 | Single PR for all bumps? | **No** — multi-pass per TB; Track B before Track A. |
| G2 | Skip Vision Camera 5? | **No** — explicitly in scope (operator choice: full upgrade). |
| G3 | CI only Jest? | **No** — full `yarn validate` in GitHub Actions. |
| G4 | Methodology retro? | **Yes** — explicit Step 3 + ML log during Step 2. |
| G5 | Lane B without new V-*? | **Yes** — toolchain/maintainability; I + TB + ML suffice. |

## Status transitions

- `incubating` → `ready` when Phase 0 baseline is recorded in TB and first docs PR is merged to the feature branch. **Reached 2026-05-29** (Phase 0 green on branch).
- `ready` → `active` when Track B PR 1 (CI validate) is in progress. **Reached 2026-05-29**.
- `active` → `done` after merge to `main`, operator functional sign-off, backlog index update, and Lane E closeout. **Reached 2026-05-31**.

## Closeout evidence

| Gate | Result |
|------|--------|
| GitHub CI (`js-validate` + JVM crypto) | **PASS** on branch and after merge |
| `yarn validate:ci` | **PASS** (172 tests; known non-blocking `act()` noise) |
| Android debug install (Pixel 7 Pro) | **PASS** via `build-install-debug-clean.sh` |
| Operator manual smoke (post-merge) | **PASS** — all app screens / flows |
| Maestro pilot | **Deferred** — enrollment not in harness |

## Residual risks

- Wireless `adb` may drop during long Gradle builds — mitigated by `adb devices` pre-check in canonical script.
- Jest `act()` warnings after React Query timers — monitor; not a release blocker.
- iOS parity and Play release remain separate milestones (`MOBILE_RELEASE_DECISION_MEMO.md`).
