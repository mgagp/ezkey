# Backlog Idea — `I-2026-0005` Checkpoint integrity breaks: declared remediation and reattachment

## Metadata

- **ID:** `I-2026-0005`
- **Status:** `incubating`
- **Priority:** `P1`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-06-05`
- **Last reviewed at:** `2026-06-05`
- **Phase tags:** `P2-hardening`
- **Component tags:** `admin-api`, `audit`
- **Captured by:** Marc

## Intent

Generalize integrity **rupture handling** for period-based validation: any irregularity in the examined window (gap, heartbeat/partial failure, or data manipulation) is surfaced as an **actionable alert** for the Global Admin, prioritized and resolved **in sequence**. This item owns the **manipulation** family end-to-end (alert with fail/resume boundaries, investigation, justification, reintegration, crypto bridge). **Gap** and **heartbeat** families reuse existing flows (revise heartbeat path only if gaps found). R1 is self-contained and bounded — no external ITSM, no archive/sealed export workflows.

## Problem and value

- **Problem:** Today, a full outage gap is handled with an explicit gap declaration that justifies the quiet period. A partial degradation (for example, `admin-api` down) trips Integration API and Auth API into degraded mode and emits an alert. But when an audit row is deleted manually, integrity validation correctly **detects** the break, yet there is no symmetric mechanism to (a) raise an alert, (b) declare and explain the break, (c) rectify, and (d) reattach the broken sequence so that the dashboard reflects an explained-and-recovered state rather than an indefinite "broken" state.
- **Expected value:** Operational continuity in the face of detected integrity tampering or accidental data loss; explicit, auditable record of the incident and the remediation; consistency with the gap-declaration model already in place for outages.

## Scope

- **In scope:**
  - **Rupture taxonomy** for period validation: (1) gap / generalized downtime, (2) heartbeat / partial failure, (3) manipulation (add/delete/modify).
  - **One actionable alert per distinct irregularity** in the validation window; priority ordering for Global Admin (manipulation highest, gap lowest by default).
  - **Manipulation path:** alert payload includes where cryptographic continuity **fails** and where it **resumes**; operator justification; period reintegration; crypto bridge; validator reports healthy after resolution.
  - Reuse **existing alert table** as operator queue; extend types/payload — no new parallel incident universe for R1.
  - Align gap and heartbeat alerts with existing gap-declaration and partial-failure reconciliation (audit/revise heartbeat completeness only).
  - Conciliation persistence on existing incident/checkpoint bridge (see INC-1 in grill session).
- **Out of scope:**
  - Proactive retroactive validation schedule/window — `I-2026-0006`.
  - Dashboard widgets — `I-2026-0007`.
  - Audit archival export and sealed-audit archive workflows (FSM exists; archive is future).
  - Backfill of historical undetected breaks.
  - External ticketing systems (optional reference in justification text only).

## Grilling decisions (2026-05-17, in progress)

Full session log: [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md) — **resume at C8**. **Design pack:** sufficient for D4 after C7 (see grill session).

| Topic | Decision |
|-------|----------|
| **Roles** | Global Admin only for investigation and reattachment; Tenant Admin keeps audit read access only (no crypto-ops surface). |
| **R1 reattachment** | Mandatory explicit cryptographic bridge; acceptance = existing integrity validator reports continuity OK for reconciled period. |
| **Detection (R1)** | Period validation (batch + ad hoc) surfaces **all** irregularities; each enters resolution. |
| **C7 — Multiple ruptures** | Three families (gap / heartbeat / manipulation); **one alert per distinct finding**; priority-ordered queue; operator resolves **in sequence**; human-only root-cause explanation. |
| **C7 — Manipulation alert** | Must include fail boundary + resume boundary (if any) within validation context. |
| **C7 — Reuse** | Gap → existing gap declaration; heartbeat → existing partial-failure path (revise if incomplete). |
| **Operator UX** | Actionable alert queue → per-type resolution flow; external ticket id optional in justification. |
| **Persistence (R1)** | **Alerts-first** queue; conciliation on existing incident/checkpoint bridge. |
| **Benign / false positive** | Same technical path; differentiate via justification text (+ optional external ticket id). |
| **C8 — Degraded mode** | Admin API down ⇒ no validation batches. Heartbeat alerts from Integration/Auth only. **Priority 0:** restore Admin + heartbeat before manipulation work. Degraded blocks auth ops until heartbeat cleared. |
| **C8 — Re-alert** | Closed manipulation alert never reopened; repeat failure ⇒ new alert. |
| **C9 — Stale / ignored** | No escalation; alerts visible until closed; degraded persists; no auto-close. |
| **C9 — Snooze (R1)** | Global Admin: justification + audit; default 24 h, duration via externalized property; renewable; **preferred** over property-only master override. |
| **C9 — Batch staleness** | No alert-on-alert; use `I-2026-0007` batch last-run widgets. |
| **C9 — Widget honesty** | Open alerts ⇒ dashboard caveat on batch/integrity widgets (`I-2026-0007`). |
| **Future** | `V-2026-0013` meta-resolution (GOD_RESOLUTION) — not R1. |
| **Open — C8-6** | Classify outage gap vs manipulation before alert — suspend; design pack. |
| **Open — INC-1** | Justification integrity vs primary audit chain — close in design pack (recommend conciliation **audit event** + operational alert index). |
| **Related** | `I-2026-0021` (Postgres role matrix), `V-2026-0012` (future batch-api split) |

## Observation note — heartbeat false-positive loop (2026-06-05)

Docker stack log review found a repeatable heartbeat loop rather than a real checkpoint outage:

- `AuditChainHeartbeatGuardService` transitioned `OK -> UNSUPERVISED_ACTIVITY -> DEGRADED_SERVICE`
  every five-minute window, then resolved about one minute later.
- Recent checkpoints were contiguous (`0` discontinuities across the regular checkpoint range
  inspected) and chain verification reported `status=OK`, `gaps=0`, `undeclaredGaps=0`, and
  `violations=0`.
- The operational side effect was severe alert/incident noise: hundreds of
  `RECOVERED_PENDING_DECLARATION` heartbeat incidents and repeated
  `AUDIT_CHAIN_HEARTBEAT_STALE` alert raise/resolve audit rows.

Root cause hypothesis: the checkpoint cron ran at exact second zero (`0 */5 * * * ?`). In Docker,
the scheduled method often executed a few milliseconds before the five-minute wall-clock boundary.
`AuditChainScheduler` correctly refused to seal a technically incomplete window, so the just-ending
window was deferred to the next tick. Heartbeat supervision still evaluated
`fail_closed_not_before = latest.window_end + 2 * window_minutes - stop_before_next_window`
(`+9 minutes` with defaults), so it entered `DEGRADED_SERVICE` shortly before the deferred
checkpoint appeared at `+10 minutes`.

Corrective direction: keep the documented heartbeat thresholds, but run the Admin API checkpoint
cron just after the boundary (`1 */5 * * * ?`). This preserves the intended "completed window first,
then grace, then degraded" semantics without sealing a window early or creating a one-cycle false
positive loop. `I-2026-0022` should capture this boundary rule in the scheduled jobs catalog.

## Key assumptions

- The current detection logic for sequence breaks is correct and reusable as the trigger.
- Symmetry with gap-declaration and partial-failure conciliation keeps the operator mental model coherent.
- Reattachment restores validator-visible continuity; it must not be narrative-only in R1.
- External ITSM (Jira, etc.) is out of scope; optional ticket reference in justification only.

## Risks and exceptions

- Reattachment semantics must be conservative: justification explains, bridge restores continuity — not a way to rewrite history undetected. Strong guards required.
- **Parallel audit chain risk:** if incidents/checkpoints hold authoritative justification without integrity guarantees, assurance may shift to a weaker store — resolve in grill session INC-1 without infinite “validator of validator” recursion.
- Alert noise: integrity-break detection should be rare; tuning must avoid floods if misconfiguration triggers false positives.
- Benign causes (filesystem, DB corruption, accidental DBA edit, partial restore, replication lag) use the same remediation path with explicit justification category.

## Promotion notes

Grilling: C7 settled 2026-05-19 — **ready for component design pack** on manipulation path + alert queue model. Close C8–C9 and INC-1 in grill session or design pack. Align with `I-2026-0006` / `I-2026-0007` before `TB-*`.

## Links

- Grill session (D4 blocks complete; resume **D5**): [`../grill-sessions/integrity-cluster-D4-D6-grill-me.md`](../grill-sessions/integrity-cluster-D4-D6-grill-me.md)
- Related vision: `V-2026-0013` (future meta-resolution, not R1)
- Related vision: `V-2026-0004` (integrity validation strategy)
- Related backlog: `I-2026-0006` (automatic detection batch), `I-2026-0007` (dashboard visibility)
- Related feature: `F-audit-chain`
- Related principles: `#1` (simplicity), `#11` (lifecycle without surprise), `#12` (security as posture)
