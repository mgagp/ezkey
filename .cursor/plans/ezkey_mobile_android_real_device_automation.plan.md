---
name: Ezkey Mobile Android real-device automation plan
overview: Introduce a pragmatic Android-first real-device functional test capability for ezkey_mobile, built around a physical phone connected over adb, the local clean-start Docker stack, Bash orchestration, and a staged combination of Maestro, scrcpy, and existing ezkey-tests building blocks.
todos:
  - id: choose-primary-framework
    content: Lock the default Android real-device automation framework and its companion tooling
  - id: define-repo-layout
    content: Place flows, scripts, and orchestration where they align with ezkey_mobile and ezkey-tests responsibilities
  - id: stabilize-selectors
    content: Add stable test-facing identifiers for critical mobile screens and actions before expanding automation
  - id: pilot-flow
    content: Deliver a first real-device pilot covering enrollment, pending, and one short steady-state auth loop
isProject: true
---

# Ezkey Mobile Android real-device automation plan

## Executive summary

The recommended first move is:

1. **Use Maestro as the primary Android real-device UI driver.**
2. **Use `scrcpy` only as a local mirroring and debugging companion, not as the test framework.**
3. **Keep JUnit in a supporting orchestration role through `ezkey-tests`, not as the primary phone UI driver.**
4. **Target a narrow Android-first pilot on the real clean-start stack before any broader investment.**

This is the best fit for Ezkey's current needs because it is:

- open source by default,
- real-device friendly via `adb`,
- Bash-friendly,
- low-friction for a React Native app,
- compatible with a pragmatic "functional plus operational churn" posture,
- less setup-heavy than Appium for the first slice,
- and more appropriate than native Android instrumentation for a cross-layer workflow that must exercise the exact phone UX and backend trust path together.

## Canonical materialization

This working plan has been materialized into:

- `V-2026-0011` in `product-docs/global/vision/product-orientation-notes.md`
- `I-2026-0019` in `product-docs/global/backlog/ideas/I-2026-0019-android-real-device-mobile-functional-tests.md`
- `TB-2026-0002` in `product-docs/global/backlog/ideas/TB-2026-0002-android-real-device-functional-pilot.md`
- Next-phase churn + evidence contract: `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`

## Context and design goal

Ezkey already has strong coverage in two places:

- **unit and component tests** in `ezkey_mobile`,
- **real Docker-stack functional tests** in `ezkey-tests`.

What is still missing is a reusable automation layer that proves the exact Android real-device path:

- local clean-start stack,
- real APK on a real phone,
- real Android Keystore-backed signing,
- real enrollment,
- real `pending`,
- real `respond`,
- and optionally a repeated steady-state loop similar to operational churn.

This gap matters because the mobile app is not a peripheral convenience UI. It is part of Ezkey's cryptographic trust chain.

## Option comparison

### Option A — Maestro plus `scrcpy` plus Bash orchestration

**Recommendation: choose this first.**

**Strengths**

- Open source and actively used for real-device mobile automation.
- Drives Android through `adb` without adding instrumentation dependencies to the app build.
- Works well for real user-style flows on a physical device.
- Simple, readable flow files are a good fit for high-signal mobile workflows.
- Easy to invoke from Bash scripts.
- Can emit JUnit-style reports for CI-style consumption later.
- Keeps the app build simpler because the tests live outside the APK.

**Weaknesses**

- Less expressive than Appium for deep custom driver logic.
- Assertions are intentionally UI-centric rather than rich Java object assertions.
- Multi-actor orchestration still needs host-side scripting around the flow files.

**Fit for Ezkey**

Very strong. Ezkey needs a pragmatic, Android-first, real-device operational test layer more than it needs a large mobile automation platform.

### Option B — Appium plus UiAutomator2 plus JUnit

**Recommendation: keep as the fallback or later escalation path.**

**Strengths**

- Open source, mature, and proven on physical Android devices.
- Natural fit if the long-term center of gravity becomes Java plus JUnit orchestration.
- Stronger for richer custom host-side logic, deeper driver control, and more traditional test-code structure.

**Weaknesses**

- More moving parts: server, driver, desired capabilities, client setup, session lifecycle.
- Heavier maintenance burden for a small first slice.
- Higher accidental complexity than Maestro for the current need.

**Fit for Ezkey**

Good second choice if the first pilot proves that Maestro is too limiting, or if the long-term goal becomes heavily Java-centric orchestration and richer programmatic control.

### Option C — Android instrumentation (`Espresso` / `UIAutomator`)

**Recommendation: do not choose this as the first path.**

**Strengths**

- Official Android stack.
- Real-device capable.
- JUnit-native.

**Weaknesses**

- Lives inside Android-specific test infrastructure.
- More awkward for a React Native app where the business goal is cross-layer functional validation rather than Android-only white-box UI testing.
- Adds native test dependencies and runner configuration.
- Higher friction for system-level or operator-style flows.

**Fit for Ezkey**

Useful only if a later phase needs deep Android-native assertions that external drivers cannot express cleanly.

### Option D — Detox

**Recommendation: deprioritize for now.**

The repository already mentions optional Detox in docs, but there is **no actual Detox setup** in `ezkey_mobile` today. More importantly, the immediate goal is **real Android phone automation**, not emulator-first React Native e2e. For this specific mandate, Detox is not the best first investment.

## Why Maestro is the best first choice

Maestro lines up well with the real constraints already visible in the repo:

- `ezkey_mobile` already supports installing a standalone debug APK on a phone without Metro by default.
- The current workflow already assumes `adb` and physical-device usage.
- The app has some accessibility labeling, but not yet a robust stable-selector strategy. Maestro can start small while that testability pass is added.
- Ezkey's mobile flows are intentionally narrow: enroll, check pending, approve or deny. That is exactly the kind of workflow-focused surface where Maestro works well.

## Role of `scrcpy`

`scrcpy` should be treated as a **developer-ergonomics companion**, not the automation framework.

Recommended usage:

- local mirrored execution while a Maestro flow is running,
- debugging flaky or intermittent steps,
- support for light human-assisted initialization when needed,
- artifact recording during investigation of pending-signature failures or timeout confusion.

`scrcpy` is valuable because it keeps the real phone visible and controllable during local work, but it should not own pass/fail semantics.

## Role of JUnit and `ezkey-tests`

JUnit should stay in the system, but in the right layer.

**Recommended split**

- **`ezkey_mobile` + Maestro**: drive the phone UI and the on-device trust path.
- **`ezkey-tests` + JUnit**: provision backend state, create auth attempts, run repeated steady-state loops, and validate backend-side final state.
- **Bash runner**: orchestrate both sides.

This split preserves Ezkey's current strengths:

- JUnit remains the home for backend-aware operational logic.
- The phone is automated by a tool designed for phone interaction.
- Bash remains the neutral glue that matches the repo preference.

## Proposed repository layout

### Under `ezkey_mobile`

Use `ezkey_mobile` for artifacts that are fundamentally about driving the mobile app UI:

- `ezkey_mobile/.maestro/flows/`
  - `enrollment-happy-path.yaml`
  - `pending-approve.yaml`
  - `pending-deny.yaml`
  - `steady-state-poll-and-approve.yaml`
- `ezkey_mobile/.maestro/shared/`
  - reusable subflows and environment defaults
- `ezkey_mobile/scripts/`
  - `run-maestro-real-device.sh`
  - `start-android-mirror.sh`
  - `install-debug-apk.sh`
- `ezkey_mobile/docs/`
  - a focused doc such as `MOBILE_REAL_DEVICE_AUTOMATION.md`

### Under `ezkey-tests`

Use `ezkey-tests` for backend orchestration that already belongs to the functional/operational test layer:

- `ezkey-tests/scripts/run-mobile-real-device-operational.sh`
- optional helper tests or utilities for:
  - bootstrap material extraction,
  - integration and enrollment setup,
  - auth attempt creation loops,
  - state verification after phone actions.

### Why this split is right

It keeps mobile UI automation near the mobile app, while backend churn orchestration stays with the existing Java functional suite instead of inventing a parallel backend harness in JavaScript or YAML.

## Required testability pass in the mobile app

Before serious automation, the app needs a small but deliberate **testability stabilization** pass.

### Current issue

The app currently relies heavily on `accessibilityLabel`, and many of those labels are localized or user-visible. That is good for accessibility, but weak as the primary automation contract.

### Recommended improvement

Add stable, non-localized `testID` values for critical elements only:

- enrollment add button,
- scanner open action,
- challenge input,
- verify submit action,
- check pending action,
- approve action,
- deny action,
- pending error box,
- latest result summary,
- settings/security toggles only if they become part of the pilot.

### Rule

Do not add `testID`s everywhere. Add them only on the narrow, high-value path needed for durable automation.

## Proposed first pilot scope

Keep the pilot intentionally narrow.

### Pilot objective

Prove that Ezkey can automate a real Android phone against the local stack in a way that is useful, repeatable, and not too expensive to maintain.

### Pilot scenarios

1. **Enrollment happy path**
   - install app on a real Android phone,
   - provide bootstrap material,
   - complete `bind` plus `verify`,
   - confirm enrollment appears locally.

2. **Pending approve happy path**
   - create one auth attempt from the backend,
   - on the phone, manually check pending,
   - approve it,
   - confirm accepted result both on-device and backend-side.

3. **Short steady-state loop**
   - one-time initialization by human or script,
   - then repeated backend auth-attempt creation for a bounded number of iterations,
   - repeated phone polling and approval,
   - collect timing and failure evidence.

4. **One diagnostic negative path**
   - either a tampered pending response using the existing demo MITM support,
   - or a timeout / invalid-signature repro path aligned with the current pending debug plan.

This gives immediate value without overcommitting to a large suite.

## Initialization model

The right first strategy is **hybrid**:

- **Initialization can be partially human-assisted** in the first phase.
- **Steady state should be automated.**

That means:

- allow a short human step for device pairing, QR bootstrap handoff, or first-run permission handling if that materially lowers complexity,
- then automate the repeated flows that create the most value.

This mirrors Ezkey's existing operational-test philosophy well: one-time setup, then a meaningful repeated run.

## Docker and environment posture

### Default recommendation for pilot runs

Use the real clean-start stack, but for churn or repeated mobile loops prefer **test mode**:

```bash
SPRING_PROFILES_ACTIVE=docker,docker-test ./docker/start.sh
```

Why:

- it removes rate-limit noise from the first steady-state runs,
- it keeps the real service topology,
- it preserves the useful backend semantics while making the mobile pilot easier to iterate on.

### Separate negative-path phase

Later, add a smaller production-like pass for:

- rate-limit behavior,
- timeout behavior,
- and adverse pending paths.

Do not mix those concerns into the first steady-state pilot.

## Recommended orchestration model

Use a Bash-first orchestration script from the repo root, conceptually like:

```bash
./ezkey-tests/scripts/run-mobile-real-device-operational.sh \
  --device serial \
  --profile light \
  --with-mirror \
  --scenario approve-loop
```

The script should orchestrate, in order:

1. verify `adb` device presence,
2. optionally start `scrcpy`,
3. ensure the app is installed,
4. ensure the stack is up,
5. prepare or reuse bootstrap material,
6. run Maestro flow(s),
7. run JUnit helper or churn loop when needed,
8. capture artifacts and summarize the outcome.

## Artifacts and evidence

The pilot should capture durable evidence, not just pass/fail.

Recommended artifacts:

- Maestro JUnit-style output,
- phone screenshots on failure,
- optional screen recording when mirrored,
- backend logs or extracted summaries for the created auth attempts,
- a small run summary in `logs/` or `.ezkey-test/`.

This is especially important for the intermittent pending-signature concern, because the value is not only detection but also fast diagnosis.

## How this helps the pending / timeout issue

The real-device harness creates a better repro lane for the class of problem already described in `ezkey_mobile/MOBILE_PENDING_DEBUG_PLAN.md`:

- intermittent pending failures,
- confusing timeout behavior,
- accidental rate-limit escalation after a bad pending cycle,
- uncertainty about whether the defect is on the phone, in the stored integration key, or on the backend side.

A structured real-device run can deliberately collect:

- the exact phone-side debug box details,
- logcat,
- backend diagnostic logs,
- and the corresponding auth-attempt lifecycle.

That is materially better than ad hoc manual reproduction.

## Phased rollout

### Phase 0 — Decision and scaffolding

- Choose Maestro as the default framework.
- Add a short design note documenting why Appium and instrumentation were not chosen first.
- Create the repo layout and Bash wrappers.

### Phase 1 — Testability hardening

- Add stable `testID`s on the critical path.
- Confirm the debug APK install workflow is deterministic on the maintainer workstation.
- Add optional `scrcpy` helper scripts.

### Phase 2 — First real-device happy path

- Install app on physical Android device.
- Complete one enrollment.
- Create one auth attempt.
- Approve it on the phone.
- Verify backend terminal state.

### Phase 3 — Short steady-state operational pilot

- Reuse one enrolled device.
- Backend repeatedly creates auth attempts.
- Phone repeatedly handles them.
- Run a short bounded profile, similar in spirit to operational churn but still local and developer-friendly.

### Phase 4 — Targeted negative-path coverage

- Tampered pending response via demo MITM.
- Timeout path.
- Optional dedicated rate-limit scenario.

### Phase 5 — Decide whether to deepen or stop

If the pilot proves valuable and stable:

- expand the flow set,
- add CI-adjacent hooks later,
- consider whether Appium or deeper Java orchestration is justified.

If it proves too brittle:

- keep the harness as a developer-only repro tool,
- and do not over-invest.

## Acceptance criteria for the pilot

The first pilot is successful if all of the following are true:

1. A single Bash command can run the real-device happy path on a connected Android phone.
2. The phone run is observable locally through optional mirroring.
3. The backend state can be verified automatically after the phone action.
4. The harness can run at least one short repeated auth loop without manual intervention in steady state.
5. Failures produce useful artifacts for diagnosis.

## Final recommendation

Adopt this default path:

- **Primary framework:** Maestro
- **Mirror/debug companion:** `scrcpy`
- **Backend orchestration and steady-state helper:** existing `ezkey-tests` JUnit layer
- **Glue:** Bash scripts
- **Environment baseline:** local clean-start Docker stack, usually `docker,docker-test` for early repeated runs

This is the highest-confidence, lowest-accidental-complexity route for introducing Android real-device automation into Ezkey now.
