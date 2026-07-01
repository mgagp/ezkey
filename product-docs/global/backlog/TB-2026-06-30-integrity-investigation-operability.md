# Tracer Bullet Brief — `TB-2026-06-30` Integrity investigation operability (B2.5)

## Metadata

- **ID:** `TB-2026-06-30-integrity-investigation-operability`
- **Status:** `ready`
- **Related idea:** `I-2026-0005-checkpoint-integrity-break-remediation`
- **Parent context:** Wave B integrity cluster; follows B2 reconciliation path
- **Feature:** `F-audit-chain`
- **Lane:** `A` (admin-api / core verification DTOs + Admin UI investigation UX)
- **Posture:** `iterative` — single vertical after B2 merge; closes investigation gap before B3
- **GitHub issue:** #269 (Wave B program umbrella)
- **Created at:** `2026-06-30`
- **Depends on:** B2 reconcile API + `AUDIT_INTEGRITY_RUPTURE` alert producer (B1)

## Objective

Close the **investigation / forensic operability** gap: when integrity validation detects entry
HMAC or chain violations, a Global Admin can **identify affected records**, **navigate** to evidence,
and see **honest** integrity presentation — without re-running ad hoc SQL or log diving.

Resolution (reconcile) remains in B2; B2.5 owns **identify → understand → then resolve**.

## Problem signal

See method log
[`ML-2026-06-30-wave-b-b25-investigation-operability-kickoff.md`](method-logs/ML-2026-06-30-wave-b-b25-investigation-operability-kickoff.md).

## Design decisions (maintainer grill — sequential)

### D1 — Persistence: where violation detail is stored and retrieved

**Status:** `decided` (2026-06-30)

**Question:** When nightly or ad hoc verification finds entry HMAC mismatches, where should the
operator-trustworthy list of affected `auditLogId`s live?

**Decision: Option E — combined surfaces, distinct roles**

| Surface | Role | Content |
|---------|------|---------|
| **Alert payload** | Investigation **snapshot** at detection time (summary + structured lists) | `entryViolations[]` and `chainViolations[]` (structured); retain existing counts and boundaries |
| **API** (`integrity-check`, `chain-integrity`) | **Operational truth** on demand | Same structured violation shapes as alert lists |
| **Nightly completion audit event** | **Immutable compliance trace** | Capped copies of the same structured lists in `event_details` (cap per D2) |

**Excluded:** dedicated DB column or parallel violation table (option D) — integrity remains
**derived by recompute**, not a mutable stored flag.

**UI posture (D1.2):** The alert carries its point-in-time observation (healthy separation). When
the operator opens an open `AUDIT_INTEGRITY_RUPTURE` alert, the Admin UI **automatically re-runs**
entry and chain verification over the alert window (boundaries from payload). The UI presents
**snapshot + live confirmation** side by side (or snapshot then refreshed panel) so the operator
sees both “what was detected” and “what verifies right now” without manual button friction.

**Chain violations (D1.3):** Parity with entry violations — structured `chainViolations[]` in
alert, API chain report, and capped nightly event (not text-only strings for operator-facing
surfaces). Text messages may remain as a human-readable `detail` field per item.

---

### D2 — Cap: truncation and overflow UX

**Status:** `decided` (2026-06-30)

**Alert payload (`AUDIT_INTEGRITY_RUPTURE`):** cap **25** `entryViolations`, **10** `chainViolations`.
Each list includes `totalCount`, `returnedCount`, and `truncated: true` when capped. Overflow UX:
banner + live re-verify (API) shows full results.

**API (`integrity-check`, `chain-integrity`):** **no cap** on the requested window. Catastrophic
mass-corruption is out-of-band (operator shutdown); API simplicity preferred over heap-protection
capping.

**Nightly completion audit event (`NIGHTLY_INTEGRITY_VALIDATION_COMPLETED`):** cap **10** entry, **5**
chain in `event_details` (choice **A**). Lists include totals + `truncated` when capped. Role:
lightweight **immutable compliance footprint** — sample IDs + counts for SOC2-style traceability;
full investigation remains alert + API re-verify.

**Snapshot vs live divergence:** UI labels **“At detection”** (alert payload) vs **“Verified now”**
(auto re-verify on alert open). Counts may differ if DB was repaired between raise and investigation.

---

### D3 — Badge semantics: signed vs verified vs known violation

**Status:** `decided` (2026-06-30)

**No DB persistence** — display derived from `entry_hmac` presence + verification results in session.

**List column states:**

| State | Condition | Display |
|-------|-----------|---------|
| **Unsigned** | No `entry_hmac` | Muted dash |
| **Signed** | HMAC present, not verified in session context | Neutral shield — **not green** |
| **Verified OK** | ID confirmed intact by latest applicable verify run | Green shield |
| **Known violation** | ID in latest verify report or alert live re-verify | Red shield |

**Violation scope (D3.2):** Both required:

1. **Per-row** red/green on violated IDs when visible on current list page.
2. **Banner** when open integrity context has violations in the verification window but **none on
   the current page** (pagination): e.g. *« 3 integrity violations in this window — not on this
   page »* with action to navigate to affected entries (links or filtered view). Prevents false
   reassurance when viewing page 1 of 25 with violations on another page.

**Entry detail dialog (D3.3):** On every open, call `GET /audit-logs/{id}/integrity-check`.
Labels: Verified intact / Integrity violation / Unsigned — remove misleading « Chain intact » for
signed-only rows.

**Session cache lifetime (D3.4):** Violation/verify context retained until **logout**; invalidate
after successful integrity rupture **reconcile** (and on explicit refresh of verification).

**Tooltip copy:** Honest distinction between signed (tamper-evidence field present) and verified
(recomputed HMAC match).

---

### D4 — Unified investigation UX (alert → audit logs)

**Status:** `decided` (2026-06-30)

**Primary CTA on open `AUDIT_INTEGRITY_RUPTURE` alert:** **Investigate in Audit Logs** — navigates to
`/audit-logs` with window pre-filled and integrity context active.

**Alert detail layout:** Two sections — **Entry HMAC violations** and **Chain violations** — each
with structured rows and **distinct deep links**:

- Entry row → `highlightAuditLogIds`, `anchorAuditLogId`, date window from boundaries.
- Chain row → `integrity=1`, `focusCheckpointId`, timeline focus (same pattern as gap).

**Query params (audit-logs):**

| Param | Role |
|-------|------|
| `integrity=1` | Expand Integrity & Lifecycle panel |
| `createdAfter` / `createdBefore` | Alert window (`failBoundary` / `resumeBoundary` or window start/end) |
| `highlightAuditLogIds` | CSV of violated entry IDs (from live re-verify) |
| `focusCheckpointId` | Chain violation anchor (also fixes gap-alert debt) |
| `source=integrity-alert` | Contextual banner + session cache (D3) |

**Pagination honesty (D3 + D4):** Banner when violations exist in window but not on current page;
explicit action **Show only affected entries** (not applied by default on navigate).

**Gap debt (in scope):** Implement consumption of `focusCheckpointId` from gap alerts (link already
emitted in `alert-detail.tsx` but previously ignored in `audit-logs.tsx`).

**Reconcile** remains on alert detail — investigation precedes resolution.

---

## Structured violation shapes (normative)

**Entry violation item:** `auditLogId`, `eventType`, `createdAt`, `reason` (e.g. `HMAC_MISMATCH`,
`MISSING_ENTRY_HMAC`).

**Chain violation item:** `checkpointId`, `windowStart`, `windowEnd`, `violationType`, `detail`
(human-readable).

**List wrappers:** `entryViolations`, `entryViolationsTotalCount`, `entryViolationsTruncated`;
same pattern for `chainViolations*`.

---

## In scope

### Slice 1 — Backend (core + admin-api)

1. Shared DTO records for entry/chain violation items and capped lists.
2. `AuditIntegrityService.verifyRange` — return structured `entryViolations` (API uncapped).
3. `AuditChainVerificationService` — structured `chainViolations` alongside existing messages.
4. `NightlyIntegrityValidationService` — enrich alert payload (25/10) and completion event (10/5).
5. OpenAPI on `integrity-check` and `chain-integrity` responses; update `docs/ALERTS.md`,
   `docs/AUDIT_LOG_INTEGRITY.md`.
6. Unit tests for cap logic and payload shapes.

### Slice 2 — Admin UI

1. **Alert detail:** At detection / Verified now; auto re-verify on open; entry + chain tables;
   **Investigate in Audit Logs** CTA; per-row links.
2. **Audit logs list:** Honest HMAC column (D3); pagination banner; **Show only affected entries**;
   `highlightAuditLogIds` row styling; session cache until logout.
3. **Audit log detail dialog:** Live `/{id}/integrity-check` on open.
4. **Integrity panel:** Entry/chain reports show violation lists; implement `focusCheckpointId`
   deep link (gap + integrity).
5. i18n EN/FR for new copy (badges, banners, section titles).

## Implementation sequence

1. Core violation DTOs + service changes + nightly/alert payload.
2. Controller/OpenAPI + spec refresh after stack.
3. Alert detail investigation UX + auto re-verify.
4. Audit logs badges, banner, deep links, gap `focusCheckpointId` fix.
5. Manual lab: tamper → alert → investigate → see IDs → reconcile → badges refresh.

## Validation

- [ ] Maven baseline (root reactor).
- [ ] Unit tests: cap truncation, alert payload, API uncapped lists.
- [ ] clean-start: tamper row → nightly alert → alert shows snapshot → auto re-verify confirms →
      Investigate navigates → affected entry visible (badge + highlight).
- [ ] Pagination banner when violations off current page.
- [ ] Gap alert `focusCheckpointId` link focuses checkpoint timeline.
- [ ] Post-reconcile invalidates session cache; honest badge state.

## Out of scope

- B3 dashboard batch widgets (`I-2026-0007`)
- Snooze (C9)
- New DB integrity-status column or parallel incident store
- External ITSM

## Links

- Kickoff ML: [`method-logs/ML-2026-06-30-wave-b-b25-investigation-operability-kickoff.md`](method-logs/ML-2026-06-30-wave-b-b25-investigation-operability-kickoff.md)
- B2 TB: [`TB-2026-06-28-manipulation-integrity-remediation.md`](TB-2026-06-28-manipulation-integrity-remediation.md)
- Design pack: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- Runtime doc: [`../../../docs/AUDIT_LOG_INTEGRITY.md`](../../../docs/AUDIT_LOG_INTEGRITY.md)
