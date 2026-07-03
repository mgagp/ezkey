# Tracer Bullet Brief — `TB-2026-07-02` Retroactive integrity validation operability (B2.7)

## Metadata

- **ID:** `TB-2026-07-02-retroactive-integrity-validation-operability`
- **Status:** `done` (merged PR #287 — program #269)
- **Related idea:** `I-2026-0006-nightly-integrity-validation-batch` (extends detective layer operability)
- **Parent context:** Wave B integrity cluster; companion to B2.6 entry conciliation
- **Feature:** `F-audit-chain`
- **Lane:** `A` (core orchestration refactor + admin-api operator trigger + Admin UI)
- **Posture:** `iterative` — slice 1 = shared orchestration + POST trigger; slice 2 = Admin UI Verify vs
  Run detection; depends on B2.6 D4 dedupe for full duplicate-alert safety (may ship in same program
  branch or immediately after B2.6 slice 1)
- **GitHub issue:** #269 (Wave B program umbrella)
- **Created at:** `2026-07-02`
- **Depends on:** B1 nightly batch (orchestration baseline), B2.5 investigation UX; **requires**
  B2.6 D4 (incident fingerprint dedupe) and D6 (entry skip) — decided in companion TB

## Objective

Generalize the **detective layer** beyond the nightly cron: one **retroactive integrity validation**
orchestration used by (1) the scheduled batch and (2) **operator-initiated** runs over a chosen window,
producing the **same** alert, audit completion event, and registry semantics as nightly — without
waiting for the next off-peak pass.

Improve **operability** for labs, EXP1 soak, development with `nightly.enabled=false`, and functional
validation of detect → alert → investigate → reconcile — while preserving the honest two-layer model
(rolling attach vs retroactive detect).

## Problem statement — session analysis (2026-07-02)

B1 delivered `NightlyIntegrityValidationService` + scheduler — correct for normative **automatic**
detection, but the implementation name and entry points encode **schedule-only** semantics:

| Need | Today | Gap |
|------|-------|-----|
| Automatic daily detection | Cron → `validateWindow(now)` | Shipped (B1) |
| Forensic examine (no side effects) | `GET integrity-check` / `chain-integrity` | Shipped (B2.5) |
| **Operator run detection now** | Wait for cron or indirect lab hacks | **Missing** |
| Same alert + completion audit as nightly | Only nightly path | **Missing** |
| Dev without nightly cron | Verify GET works; no detect+alert | **Partial** |
| Full E2E test without 24 h wait | Tamper + wait / shorten cron in profile | **Friction** |

The earlier session analysis concluded:

- **Do not** attach alert creation to existing **GET** verify buttons (idempotency, accidental alerts).
- **Do** extract shared orchestration callable by scheduler **and** Global Admin POST.
- **Do** separate UI modes: **Verify** (read-only) vs **Run validation** (detect + optional alert per D8).
- **Do** keep `ezkey.audit.integrity.nightly.enabled` as **scheduler-only** switch; ad hoc detect remains
  available when HMAC is active (D5-14 posture — informed choice, not forced cron).
- **Require** B2.6 incident fingerprint dedupe so on-demand + nightly same day do not spam (design-pack
  D5-13).

This TB materializes that discussion so it is not lost when B2.6 entry conciliation ships first.

## Stakes and limits

### Operability value (not test-only)

- Global Admin can **close the loop** on integrity in minutes after induced or real tamper.
- Installations with nightly disabled retain **punctual compliance passes** without enabling cron.
- EXP1 / clean-start maintainers avoid calendar coupling for Wave B validation.

### Security / honesty unchanged

- Retroactive detect does not replace rolling attach blind spot — document clearly.
- Operator POST is **Global Admin only** (same as lifecycle reconcile).
- POST is a **mutating** act: may raise/touch alerts and write completion audit — not a silent verify.

### Non-goals

- Replacing GET verify endpoints.
- Alert on batch infra failure (C9 — registry/widget only).
- Snooze (C9).
- Tenant Admin access.

## Target architecture — generic retroactive validation

### Naming refactor (code — slice 1)

| Current (B1) | Target (B2.7) | Notes |
|--------------|---------------|-------|
| `NightlyIntegrityValidationService` | `RetroactiveIntegrityValidationService` | Class rename; nightly scheduler remains thin wrapper |
| `NightlyIntegrityValidationScheduler` | Keep name **or** `RetroactiveIntegrityValidationScheduler` | Prefer **keep scheduler name** + javadoc “calls retroactive orchestration” to limit churn |
| `validateWindow(windowEndExclusive)` | `runValidation(from, to, options)` | Explicit inclusive/exclusive bounds; nightly computes `from = end - windowHours` |
| `NightlyIntegrityValidationResult` | `RetroactiveIntegrityValidationResult` | Add `triggerSource` field |
| Event action `nightly-integrity-validation` | `retroactive-integrity-validation` | `event_details.triggerSource`: `SCHEDULED` \| `OPERATOR` |
| Registry job key `NIGHTLY_INTEGRITY_VALIDATION` | Unchanged for scheduled runs | Optional second row `RETROACTIVE_INTEGRITY_VALIDATION_OPERATOR` or scope suffix only (see D3) |

**Properties prefix:** Keep `ezkey.audit.integrity.nightly.*` for **schedule + default window-hours**.
Add optional `ezkey.audit.integrity.retroactive.operator-max-window-hours` (default = same as nightly
window) to cap operator-selected ranges (abuse guard).

### Orchestration contract (single path)

```text
RetroactiveIntegrityValidationService.runValidation(from, to, options)
  1. Skip if HMAC inactive (same as today)
  2. Collect entry violations (+ B2.6 alert-eligible filter when present)
  3. verifyChain(from, to)
  4. Apply C8-6 classifier (unchanged)
  5. raiseOrTouch AUDIT_INTEGRITY_RUPTURE if needed (B2.6 D4 dedupe key)
  6. emit completion audit (NIGHTLY_INTEGRITY_VALIDATION_COMPLETED → rename or generalize event type?)
  7. update job registry (per trigger rules D3)
  8. return structured result to caller
```

**Event type naming (open):** Keep `NIGHTLY_INTEGRITY_VALIDATION_COMPLETED` with `triggerSource` in JSON
(min churn) **or** introduce `RETROACTIVE_INTEGRITY_VALIDATION_COMPLETED` (clearer). Recommendation:
**keep event type**, add `triggerSource` + `requestedByAdminId` when `OPERATOR`.

## Design decisions (maintainer alignment — D1–D6 decided 2026-07-02)

### D1 — POST operator trigger (not GET)

**Status:** `decided` (2026-07-02)

**Endpoint:** `POST /api/v1/audit-logs/integrity-validation/run` on `AuditLogController` (same resource
tree as `GET …/integrity-check`). Global Admin only. **HTTP 200** synchronous.

**Request body:**

```json
{
  "from": "2026-06-29T22:00:00Z",
  "to": "2026-06-30T22:00:00Z",
  "raiseAlert": true
}
```

| Field | Rule |
|-------|------|
| `from` | Required, inclusive ISO-8601 UTC (same as GET verify) |
| `to` | Required, exclusive (same as GET verify) |
| `raiseAlert` | Optional, **default `true`**; `false` = completion audit only, no `raiseOrTouch` |

**Validation (RFC 9457 400):** missing bounds; `to` not after `from`; window exceeds
`ezkey.audit.integrity.retroactive.operator-max-window-hours` (**hard reject**, no silent truncate;
default cap = `nightly.window-hours`).

**HMAC inactive:** **200** with `skipped: true` + reason (parity with nightly batch — not 503).

**Response (`RetroactiveIntegrityValidationResult`):** `triggerSource=OPERATOR`, bounds, `intact`,
`alertRaised`, `alertId` (when raised), `skipped`, chain/entry counts including separate
`entryAlertEligibleCount` (D6), human `scope`.

**Idempotence:** B2.6 D4 incident fingerprint → `raiseOrTouch`, not duplicate OPEN rows.

**Excluded:** side effects on GET verify endpoints; operator `reason` field on POST (completion audit
is the trace).

---

### D2 — Scheduler decoupled from orchestration name

**Status:** `decided` (2026-07-02)

- Rename orchestration: `NightlyIntegrityValidationService` → **`RetroactiveIntegrityValidationService`**
  with `runValidation(from, to, options)`.
- **Keep** scheduler class name `NightlyIntegrityValidationScheduler` + javadoc « calls retroactive
  orchestration » (limit churn).
- `ezkey.audit.integrity.nightly.enabled=false` → **scheduler idle only**; operator POST still allowed
  when HMAC active.
- Docker/clean-start default: `enabled=true` (unchanged).
- **CONFIGURATION.md:** nightly off = no automatic scheduled pass — **not** « no integrity detection ».

---

### D3 — Job registry behavior for operator runs

**Status:** `decided` (2026-07-02)

**Option A (R1):** only **scheduled** runs update `NIGHTLY_INTEGRITY_VALIDATION` registry row; operator
POST **does not** update registry (preserves last nightly status for C9/B3 widgets).

Operator outcome visible via: HTTP response; completion audit (`triggerSource=OPERATOR`,
`requestedByAdminId`); optional Admin UI toast (session-only until B3).

---

### D4 — Design-pack D5-13: ad hoc + nightly overlap

**Status:** `decided` (2026-07-02)

Overlapping windows / same calendar day **allowed**. Duplicate OPEN alerts prevented by B2.6 D4
incident fingerprint dedupe — not by forbidding overlap. B2.6 D6 skip prevents re-litigation noise on
Explained entries.

---

### D5 — Admin UI: Verify vs Run validation

**Status:** `decided` (2026-07-02)

| Control | HTTP | Effect |
|---------|------|--------|
| **Verify** (rename from « Run integrity check ») | GET integrity-check + chain-integrity | Reports only |
| **Run validation** (new) | POST integrity-validation/run (`raiseAlert: true`) | Detect path; may raise/touch alert |

- Button copy explicit: « Run validation and raise alert if needed » — **no** confirm dialog R1.
- Response: violations first; if `alertRaised`, link **View alert** / navigate to `/alerts/{id}`.
- Manual verify remains available as **Re-run** affordance (B2.5).

---

### D6 — Relationship to B2.6 + ship order

**Status:** `decided` (2026-07-02)

| B2.6 | B2.7 dependency |
|------|-----------------|
| D4 fingerprint dedupe | **Required** |
| D6 alert-eligible skip | **Required** |
| Conciliation registry | Independent (reconcile unchanged) |

**Event type:** keep `NIGHTLY_INTEGRITY_VALIDATION_COMPLETED`; add `triggerSource` (`SCHEDULED` |
`OPERATOR`) + `requestedByAdminId` in `event_details` when operator-triggered (min enum churn).

**Ship order on #269:** B2.6 slice 1 → B2.7 slice 1 (or combined refactor PR if same service touched);
B2.6 slice 2 UI → B2.7 slice 2 UI.

---

## Operator journey (detect path)

```text
1. (Optional) Verify — GET reports, no alert
2. Run validation — POST over same [from, to]
   → completion audit (triggerSource=OPERATOR)
   → AUDIT_INTEGRITY_RUPTURE raised/touched if violations remain alert-eligible
3. Alerts → investigate (B2.5) → reconcile (B2 + B2.6)
4. Run validation again → no duplicate alert; verify still shows explained violations honestly
```

**With nightly.enabled=false:** Steps 2–4 remain available; step 1 always was.

## In scope

### Slice 1 — Core + admin-api

1. Rename/refactor to `RetroactiveIntegrityValidationService` + `runValidation(from, to, options)`.
2. Nightly scheduler calls shared method (behavior parity tests).
3. `POST …/integrity-validation/run` + DTOs + controller tests.
4. Completion audit: `triggerSource`, optional `adminId` on operator path.
5. `CONFIGURATION.md` updates (nightly vs operator; max window cap).
6. Postman + `docs/AUDIT_LOG_INTEGRITY.md` + `docs/ALERTS.md` (operator trigger subsection).

### Slice 2 — Admin UI

1. Relabel verify button; add **Run validation** with loading/error/success states.
2. i18n EN/FR; link to alert when `alertRaised`.
3. Help topic addendum (integrity panel: verify vs run validation).

## Out of scope

- B3 dashboard widgets for operator last-run (optional session display only).
- Functional test module changes (optional follow-up; POST enables easier ezkey-tests elective).
- Changing rolling `AuditChainScheduler` behavior.

## Implementation sequence

1. Refactor service rename + extract `runValidation` (parity tests against existing nightly tests).
2. POST endpoint + authorization.
3. Audit event + registry policy (D3 option A).
4. Admin UI controls + i18n.
5. Manual lab: tamper → POST run → alert → reconcile (B2.6) → POST again → no dup.

## Validation

- [ ] Maven baseline.
- [ ] Unit: nightly scheduler still calls orchestration; operator POST same alert payload shape.
- [ ] Unit: HMAC inactive → skipped result (both triggers).
- [ ] clean-start: tamper → POST → alert without waiting for cron.
- [ ] `nightly.enabled=false` profile: POST works; scheduler idle.
- [ ] Overlap nightly + POST same window → single OPEN alert touched (with B2.6 D4).
- [ ] GET verify after POST → no additional alert.

## Open questions for maintainer review

1. ~~**Event type**~~ — settled in D6 (keep enum + `triggerSource` in JSON).
2. ~~**raiseAlert flag**~~ — settled in D1 (`false` allowed API; UI defaults true).
3. ~~**Max window cap**~~ — settled in D1 (optional property only); **revised 2026-07-03:** default
   no cap — operator POST uses same `[from, to)` as GET verify (parity with investigation UX).
4. ~~**Ship order**~~ — settled in D6 (B2.6 slice 1 → B2.7 slice 1, or combined refactor PR).
5. ~~**Operator max window vs UI date picker (2026-07-03 lab)**~~ — **settled:** remove default 24h
   cap on operator POST and UI; Run validation uses the same range as Verify. Optional
   `operator-max-window-hours` remains for deployments that want an explicit guardrail. Root cause
   analysis below retained for historical context.

## Root cause analysis — Verify vs Run perceived gap (2026-07-03)

| Observation | Evidence |
|-------------|----------|
| Verify entry HMAC found tamper | Admin API log: `HMAC verification FAILED for audit_log_id=1104` |
| Run validation reported intact | Completion audits 1108/1109: `entryHmacViolationCount=0`, window end `2026-07-03T03:59:59.999Z` |
| Tampered row timestamp | `created_at=2026-07-03T11:56:27Z` — after run window end |

**Conclusion:** Expected behavior given mismatched windows + 24h operator cap forcing a narrower
range than Verify. **Not** a classifier bug. Operator journey in D5 assumes POST over **the same**
`[from, to)` as GET verify; the cap breaks that when Verify uses presets &gt; 24h unless the operator
narrows to a sub-range that still contains the tampered rows (e.g. **Today**, not **Yesterday**).

**UI secondary gap:** `dateRangeToApiParams` used inclusive end-of-day for integrity APIs documented
as exclusive `to`; fixed via `integrityExclusiveDateRangeToApiParams` in Admin UI.

## Links

- Companion: [`TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md`](TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md) (B2.6)
- B1 TB: [`TB-2026-06-28-nightly-integrity-validation-batch.md`](TB-2026-06-28-nightly-integrity-validation-batch.md)
- B2.5 TB: [`TB-2026-06-30-integrity-investigation-operability.md`](TB-2026-06-30-integrity-investigation-operability.md)
- Design pack: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- Idea: [`ideas/I-2026-0006-nightly-integrity-validation-batch.md`](ideas/I-2026-0006-nightly-integrity-validation-batch.md)
- Kickoff ML: [`method-logs/ML-2026-07-02-entry-integrity-conciliation-kickoff.md`](method-logs/ML-2026-07-02-entry-integrity-conciliation-kickoff.md)
