# Tracer Bullet Brief — `TB-2026-0002` Android real-device functional pilot

## Metadata

- **ID:** `TB-2026-0002`
- **Status:** `active`
- **Related idea:** `I-2026-0019`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-05-08`
- **Captured by:** Marc

## Pilot status (Maestro slice)

The **single-attempt** Maestro flows (`pilot_pending_respond`, with and without 2-digit challenge) are **validated on hardware** after clean-start + new enrollment (hybrid init). That satisfies the **“one full pending/respond slice”** intent for the UI layer.

## Next phase — churn harness (JUnit + Maestro + evidence)

**Goal:** approximate the operator-reported “first check pending sometimes fails until a new attempt” class of issues by running **many** short phone-backed iterations with **varied** scenario parameters (challenge on/off, approve/deny, timeout paths when deterministically available), without assuming a single magic temporal sequence.

**Stack split (unchanged from TB objective):**

- **`ezkey-tests` / JUnit:** create and characterize each auth attempt; optional post-phone API assertions; emit **correlation metadata** (e.g. `auth_attempt_id`) into per-iteration artifact folders.
- **Maestro:** consume the attempt on device using existing or extended flows.
- **Bash:** session-scoped working directory, per-iteration subfolders, Maestro JUnit/XML + transcript + logcat slice; optional later **post-pass** summarizer (`summary.jsonl` → compact table).

**Artifact contract** (dated session root, `iterations/<nnnnn>/` with `meta.md`, `maestro.xml`, `maestro.log`, `logcat.txt`) is specified in:

- `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`

**Delivery order:** Phase A = skeleton + one iteration end-to-end; Phase B = deterministic multi-iteration happy path; Phase C = seeded randomization / long runs; Phase D = optional lightweight post-processing—avoid log “AI” or heavy parsers in v1.

**Exit criteria (TB) reminder:** item **3** (“short loop”) moves from backlog follow-up to **met** once Phase B runs unattended after hybrid enrollment handoff; item **1** is partially met today (hybrid documented); operability gate remains for a second developer.

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
