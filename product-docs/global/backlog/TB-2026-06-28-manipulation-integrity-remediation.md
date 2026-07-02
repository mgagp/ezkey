# Tracer Bullet Brief — `TB-2026-06-28` Manipulation integrity rupture remediation

## Metadata

- **ID:** `TB-2026-06-28-manipulation-integrity-remediation`
- **Status:** `completed`
- **Related idea:** `I-2026-0005-checkpoint-integrity-break-remediation`
- **Parent context:** Wave B integrity cluster; design pack
  [`integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- **Feature:** `F-audit-chain`
- **Lane:** `A` (admin-api / core lifecycle + alert resolution + Admin UI)
- **Posture:** `iterative` — slice 1 = reconcile API + crypto bridge + alert close; slice 2 = Admin UI
  operator flow; slice 3 (optional follow-on TB) = snooze (`C9`)
- **GitHub issue:** #269 (Wave B program umbrella)
- **GitHub branch:** `feature/269-i-2026-0005-manipulation-integrity-remediation`
- **Created at:** `2026-06-29`
- **Depends on:** B1 merged (PR #270) — `AUDIT_INTEGRITY_RUPTURE` alert producer + nightly batch

## Objective

Deliver the **manipulation-family remediation path** (`I-2026-0005`): when period validation
(nightly or ad hoc) surfaces an integrity rupture that is **not** explainable by gap declaration or
heartbeat conciliation alone, a Global Admin can **justify**, **cryptographically re-bridge** the
chain for the affected window, and **close** the matching `AUDIT_INTEGRITY_RUPTURE` alert so
subsequent validation passes treat the period as conciliated (D5 skip rule).

## Current state

- **Detection:** `NightlyIntegrityValidationService` + ad hoc `verifyChain` — shipped (B1).
- **Alert:** `AUDIT_INTEGRITY_RUPTURE` with fail/resume boundaries in payload — shipped (B1).
- **Gap path:** `POST /lifecycle/declare-gap` + `GAP_DECLARED` alert resolution — shipped.
- **Heartbeat path:** `AuditChainHeartbeatGuardService` + incident declare + `HEARTBEAT_RESTORED` —
  shipped.
- **Manipulation reconcile:** **not** shipped — no lifecycle endpoint, no conciliation checkpoint
  type, no alert resolution reason, no Admin UI flow.

## In scope (slice 1 — first vertical)

1. **Lifecycle API** (Global Admin): reconcile an open `AUDIT_INTEGRITY_RUPTURE` alert (by `alertId`
   or stable `dedupeKey`) with:
   - operator **justification** (min length aligned with existing lifecycle reason fields),
   - optional **external ticket reference**,
   - **fail boundary** / **resume boundary** (must match or be compatible with alert payload),
   - explicit **conciliation category** (e.g. accidental DBA edit, corruption, investigated benign).
2. **Crypto bridge:** insert a signed **`MANIPULATION_CONCILIATION`** checkpoint (or equivalent)
   spanning the reconciled discontinuity; re-chain downstream checkpoints (same pattern as
   `declareGap` post-gap re-chain).
3. **Audit meta-event:** first-class conciliation event (INC-1) — e.g.
   `AUDIT_INTEGRITY_RUPTURE_CONCILIATED` — JSON `event_details`; incidents/checkpoints remain
   operational index only.
4. **Alert resolution:** `alertService.resolveByDedupeKey(..., INTEGRITY_RUPTURE_CONCILIATED,
   adminId)` — new `AlertResolutionReason`; **never reopen** closed manipulation alert (C8-7).
5. **Verification skip:** extend `AuditChainVerificationService` / nightly orchestration to **skip**
   sub-ranges covered by resolved manipulation conciliation (alongside gap + heartbeat).
6. **Acceptance test:** after reconcile, `verifyChain` over the window reports intact for the
   conciliated period.
7. **Postman** + `docs/ALERTS.md` + `CONFIGURATION.md` updates for the new endpoint.
8. **Unit tests** for bridge logic and alert resolution wiring.

## In scope (slice 2 — same TB, after slice 1)

1. **Admin UI:** from `/alerts/{id}` for `AUDIT_INTEGRITY_RUPTURE` — structured payload view (B1) +
   **Reconcile** action (dialog: justification, category, confirm boundaries).
2. **i18n** EN/FR for flow copy and success/error states.
3. **Help topic** (optional, minimal) under `help` namespace.

## Out of scope (this TB)

- Dashboard batch widgets (`I-2026-0007` / B3).
- **Snooze** API/UI (`C9`) — defer to optional slice 3 or separate TB unless trivial during B2.
- Archive export / sealed-audit workflows.
- Backfill of historical undetected breaks.
- External ITSM integration (ticket id is optional text field only).
- Tenant Admin write access to reconciliation.
- `V-2026-0013` GOD_RESOLUTION meta-resolution.

## Design decisions (from design pack + grill)

| Decision | Choice |
|----------|--------|
| Queue model | Alerts-first; conciliation on checkpoint bridge + audit event |
| Roles | Global Admin only for reconcile |
| Re-open | Never — repeat failure → new alert (C8-7) |
| Acceptance | Existing validator green for reconciled window |
| Benign vs fraud | Same technical path; differentiate in justification category/text |
| Heartbeat priority | Operator order: restore heartbeat before manipulation work when both open (C8-11); API may warn or block reconcile while `AUDIT_CHAIN_HEARTBEAT_STALE` open — TB chooses **block** for R1 |
| Complexity guard | Red-line test in [`integrity-assurance-honest-line.md`](../../integrity-assurance-honest-line.md) |

## Implementation sequence

1. DTOs + `AuditLifecycleService.reconcileIntegrityRupture(...)` (mirror `declareGap` re-chain).
2. Controller endpoint on `AuditLogController` under `/lifecycle/...`.
3. New `EventType`, `AlertResolutionReason`, checkpoint type constant.
4. Verification skip queries for conciliated windows.
5. Tests + Postman + docs.
6. Admin UI slice (slice 2).

## Validation

- [x] Maven baseline (root reactor).
- [x] Unit tests for conciliation bridge + alert resolution.
- [x] clean-start lab: tamper audit row → nightly/ad hoc alert → reconcile → verify green (after V14
  `checkpoint_type` widen — see
  [`ML-2026-07-01-b2-reconcile-checkpoint-type-varchar-gap.md`](method-logs/ML-2026-07-01-b2-reconcile-checkpoint-type-varchar-gap.md)).
- [ ] Regression: gap declare + heartbeat paths unchanged (not re-run in final lab pass).
- [x] Admin UI reconcile flow (slice 2) — manual exploratory on clean-start.

## Links

- Idea: [`ideas/I-2026-0005-checkpoint-integrity-break-remediation.md`](ideas/I-2026-0005-checkpoint-integrity-break-remediation.md)
- Design pack: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- Grill: [`grill-sessions/integrity-cluster-D4-D6-grill-me.md`](grill-sessions/integrity-cluster-D4-D6-grill-me.md)
- Vision: [`../../vision/V-2026-0004-integrity-validation-strategy.md`](../../vision/V-2026-0004-integrity-validation-strategy.md)
- B1 TB (completed): [`TB-2026-06-28-nightly-integrity-validation-batch.md`](TB-2026-06-28-nightly-integrity-validation-batch.md)
- Runtime alerts: [`../../../docs/ALERTS.md`](../../../docs/ALERTS.md)
- Honest line: [`../../integrity-assurance-honest-line.md`](../../integrity-assurance-honest-line.md)
