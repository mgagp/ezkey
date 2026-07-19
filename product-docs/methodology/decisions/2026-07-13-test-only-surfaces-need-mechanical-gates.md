---
public: true
---
# Test-only surfaces need mechanical gates (not plan prose alone)

## Date

2026-07-13

## Context

Ezkey Mobile F2a (controlled enrollment seed bypass for Maestro / real-device harness bootstrap)
was specified with clear acceptance criteria: debug/test-only, unavailable in release builds,
preserve bind/verify trust, and stay **green-by-design** under static-analysis / code-quality
review ([GitHub #254](https://github.com/mgagp/ezkey/issues/254), TSP for `TB-2026-0002`).

A post-rebase revision found that:

- issue, TSP, and Maestro README claimed a `__DEV__` (or “debug-only”) gate;
- implementation gated only on build-time env flags + ack token;
- local `.env` often enables bypass for harness work, and `react-native-config` bakes `.env` into
  native builds — so a release assemble without a hard fail could ship harness UI;
- the project’s own debug posture often uses offline debug APKs with `__DEV__ === false`, so a
  naive `__DEV__` gate would also break legitimate automation.

Plans and methodology artifacts had **stated** the intent correctly. They had not forced a
**mechanical** proof that release cannot activate the surface. That is the gap this decision
closes for future test-only work (mobile and elsewhere by analogy).

## Source signal

- “We said yes to instrumentation and automation, but a production-intent build must stay clean.”
- “Resist scrutiny from a skeptical human and an over-zealous agent” — including doctor-curated
  passes that must not false-alarm on intentional, gated harness code, and must alarm when the
  gate is missing.
- Methodology and plans are supposed to protect against this class of drift; when they do not,
  revise honestly and put the learning where cold readers look.

## Options considered

| Option | Advantages | Disadvantages |
| --- | --- | --- |
| **A. Docs-only correction** | Fast | Same failure mode recurs on the next harness feature |
| **B. Mechanical gate + release preflight + one discoverable contract doc** | Matches acceptance; cheap ongoing cost | Slightly more code than env-only |
| **C. Strip all in-app harness UI; host-only automation** | Maximal production purity | Blocks F2a camera/QR bootstrap goal; heavier F2b |

## Decision

Adopt **B** for Ezkey Mobile test-only surfaces:

1. **Mechanical availability gate** in app code using native **debug build type**
   (`BuildConfig.DEBUG` / `readIsDebugBuild()`), not `__DEV__` alone, plus explicit env opt-in.
2. **Release script preflight** that fails when test-only `.env` flags are still true.
3. **One canonical contract** (`ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`)
   linked from `AGENTS.md`, `docs/README.md`, and Maestro runbook — not a parallel backlog circus.
4. **Review rule for agents:** when evaluating harness / bypass / trace code, read that contract
   before escalating findings; treat missing mechanical gates as P1.

## Method lesson (Lane E)

Artifact prose (`I-*`, `TB-*`, `TSP-*`, issue acceptance, plan checklists) records **intent**.
For **security-adjacent or release-adjacent test hooks**, intent is incomplete without:

- a named mechanical invariant,
- a cheap automated check (unit test and/or release preflight),
- a single discoverable contract so doctor/human review does not re-litigate from scratch.

This does **not** mean every feature needs more documents. It means test-only escape hatches are
exactly the case where “we wrote it in the plan” is insufficient.

## Follow-up applied

- App gate + Jest coverage: `controlledEnrollmentBypass.ts`
- Native constant: `EzkeyCryptoModule.isDebugBuild`
- `scripts/assert-release-production-clean-env.sh` wired into release install script
- Canonical contract + AGENTS / docs / Maestro / TSP alignment
- Method log pointer: `product-docs/global/backlog/method-logs/ML-2026-07-13-mobile-f2a-production-clean-gate-gap.md`
