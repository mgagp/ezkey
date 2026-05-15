# Tracer Bullet Brief — `TB-2026-0002` Android real-device functional pilot

## Metadata

- **ID:** `TB-2026-0002`
- **Status:** `active`
- **Related idea:** `I-2026-0019`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`

## Objective

Deliver a repeatable **Android-first pilot** that proves the real backend-to-phone trust path on a physical device against the **local clean-start Docker stack**, using **Maestro** as the primary UI driver, **Bash** for orchestration and artifact capture, **`ezkey-tests` / JUnit** where backend setup and steady-state verification already exist, and **`scrcpy`** only as an optional mirroring companion.

## Boundaries in scope

- One installed release-capable or debug build of `ezkey_mobile` on a **real Android device** reachable via `adb`.
- **Maestro** flow(s) for critical-path UI actions once steady state is reachable (hybrid init allowed).
- **Bash** entry script(s) under repo conventions (Git Bash on Windows per project shell guidance) that sequence: stack readiness assumptions, device checks, Maestro run, and captured logs or reports.
- Minimal **testability** additions in the mobile app for stable selectors on the pilot screens (`testID` or equivalent), limited to what the pilot needs.
- Alignment with documented mobile flows (`ezkey_mobile/docs/MOBILE_FUNCTIONAL_FLOWS.md`) and optional reuse of patterns from `ezkey-tests` operational scripts where it reduces duplication.

## Out of scope

- iOS parity or simulator-first automation.
- Replacing or shrinking existing `ezkey-tests` API suites.
- Broad UI coverage across every screen.
- Default reliance on cloud device farms or paid SaaS.
- Treating `scrcpy` as the pass/fail harness.

## Critical flows

- **Nominal path:** device enrolled against the clean-start stack; operator or automation reaches **steady state**; **one pending auth attempt** is visible and **approved/responded** on the device as product semantics require.
- **Steady-state slice:** a **short repeated auth loop** (backend creates attempts; phone handles them) long enough to validate repeatability without rebuilding orchestration logic entirely inside Maestro.
- **Exception awareness:** document what happens when enrollment cannot be fully unattended (QR/bootstrap, first-run gates) and how the hybrid human-assisted step hands off to automated steady state.

## Evidence plan

- Checked-in **Maestro flow file(s)** with a short README describing prerequisites (`adb`, Maestro CLI, stack URL assumptions).
- **Bash runner** that documents expected environment variables and exits non-zero on obvious preflight failures (no device, Maestro missing, stack not reachable if checked).
- **JUnit / `ezkey-tests` touchpoints** identified (existing tests or thin wrappers) for backend-side setup or verification where UI automation should not reimplement domain logic.
- **Artifacts:** Maestro report or captured stdout/stderr location convention; optional screenshot policy if useful for debugging.
- **Selector inventory:** list of added stable identifiers and the screens they cover.
- Update **`I-2026-0019`** status and notes when the pilot is runnable by a second developer from the documented steps.

## Quality gates

- **Operability gate:** a colleague can follow written steps on a clean workstation with JDK/Docker/Maestro prerequisites installed and reproduce the pilot on a connected phone.
- **Test gate:** pilot passes twice in succession on the same device without manual code edits between runs (hybrid init steps allowed if documented).
- **Safety gate:** scripts default to **non-destructive** stack posture; any destructive reset steps are explicit opt-in.

## Exit criteria

`TB-2026-0002` is validated when:

1. **Install + steady state:** documented path from **install on device** to **automated steady-state** execution (with hybrid init explicitly described if needed).
2. **One full pending/respond slice:** Maestro drives the **critical approve/respond path** on device against the clean-start stack.
3. **Short loop:** a **repeatable multi-attempt** slice runs without manual intervention after handoff, or residual manual steps are listed as explicit backlog follow-ups.
4. **Traceability:** `I-2026-0019` reflects pilot outcome (done vs follow-ups), and follow-on scope for broader coverage or CI is stated honestly.
