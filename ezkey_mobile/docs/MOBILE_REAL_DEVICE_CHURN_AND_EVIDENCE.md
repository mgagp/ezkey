# Real-device churn harness — design note (JUnit + Maestro)

## Purpose

This note captures the **next-phase** intent for `TB-2026-0002` / `I-2026-0019`: after the validated **hybrid** Maestro pilot (manual enrollment on device, automated pending/respond), add a **repeatable churn loop** driven from **`ezkey-tests` (JUnit)** for auth-attempt creation and scenario injection, with **Maestro** consuming each attempt on a **physical Android** device.

It complements:

- `ezkey_mobile/maestro/README.md` — pilot prerequisites and selector inventory.
- `ezkey_mobile/scripts/run-real-device-pilot-maestro.sh` — single-flow runner and report paths.
- `ezkey_mobile/MOBILE_PENDING_DEBUG_PLAN.md` — pending/signature diagnostics.
- `ezkey-tests/scripts/run-operational-churn.sh` — operational churn **without** phone UI (pattern for duration/iterations/seed).

## Operator motivation (problem statement)

Observed intermittently in the field: the **first** “check pending” from the phone after an auth attempt is started (e.g. from Admin UI) **fails** (symptoms consistent with an invalid or rejected **device proof** path); cancelling the attempt and creating a **new** one sometimes makes the next check succeed. Hypotheses under investigation include timing, network, or native signing paths—not assumed to be tied to a specific enrollment id or “admin-linked enrollment” until evidence says otherwise.

The automation goal is **not** to encode one fragile sequence believed to reproduce the bug, but to run **many varied sequences** (challenge on/off, approve/deny, timeouts where feasible) over volume and time so that **when** the defect appears, **correlated artifacts** exist for root-cause analysis.

## Role segmentation update (2026-06-24)

To reduce operator lockout risk and improve agent autonomy, mobile test operations now use a split-admin model on clean-start stacks:

- `admin.docker`: reserved for human operator ownership and recovery.
- dedicated mobile test admin: currently `mobile_tester`, with preferred long-term naming `admin.mobile`.

Current validated posture for the dedicated mobile test admin:

- global admin exists and is active;
- linked enrollment is `VERIFIED` and has server-side `device_public_key`;
- Demo Device persists the enrollment material in `data/enrollments/<id>.json`, enabling autonomous bind/verify/auth loops.

This split is considered **essential operational complexity** for reliable mobile test autonomy, not accidental complexity.

## Canonical 3-phase campaign sequence (agent-cold restart safe)

This is the current process contract for autonomous mobile campaign runs.

### Phase 1 — Token bootstrap (Demo Device lane)

- Preconditions:
  - clean-start stack healthy;
  - dedicated global admin exists (`mobile_tester` now, target `admin.mobile`);
  - Demo Device enrollment JSON exists for that admin.
- Execution model (aligned with functional tests helpers):
  - `POST /api/v1/admin/auth/login` (`challengeRequested=true`)
  - `POST /api/v1/auth-attempts/pending`
  - `POST /api/v1/auth-attempts/respond`
  - `POST /api/v1/admin/auth/passwordless-wait`
- Outcome: valid Global Admin bearer token for API provisioning.

### Phase 2 — Provisioning (Admin API lane)

- Use the bearer token to:
  - create or reuse a dedicated integration for mobile campaign work;
  - create a fresh enrollment for the current run.
- Persist campaign state (JSON): integration id, enrollment id, timestamps, urls, scenario metadata.
- Optional bind-on-phone step: run Maestro enrollment flow to consume the fresh enrollment on real phone.

### Phase 3 — Churn execution (Real phone lane)

- Preconditions:
  - campaign enrollment tile is visible on real phone home screen;
  - campaign token still valid.
- Run scenario loop (example set):
  - approve without challenge;
  - approve with challenge;
  - timeout with no action.
- Validate each iteration using backend status checks (`/api/v1/auth-attempts/{id}`) and keep summary CSV/JSON artifacts.

### Lane boundary rule

- Demo Device lane ends after Phase 1 token bootstrap (and optionally helping phone bind during Phase 2 bootstrap tasks).
- Real churn truth is Phase 3 on physical phone + Maestro + backend assertions.

## Bootstrap promotion criteria (future)

Automatic creation of `admin.mobile` during clean-start bootstrap is a candidate only after these gates are met:

1. repeated Maestro/JUnit churn runs succeed with fresh one-shot credentials over multiple sessions;
2. the split-admin practice shows lower lockout and lower recovery-code churn than a single-admin model;
3. docs and scripts converge on the same baseline checks and naming;
4. at least one tracer-bullet closeout confirms reproducible execution evidence.

Until those gates pass, keep dedicated mobile test admin setup as an explicit step (human or scripted) after clean-start.

## Design principles

1. **Keep hybrid enrollment** until a separate initiative justifies full Maestro enrollment: the device is known-good for crypto and storage before the loop starts.
2. **Shortest path to the hypothesis domain:** JUnit owns **API truth** (create auth attempt, optional cancel, read attempt state); Maestro owns **phone UX** (navigate, check pending, approve/deny, challenge entry).
3. **Bounded complexity:** start with a **deterministic happy-path loop** (fixed sequence, few iterations) to validate orchestration and the **artifact folder contract**; only then add randomization and long runs.
4. **Reproducibility:** support a fixed **random seed** (same spirit as `run-operational-churn.sh --seed`) for any randomized scenario picker.
5. **No over-engineering on “live log intelligence”:** iteration `meta.md` plus raw logs first; optional **second-pass** script that aggregates pass/fail into a small table is explicitly a later add-on.

## High-level architecture

| Layer | Responsibility |
| --- | --- |
| **JUnit (`ezkey-tests`)** | For each iteration: create (or supersede) an auth attempt with chosen flags; optionally assert backend state after the phone step; write **correlation ids** and scenario parameters into the iteration folder. |
| **Bash (repo root or `ezkey_mobile/scripts`)** | Create session directory; for each iteration: export env vars → invoke Maestro → stop/capture logcat slice → append outcome to a session-level `summary.tsv` or `summary.jsonl`. |
| **Maestro** | Existing flows extended or parameterized (`ENROLLMENT_ID`, `CHALLENGE_CODE`, future: `EXPECTED_OUTCOME`, longer timeouts where needed). Deny path may require a **second flow file** mirroring approve. |

**Desynchronization** between “JUnit thinks attempt X is pending” and “phone shows error / timeout” is expected to surface as: Maestro non-zero exit, or JUnit post-step API state mismatch. Treating “Maestro global timeout” alone as proof of the historical bug would be **too weak**—it is still a useful **signal** for triage when combined with logcat and attempt id correlation.

## Artifact directory layout (contract)

All paths below are **conventions** for local developer runs (gitignored parent recommended, e.g. `ezkey_mobile/maestro/sessions/` or repo-root `logs/mobile-churn/`).

```text
<session-root>/
  SESSION.md                 # human-readable: git sha, device serial, stack profile, seed, enrollment id, start/end time
  summary.jsonl              # one JSON object per iteration (machine-friendly rollup)
  iterations/
    00001/
      meta.md                # short Markdown: timestamp, enrollment id, scenario flags, auth_attempt_id, intended outcome
      maestro.xml            # JUnit report from Maestro (--format junit)
      maestro.log            # full Maestro transcript (stdout/stderr)
      logcat.txt             # adb logcat slice for this iteration (tags TBD; include ReactNativeJS + app tag)
      junit-side.log         # optional: snippet of Surefire output for this iteration if split per iteration
    00002/
      ...
```

### `meta.md` (per iteration) — suggested fields

- `iteration`: numeric index (zero-padded for sort).
- `started_at` / `ended_at` (UTC ISO-8601).
- `enrollment_id` (numeric id as used in Maestro `testID`).
- `auth_attempt_id` (from API after creation).
- `scenario`: structured line, e.g. `challenge=false approve=true admin_timeout=false`.
- `random_seed` / `draw_index` (if using a scenario deck).
- `maestro_exit_code`.
- `outcome`: `pass` | `fail_maestro` | `fail_api_assert` | `unknown` (operator-set or script-set).

### `SESSION.md` (session level)

- Repository revision (`git describe --always` or CI env).
- `adb` device serial.
- Docker / profile note (`SPRING_PROFILES_ACTIVE` if relevant).
- Link to the Maestro **flow file(s)** used.

## Randomization strategy (later phase)

Per iteration, pick from a **small closed set** of scenario dimensions (not open-ended prose):

- `challenge_required` (boolean).
- `operator_action` (`approve` | `deny`) — requires Maestro flow variants.
- `simulate_slow_network` or `admin_timeout_intent` — only when the stack supports a **deterministic** slow path (avoid flaky “hope the network drops” tests in v1).

Use a **deck** or seeded PRNG so the distribution is explicit and replayable.

## Phased delivery

| Phase | Goal |
| --- | --- |
| **A — Harness skeleton** | Bash creates `session-root` + one iteration folder; JUnit **or** a stub script creates **one** attempt; Maestro runs **one** known-good flow; all files listed above appear in the right places. |
| **B — Deterministic multi-iteration** | N iterations, fixed scenario, assert minimal API state after each Maestro run. |
| **C — Randomized long run** | Duration or iteration cap, seeded randomizer, optional `docker-test` profile to reduce rate-limit noise. |
| **D — Optional post-pass** | Small script reads `summary.jsonl` + `meta.md` and emits `SESSION-table.md` (human glance); no requirement for NLP on logcat in v1. |

## Open questions (explicit)

- **Deny flow:** add `pilot_pending_deny.yaml` (or equivalent) with stable selectors.
- **Timeout injection:** needs a **supported** mechanism (test profile, mock delay, or documented MITM) before encoding in the scenario matrix.
- **Correlation id in logcat:** consider a single log line from JUnit or a test-only Auth API header echoed by the app when `EZKEY_*_TRACE` flags are on—**optional** once Phase A is stable.

## Traceability

- Tracer bullet: `product-docs/global/backlog/ideas/TB-2026-0002-android-real-device-functional-pilot.md`
- Idea: `product-docs/global/backlog/ideas/I-2026-0019-android-real-device-mobile-functional-tests.md`
- Follow-ups: `product-docs/global/backlog/ideas/I-2026-05-31-mobile-android-stack-followups.md`
- Test plan slice: `product-docs/global/backlog/test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md`
- GitHub: [#239](https://github.com/mgagp/ezkey/issues/239) (F1), [#254](https://github.com/mgagp/ezkey/issues/254) (F2a)
- Incubation plan: `.cursor/plans/ezkey_mobile_android_real_device_automation.plan.md` (Phase 3 steady-state)

## Implementation status (2026-06-26)

| Layer | Status | Notes |
| --- | --- | --- |
| Maestro pilot (pending/respond) | **Validated** | TB exit #2 |
| F2a enrollment bypass | **Shipped + production-clean gate** | Native debug + env + ack; release env preflight; see `MOBILE_TEST_AUTOMATION_PRODUCTION_CLEAN.md` |
| 3-phase campaign model | **Documented** | `run-mobile-test-campaign.ps1` (interim PowerShell) |
| Churn loop (no recovery) | **Documented** | `run-mobile-churn-no-recovery.ps1` (interim) |
| JUnit `TestDataFactory` wrapper | **Open** | Phase A deliverable |
| Session artifact folders (`iterations/<nnnnn>/`) | **Open** | Phase A deliverable |
| Deny Maestro flow | **Open** | Phase B |
| Seeded long run (`--seed`) | **Open** | Phase C |

**Next hardware session:** Phase A — one iteration end-to-end with full artifact contract; then
`traceability-sync` and TB execution bullet update.

## Documentation cadence (avoid rot)

Advance **one or two harness phases at a time**. Update **canonical** docs only at defined gates; keep **run evidence** in gitignored session folders, not in `product-docs/`.

### Tiered sources of truth

| Tier | What it holds | Update when |
| --- | --- | --- |
| **Intent** | `I-2026-0019` — problem, hypotheses, scope | Meaning of the initiative changes |
| **Slice contract** | `TB-2026-0002` — exit criteria, phase status, links | A phase completes or scope shifts |
| **Engineering design** | This file + `maestro/README.md` — how to run, artifact layout, open questions | Harness behavior or folder contract changes |
| **Operator runbook** | `maestro/README.md`, scripts `--help` | Commands, env vars, prerequisites change |
| **Traceability matrix** | `product-docs/components/mobile/spec-test-traceability.md` (+ global row if promoted) | A new test layer is **real** (named suite, how to run) |
| **Test plan slice** | `product-docs/global/backlog/test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md` | Before Phase A; revise when layers or deferrals change |
| **Run evidence** | `maestro/sessions/<session>/` (gitignored) — `meta.md`, logs, `summary.jsonl` | Every session; never copy into product-docs |

**Do not** duplicate run logs or per-iteration narratives into backlog files. **Do** add a dated **Execution** bullet under `I-2026-0019` / **Pilot status** under `TB-2026-0002` when a phase gate is met (what passed, what is deferred).

### Skills by gate (invoke explicitly in session)

| Gate | Skill | Outcome |
| --- | --- | --- |
| Before Phase A code | `test-strategy-planner` | Test plan slice: unit / functional / operational / Maestro layers, deferrals |
| Optional if harness touches new boundaries | `component-design-pack` | Thin mobile + `ezkey-tests` boundary note (JUnit creates attempt, Maestro consumes) |
| After Phase A or B passes on hardware | `traceability-sync` | New row or gap in `components/mobile/spec-test-traceability.md`; link harness doc |
| End of Phase A or B | `closeout` | TB status note, residual risks, next phase id |
| Mid-phase design doubt only | `grill-me` | Short risk list; avoid re-writing V/I/TB |
| Session started from `.cursor/plans/` only | `plan-incubation` | Materialize deltas into TB/I; do not treat as retrofit |

**Not needed each phase:** `vision-intake`, `tracer-bullet-promote` (already promoted), `legacy-plan-miner` / `retrofit-curator` (historical lane only).

### Phase documentation checklist

**Phase A — skeleton (one iteration end-to-end)**

- [x] Test plan slice created or updated (`test-strategy-planner`) — [`TSP-2026-06-26-mobile-real-device-churn-harness.md`](../../product-docs/global/backlog/test-plans/TSP-2026-06-26-mobile-real-device-churn-harness.md).
- [x] TB: mark Phase A **in progress**; link script/test class names once they exist.
- [x] This doc: confirm artifact paths match implementation (contract unchanged; interim scripts noted in **Implementation status**).
- [x] `maestro/README.md`: link to session runner when added (pilot runner + campaign scripts documented).
- [ ] After green run: TB **Execution** line + `traceability-sync` (gap: “real-device churn harness — Phase A” until matrix row is formal).
- [ ] `closeout` for Phase A only (TB stays `active`).

**Phase B — deterministic multi-iteration happy path**

- [ ] Test plan slice: operational layer marked **run now** for bounded N iterations.
- [ ] `spec-test-traceability.md`: one matrix row or open-gap closure for `F-auth-pending-respond` + real-device loop.
- [ ] TB exit criterion **3** (short loop): honest yes/partial with link to command.
- [ ] `closeout` + traceability sync.

**Phase C / D** — repeat checklist; add post-pass script to operator doc only when the script exists.

### Anti-rot rules

1. **One narrative home** — motivation stays in `I-2026-0019`; execution status in `TB-2026-0002`; mechanics here and in `maestro/README.md`.
2. **No orphan plans** — if `.cursor/plans/ezkey_mobile_android_real_device_automation.plan.md` changes, add one line under TB **Links** or **Pilot status**; do not fork a second design doc.
3. **Evidence is files on disk** — session folders are the lab notebook; product-docs only point to the contract.
4. **Update matrices when tests exist** — not when ideas are discussed.
5. **Parked vs done** — use `closeout` **parked** with a review date if Phase B slips; avoid leaving TB ambiguously “almost done”.
