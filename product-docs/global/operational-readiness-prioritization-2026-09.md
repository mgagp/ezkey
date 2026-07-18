# Operational Readiness Prioritization — September 2026 Target

## Metadata

- **Document ID:** `operational-readiness-prioritization-2026-09`
- **Status:** `active`
- **Created at:** `2026-06-28`
- **Last reviewed at:** `2026-06-28`
- **Target milestone:** First **formally operable** Ezkey release line (experimental deployment posture; not production SLA)
- **Horizon:** **September 2026** (~10 weeks from this exercise)
- **Owner:** Marc (maintainer discussion captured below)
- **Supersedes:** Ad-hoc “next P3 slice” choice (Alerts list polish) as the default cold-start priority

## Session restart phrase (cold start)

> **“Where are we?” / “What should be next?”** → Read this document first, then the
> [Integrity cluster grill session](backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md)
> and backlog items **`I-2026-0005`**, **`I-2026-0006`**, **`I-2026-0007`**.

This is the canonical **release-order compass** until the September milestone is reached or this
document is explicitly superseded.

## Context — maintainer discussion (2026-06-28)

After closing the Admin UI operator-experience program (`I-2026-0028`) and the async re-encryption
slice (`I-2026-0002` / PR #265), the natural next item in the operator matrix was **Alerts list
polish** (P3 residual). A prioritization review challenged that default:

1. **Alerts are for exceptional operational events**, not everyday admin work. In Ezkey’s operational
   model, the primary exceptional class is **platform degradation** — especially **Admin API
   unavailability**, because Admin API runs checkpoint scheduling and integrity batches.

2. **Alerts only become meaningfully testable** once the full **integrity + heartbeat + detection**
   pipeline exists: rolling checkpoints, peripheral degraded mode, gap declaration, nightly
   retroactive validation, and (where applicable) manipulation rupture handling.

3. **Project posture today:** experimental deployment only (`EXP1`, clean-start, local stack). There
   is **no production pressure** and no obligation to support live customer environments. The goal
   is to **complete structural work properly**, eliminate unknowns, and **maximize calendar time**
   for soak and scenario simulation before treating a release as “operable in real life.”

4. **Desired end state (September):** structural integrity/alerting work is **reputed complete** for
   R1; then deploy on **EXP1**, leave the system running for **hours, days, or weeks**, and exercise
   scenarios such as:
   - stop Admin API and validate recovery / degraded-mode behavior;
   - induce cryptographic continuity breaks or tampering and validate detection, alert generation,
     gap declaration, nightly batch findings, and operator remediation flows.

5. **UI polish on the Alerts list** (matrix row `draft`, column order TBD) remains valid but is
   **downstream** of making the alert *system* trustworthy and scenario-testable.

This exercise is recorded here so future sessions (human or agent) do not re-derive the same
conclusion from scratch.

## Values compass (how we ordered work)

Applied from [`design-principles.md`](design-principles.md) and
[`operator-alignment-guide.md`](operator-alignment-guide.md):

| Principle | Application to this prioritization |
|-----------|-------------------------------------|
| **#1 Simplicity / 80–20** | Finish one **integrity cluster** end-to-end before cosmetic list polish. |
| **#5 Operator-first** | Global Admin must trust **degraded mode, heartbeat, and integrity** before alert UI columns. |
| **#8 Spec-first, test-driven** | Integrity cluster was **grilled** (D4–D6); design pack → TB → scenario tests is the path. |
| **#9 One canonical place** | This document is the **release-order** home; backlog ideas remain scope homes. |
| **#12 Security as posture** | Audit-chain integrity is the trust anchor; alerts are its operator surface. |
| **Invisible when healthy** | Dashboard/widgets and alert counts matter **after** detection/resolution works. |

## Judgment — defer Alerts P3 UI polish

| Factor | Alerts list polish (matrix P3) | Integrity cluster (`I-0005` / `0006` / `0007`) |
|--------|--------------------------------|--------------------------------------------------|
| **Operator value** | Readability of an existing list | **Correct behavior under failure and tampering** |
| **Testability today** | Static UI review only | End-to-end scenarios (Admin down, gap, manipulation) |
| **Dependency** | Assumes alert types + resolution flows are stable | **Produces** those types and flows |
| **Risk if done first** | Polished UI over incomplete semantics | Unblocks honest EXP1 soak |

**Decision:** **Do not promote** Alerts matrix polish to the next TB until the integrity cluster
reaches its R1 “structurally complete” gate (see below). Update `I-2026-0028` P3 residual accordingly.

### What already exists (baseline — not “done” for release)

Documented in [`docs/ALERTS.md`](../../docs/ALERTS.md) and shipped code:

- Alert table + read API + Admin UI list/detail (read-only).
- Producers: `AUDIT_CHAIN_GAP_PENDING`, `AUDIT_CHAIN_HEARTBEAT_STALE`.
- Heartbeat guard + peripheral **503 degraded** on Auth/Integration APIs.
- Gap declaration auto-resolves gap alerts.

**Not yet R1-complete** per backlog and [`V-2026-0004`](vision/V-2026-0004-integrity-validation-strategy.md):

- **Nightly retroactive validation batch** (`I-2026-0006`) — detective layer for tampering.
- **Manipulation rupture remediation** (`I-2026-0005`) — alert + conciliation + crypto reattachment.
- **Dashboard batch health / open-alert banner** (`I-2026-0007`) — operator “3-second ok/not ok.”
- Design-pack pending items from grill session (INC-1, manipulation UI flow, batch last-run table).

## Critical path — Integrity & operational trust cluster

**Vision anchor:** [`V-2026-0004`](vision/V-2026-0004-integrity-validation-strategy.md) (rolling attach + nightly detect).

**Grill input (complete):** [`integrity-cluster-D4-D6-grill-me.md`](backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md).

**Feature milestone:** `F-audit-chain` in [`features-and-phases.md`](features-and-phases.md) / [`roadmap.md`](roadmap.md) (`P2-hardening`, `in-progress`).

| Order | Backlog ID | Role in cluster | Why this sequence |
|-------|------------|-----------------|-------------------|
| **1** | Design pack → `TB-*` | Turn grilled decisions into bounded delivery slices | Grilling done; TB promotion was the explicit next step (May 2026). |
| **2** | `I-2026-0006` | Nightly retroactive integrity batch (24 h window) | **Detects** anomalies rolling attach cannot promise; feeds alert model. |
| **3** | `I-2026-0005` | Manipulation + unified rupture remediation | Completes **alert → investigate → conciliate → reattach** for tampering. |
| **4** | `I-2026-0007` | Dashboard widgets + open-alert count banner | Operator visibility **after** batch rows and alert flows exist. |
| **5** | `I-2026-0028` P3 **Alerts** + matrix row | List polish, filters, typed payloads | **Testable** only once producers and resolution paths above are stable. |
| **6** | `I-2026-0028` P3 **Audit chain checkpoints** | Embedded / Tier B surface | Follows same investigation posture as audit logs. |

**Heartbeat / degraded mode** is not a separate backlog row but **cross-cuts** steps 2–4: validate
and harden existing `AuditChainHeartbeatGuardService` behavior as part of TB test plans (Admin API
stop/start scenarios).

## Recommended waves (June 2026 → September 2026)

### Wave A — Close operator-list debt (done)

- Tier A/B lists, detail FK parity, retire Related details, async re-encryption triggers.
- Evidence: `I-2026-0028` program closeout, `I-2026-0002` closeout (2026-06-27/28).

### Wave B — Integrity cluster R1 (**closed** 2026-07-03)

1. Component design pack — **done** [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md).
2. GitHub program issue **#269** — **closed** (PRs #270, #287, #288, #289 + B2/B2.5 lineage).
3. B1–B3 + B2.6/B2.7 — **shipped**. Closeout:
   [`backlog/method-logs/ML-2026-07-03-wave-b-integrity-cluster-closeout.md`](backlog/method-logs/ML-2026-07-03-wave-b-integrity-cluster-closeout.md).
4. Scenario tests / EXP1 soak — **ongoing** (Wave D; not blocking Wave C).

**Exit gate for Wave B:** met for R1 implementation; runtime soak continues on EXP1.

### Wave C — Operator UI residuals (**closed** 2026-07-05)

- Alerts list matrix → `implemented` (TB
  [`TB-2026-07-03-admin-ui-alerts-list-polish.md`](backlog/TB-2026-07-03-admin-ui-alerts-list-polish.md) — **done**).
- Dashboard widget signal model → `I-2026-0030` / PR #291 — **done**.
- Audit chain checkpoints matrix row → TB
  [`TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md`](backlog/TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md) — **done** (last Wave C slice).
- Any manual resolve/snooze UI **only if** API contracts exist (today: auto-resolve only).

**Wave C exit gate:** met when checkpoints TB merges and matrix row is `implemented`.

### Wave D — EXP1 soak & simulation (after Wave B gate)

- Deploy current `main` to **EXP1** with extended runtime (days/weeks).
- Run scripted and ad hoc scenarios (maintainer-operated).
- Capture findings as backlog ideas or TB fixes — **not** as premature production support.

## Explicitly deferred (September release lens)

| Item | Reason |
|------|--------|
| Mobile / channel features (`I-2026-0010`, `I-2026-0023`, `I-2026-0024`) | Not on operability critical path for first release line. |
| Profile generator / methodology product (`I-2026-0017`, `I-2026-0018`) | Methodology publication; parallel to product release. |
| HA Docker parity (`I-2026-0003`) | Valuable; not blocking EXP1 experimental soak. |
| Paginated screen analysis ideas (`I-2026-0013`, `I-2026-0014`) | Largely satisfied by completed operator program; incubating unless new gap found. |

## Parallel tracks (if capacity — do not block Wave B)

| ID | Notes |
|----|-------|
| `I-2026-0004` | Promoted P1 — API-key auth configurability; orthogonal to integrity. |
| `I-2026-0011` | Bootstrap activation-code default — operability; schedule after Wave B if bootstrap blocks EXP1. |
| `I-2026-0021` | **Done 2026-07-18** — PostgreSQL migrate/runtime role split delivered and validated; linked P3 hardening remains non-blocking. |

## What “operable release quality” means here

For the **September 2026** target, “operable” means:

1. A Global Admin can **detect and remediate** integrity ruptures through documented flows (not raw DB).
2. **Degraded mode** behavior under Admin API loss is **predictable** and **tested**.
3. **Nightly detection** runs and surfaces anomalies through the **alert model**.
4. Dashboard gives an honest **health snapshot** (batch last-run + open alerts).
5. The team has **calendar time on EXP1** to simulate failures without release pressure.

It does **not** mean: full SOC 2 certification, iOS mobile parity, HA stack, or every P2 backlog
idea closed.

## Discoverability map

| Entry point | Link |
|-------------|------|
| **This document** | You are here — release-order compass |
| Backlog index | [`backlog/index.md`](backlog/index.md) § Current prioritization anchor |
| Roadmap P2 | [`roadmap.md`](roadmap.md) → Milestone `P2-hardening` |
| Session start | [`methodology/session-start-guide.md`](../methodology/session-start-guide.md) § Where are we? |
| Integrity vision | [`V-2026-0004`](vision/V-2026-0004-integrity-validation-strategy.md) |
| Grill decisions | [`integrity-cluster-D4-D6-grill-me.md`](backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md) |
| Runtime alert reference | [`docs/ALERTS.md`](../../docs/ALERTS.md) |
| Operator program residual | [`I-2026-0028`](backlog/ideas/I-2026-0028-admin-ui-operator-experience-follow-up.md) |
| Method log (this exercise) | [`ML-2026-06-28-operational-readiness-prioritization-exercise.md`](backlog/method-logs/ML-2026-06-28-operational-readiness-prioritization-exercise.md) |

## Review cadence

- Revisit when Wave B exit gate is met, or at **2026-08-01** (mid-horizon check), whichever comes first.
- Supersede this document (do not silently edit priorities) if the September target or release definition changes.
