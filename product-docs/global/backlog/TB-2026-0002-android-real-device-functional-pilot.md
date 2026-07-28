# Tracer Bullet Brief — `TB-2026-0002` Android real-device functional pilot

## Metadata

- **ID:** `TB-2026-0002`
- **Status:** `active`
- **Related idea:** `I-2026-0019`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-07-13`
- **Captured by:** Marc
- **GitHub issues:** [#239](https://github.com/mgagp/ezkey/issues/239) (F1 churn harness), [#254](https://github.com/mgagp/ezkey/issues/254) (F2a enrollment seed bypass). Supersedes umbrella tracking in [#179](https://github.com/mgagp/ezkey/issues/179) — prefer #239/#254 for execution.
- **Test plan slice:** [`TSP-2026-06-26-mobile-real-device-churn-harness.md`](../test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md)
- **Related follow-ups:** `I-2026-05-31-mobile-android-stack-followups` — **F1** = churn harness (**in progress**); **F2a** = controlled enrollment bootstrap (**in progress**); **F2b** = full QR automation (future)

## Pilot status (Maestro slice — pending/respond)

The **single-attempt** Maestro flows (`pilot_pending_respond`, with and without 2-digit challenge) are **validated on hardware** after clean-start + **manual** enrollment. That satisfies TB exit criterion **#2** (one full pending/respond slice).

**Active work (F1):** extend this pilot into a **JUnit-coordinated churn loop** — orchestration and documentation aligned **2026-06-26**; **Phase A hardware validation pending**. See **Next slice — auth churn harness** below.

**Active work (F2a):** controlled enrollment seed bypass for debug/test builds ([#254](https://github.com/mgagp/ezkey/issues/254)) — app + Maestro flows landed; **2026-07-13** production-clean mechanical gate (`BuildConfig.DEBUG` + env + ack, release env preflight). Contract: `ezkey_mobile/docs/MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md`. **Not** a substitute for F1 artifact contract or manual enroll path for churn v1 steady state.

## Next slice — auth churn harness (F1 — priority)

**Owner:** [`I-2026-05-31-mobile-android-stack-followups`](I-2026-05-31-mobile-android-stack-followups.md) (**F1**, `in progress`). **GitHub:** [#239](https://github.com/mgagp/ezkey/issues/239).

**Goal:** After **one manual enrollment per session**, run an **autonomous seeded loop** (target ~2 h or N iterations): **`ezkey-tests` / JUnit** creates auth attempts (challenge on/off, etc.) → **Maestro** consumes on device (approve, deny, challenge, timeout / not-consumed) → **correlated artifacts** per iteration to investigate intermittent **first check-pending / device-proof** failures.

**Prerequisites (v1)**

- Clean-start Docker stack; debug APK on device (`build-install-debug-clean.sh`).
- **Manual enrollment once** per session; known `ENROLLMENT_ID`.
- Existing Maestro flows + runner; design in `ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`.

**In scope**

- Session orchestrator with iteration folders, `summary.jsonl` / TSV (contract in
  `MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`). **Interim:** PowerShell campaign/churn scripts
  (`run-mobile-test-campaign.ps1`, `run-mobile-churn-no-recovery.ps1`) — Phase A layout and JUnit
  layer still open.
- JUnit or thin wrapper using `TestDataFactory#createAuthAttempt` (and related API truth).
- Maestro: existing flows + **deny** flow; parameterized env per iteration.
- Seeded scenario picker (approve / deny / challenge / skip consume).
- Documented operator path; target Git Bash per repo convention (Windows interim PowerShell noted in
  `ezkey_mobile/scripts/README.md`).

**Out of scope v1**

- Maestro enrollment / QR automation (**F2** — future generalization).
- Full unattended cold device from install.

**Delivery order**

1. Phase A: one iteration end-to-end (JUnit create → Maestro consume → artifacts).
2. Phase B: deterministic multi-iteration loop.
3. Phase C: seeded variance + long run (~2 h).
4. Phase D (optional): lightweight post-pass summarizer.

**Quality gates**

- `yarn validate:ci` green if mobile/JS touched.
- Churn session produces expected artifact layout; Maestro + attempt ids correlate in `meta.md`.

**Status:** `in progress` — Phase A (documentation + orchestration prep **2026-06-26**); hardware
validation and full artifact contract **pending**.

**Execution (2026-06-26):**

- Test plan slice [`TSP-2026-06-26-mobile-real-device-churn-harness.md`](../test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md) created.
- Design doc and operator runbooks updated (`MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`, `maestro/README.md`, `scripts/README.md`, `AGENTS.md`).
- Interim orchestration scripts documented (campaign 3-phase model, churn-no-recovery loop).
- **Deferred to next hardware session:** Phase A green run, JUnit wrapper, deny flow, session folder layout.

## Future slice — Android enrollment automation (F2)

**Owner:** same `I-2026-05-31`.

### F2a — controlled seed bypass (`in progress`)

**GitHub:** [#254](https://github.com/mgagp/ezkey/issues/254). **Branch:** `feat/mobile-f2a-enrollment-seed-bypass-issue-254`.

Debug/test-only wizard bypass (`EZKEY_ENROLLMENT_SEED_BYPASS_*`), Maestro flows
`pilot_enrollment_seed_bypass*.yaml`, optional `get-fresh-enrollment-seed.ps1` (recovery-based fresh
seed — explicit opt-in, not for steady-state churn loops). Hardware re-validation pending.

### F2b — full QR / camera path (`pending`)

**Goal:** After F1 proves orchestration value, automate **wizard → QR → steady state** without
recovery-based reset. **Status:** `pending` — do not start before F1 Phase B at minimum.

## Next phase — churn harness (design reference)

**Canonical execution scope:** **F1** section above. Detail below matches `MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`.

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

## Links

- Test plan slice: [`TSP-2026-06-26-mobile-real-device-churn-harness.md`](../test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md)
- Design + artifact contract: [`ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md`](../../../ezkey_mobile/docs/MOBILE_REAL_DEVICE_CHURN_AND_EVIDENCE.md)
- Maestro runbook: [`ezkey_mobile/maestro/README.md`](../../../ezkey_mobile/maestro/README.md)
- GitHub: [#239](https://github.com/mgagp/ezkey/issues/239) (F1), [#254](https://github.com/mgagp/ezkey/issues/254) (F2a)
- Incubation plan: [`.cursor/plans/ezkey_mobile_android_real_device_automation.plan.md`](../../../.cursor/plans/ezkey_mobile_android_real_device_automation.plan.md)
