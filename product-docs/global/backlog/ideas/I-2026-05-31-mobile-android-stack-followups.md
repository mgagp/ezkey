# Backlog Idea — `I-2026-05-31` Mobile Android stack follow-ups (post-#177)

## Metadata

- **ID:** `I-2026-05-31-mobile-android-stack-followups`
- **Status:** `active`
- **Priority:** `P2`
- **Created at:** `2026-05-31`
- **Updated at:** `2026-06-23`
- **Phase tags:** `P2-maintainability`, `toolchain`, `mobile-quality`, `android`, `P2-hardening`
- **Component tags:** `mobile`, `android`, `ezkey-tests`
- **Lane:** `A` (promote per-slice `TB-*` when execution starts)
- **Spawned from:** `I-2026-05-29-mobile-stack-modernization` (GitHub **#177**, `done` 2026-05-31)
- **GitHub issue:** `#239`
- **Captured by:** Marc (post-merge planning session after mobile stack closeout)

## Intent

Track **Android-only** follow-up work after the mobile stack modernization program (#177). The baseline
(RN 0.85.3, VC5+Nitro, AS3, CI `validate:ci`, ESLint 9, canonical debug build scripts) is **done**.

**First execution slice (confirmed 2026-05-31):** extend **`TB-2026-0002`** with a **real-device auth churn
harness** — JUnit (or thin script) creates auth attempts on the clean-start stack; **Maestro** consumes them
on a physical device in a **seeded, long-running loop** (e.g. ~2 hours) with approve / deny / challenge /
timeout / not-consumed variants. Goal: accumulate correlated evidence to investigate the **intermittent
first check-pending / device-proof** class of issues (`I-2026-0019` motivation), even when the defect is
not currently reproducing.

**Future phase (generalization):** automate enrollment (wizard + QR) so cold device → enroll → churn runs
without manual onboarding — deferred until churn orchestration is proven valuable.

## Scope decision — churn v1 (2026-05-31)

| Decision | Choice |
|----------|--------|
| **Enrollment per session** | **Once, manual** (QR / bind-verify on device), same posture as existing Maestro pilot |
| **Automation focus** | **Create attempt (JUnit/API) → consume on phone (Maestro) → repeat** with artifacts |
| **Maestro flows** | Reuse `pilot_pending_respond` (+ challenge); add **deny** and orchestration; **no** wizard/QR flow in v1 |
| **Learning value** | JUnit ↔ Maestro coordination and session evidence layout — reusable pattern for later generalization |
| **Success criterion** | Repeatable unattended loop after manual enroll; rich artifacts when/if flaky behaviour appears — not “must reproduce bug on first run” |

## Problem and value

### Problem

- Operator-observed **intermittent** failure on first pending check from the phone after an auth attempt is
  queued; root cause still unclear; defect has **not** reproduced recently but warrants a **volume + evidence**
  harness.
- Existing Maestro pilot covers **pending/respond only** after manual enrollment; no **JUnit-coordinated loop**
  or multi-scenario churn.
- Residual dependency/tooling gaps from #177 remain tracked separately (Tier 2–3).

### Expected value

- **Operational churn on real device** with correlated `auth_attempt_id`, Maestro JUnit/XML, logcat, transcripts.
- Methodology and code patterns for **API truth + UI consumption** orchestration (generalizable later).
- One umbrella `I-*` for Android follow-ups without reopening #177.

## Scope

### In scope

- **`TB-2026-0002` extension** — churn harness (see F1); `ezkey-tests` touchpoints; Bash session layout per
  `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`.
- Android device validation; existing Maestro pilot assets.
- Optional: small `testID` additions only where churn/deny flows require them.

### Out of scope

- **Full Maestro enrollment / QR** in v1 (→ **F2** future phase).
- **iOS** (`I-2026-0027`).
- **Google Play release** (separate idea when needed).
- Re-running archived S01–S23 dependency review.
- Cloud device farms; broad UI coverage.

## Execution slices

Promote a row to **`TB-*`** execution when starting a branch. Runtime gates: `validate:ci` where JS changes;
`./scripts/build-install-debug-clean.sh`; churn sessions document stack + `ENROLLMENT_ID` prerequisites.

### Tier 1 — execute next

| ID | Slice | Target / note | TB when started | Status |
|----|-------|---------------|-----------------|--------|
| **F1** | **Auth churn harness** (JUnit + Maestro) | Extend **`TB-2026-0002`**; manual enroll **once per session**; seeded loop; deny flow; artifacts | **`TB-2026-0002`** (active phase) | **`active`** |
| F2 | Maestro **enrollment + QR** (Android) | **Future generalization** after F1; hybrid or deep-link strategy TBD | `TB-2026-0002` or new TB | `pending` |
| F3 | **react-native-gesture-handler** 3.x | Major; navigation / gestures | `TB-2026-…-gesture-handler-3` | `pending` |
| F4 | **react-native-nitro-image** 0.15.x | Minor Nitro | Sub-step of F3 or own TB | `pending` |

### Tier 2 — when convenient (tooling / hygiene)

| ID | Slice | Target / note | Status |
|----|-------|---------------|--------|
| H1 | **ESLint 10** + `@react-native/eslint-config` compat | Dev + CI lint path | `pending` |
| H2 | Jest **`act()` / React Query** timer noise | Local `validate:ci` exit code noise | `pending` |
| H3 | **baseline-browser-mapping** devDep refresh | Lint/test warning only | `pending` |
| H4 | **lint-staged** 17.x | Dev tooling | `pending` |
| H5 | **@rnx-kit/third-party-notices** 3.x | License generation tooling | `pending` |

### Tier 3 — parked until next RN / Android toolchain line

| ID | Slice | Blocker |
|----|-------|---------|
| P1 | **React** 19.2.6+ | RN renderer exact-match |
| P2 | **Jest 30** / **@types/jest** 30 | `@react-native/jest-preset` on Jest 29 |
| P3 | **TypeScript 6** | Stay on TS 5.9.x until RN preset moves |
| P4 | **AGP 9** / **Gradle 9** | Major Android migration |
| P5 | gesture-handler split | Reserved if F3 splits |

## Related artifacts

- [`I-2026-05-29-mobile-stack-modernization.md`](I-2026-05-29-mobile-stack-modernization.md) — closed program
- [`TB-2026-0002-android-real-device-functional-pilot.md`](TB-2026-0002-android-real-device-functional-pilot.md) — **F1 owner**
- [`I-2026-0019-android-real-device-mobile-functional-tests.md`](I-2026-0019-android-real-device-mobile-functional-tests.md) — parent real-device idea
- [`ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`](../../../ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md)
- [`ezkey_mobile/maestro/README.md`](../../../ezkey_mobile/maestro/README.md) — existing pilot flows
- [`ML-2026-05-29-mobile-stack-modernization.md`](../method-logs/ML-2026-05-29-mobile-stack-modernization.md)
- [`2026-05-31-mobile-android-build-jdk-resolution.md`](../../../methodology/decisions/2026-05-31-mobile-android-build-jdk-resolution.md)

## Status transitions

- `captured` → `incubating` when the first Tier-1 slice is scheduled. **Reached 2026-05-31** (F1 churn scope confirmed).
- `incubating` → `active` when F1 TB execution starts (issue + branch). **Reached 2026-06-23** (GitHub issue `#239`, branch `feat/mobile-f1-auth-churn-harness-issue-239`).
- `active` → `done` when Tier 1 slices are closed or explicitly deferred; Tier 2/3 item-by-item.

## Grill Me (inline)

| # | Question | Answer / decision |
|---|----------|-------------------|
| G1 | One I or many I? | **One I** with tiered table. |
| G2 | iOS here? | **No** — `I-2026-0027`. |
| G3 | First slice? | **F1 churn** (JUnit + Maestro), not enrollment QR. |
| G4 | Enrollment in v1? | **Manual once per session.** |
| G5 | Repro required on first run? | **No** — volume + evidence; bug may be rare. |
