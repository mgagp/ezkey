# Tracer Bullet Brief — `TB-2026-07-02` Entry integrity conciliation & alert coherence (B2.6)

## Metadata

- **ID:** `TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence`
- **Status:** `done` (merged PR #287 — program #269)
- **Related idea:** `I-2026-0005-checkpoint-integrity-break-remediation` (extends manipulation path)
- **Parent context:** Wave B integrity cluster; closes asymmetric chain vs entry remediation gap
- **Feature:** `F-audit-chain`
- **Lane:** `A` (core lifecycle + alert orchestration + Admin UI coherence)
- **Posture:** `iterative` — slice 1 = persistence + detection skip + reconcile wiring; slice 2 = UI +
  dedupe hardening; slice 3 = see companion **B2.7** (retroactive validation operability — not optional
  for full Wave B detect operability, but may land adjacent PR on #269)
- **GitHub issue:** #269 (Wave B program umbrella)
- **Created at:** `2026-07-02`
- **Depends on:** B1 nightly batch, B2 chain reconcile (`MANIPULATION_CONCILIATION`), B2.5 investigation UX

## Objective

Deliver the **entry-level equivalent** of checkpoint `MANIPULATION_CONCILIATION`: when per-entry HMAC
violations are detected, investigated, and explained, persist an **auditable conciliation act** so
subsequent integrity passes **reliably skip re-alerting** while **preserving honest tamper-evidence**
(the row stays HMAC-invalid; we do not re-sign).

Unify **alert coherence** across chain and entry dimensions: one operator queue item
(`AUDIT_INTEGRITY_RUPTURE`) with stable incident identity, partial progress, and discoverable
justification lookup by `auditLogId`.

## Problem statement — the blind spot

Wave B shipped a **symmetric detection** model (nightly + ad hoc verify entry HMAC **and** checkpoint
chain) but an **asymmetric remediation** model:

| Layer | Detection | Remediation today | Re-litigation skip |
|-------|-----------|-------------------|--------------------|
| **Checkpoint chain** | `verifyChain`, nightly | `MANIPULATION_CONCILIATION` bridge + alert resolve | Yes — conciliation checkpoint skipped in chain verify |
| **Per-entry HMAC** | `integrity-check`, nightly | **None** — reconcile only bridges chain | **No** — `EntryHmacViolationCollector` always flags mismatches |

After B2 reconcile, an operator can close the alert and restore **chain continuity**, but:

- Tampered rows **remain** `HMAC_MISMATCH` on every verify (correct — tamper-evident).
- Nightly / future on-demand validation can **re-raise** alerts (new `windowStart` dedupe keys) for
  the **same** explained entry tamper.
- B2.5 honest badges correctly stay **red** on affected rows — but there is no persisted “explained and
  accepted” act discoverable by detection logic.

This violates the design-pack D5 intent (*resolved manipulation periods excluded from
re-litigation*) for the **entry** half of the rupture. B2.5 D1 explicitly excluded a parallel
**mutable violation flag** on audit rows; this TB adds a separate **conciliation registry** (operator
acts, not derived integrity state on `ezkey_audit_log`).

## Stakes, limits, and non-goals

### Why this matters (operability + trust)

- **Operator fatigue:** Re-alerts on already-investigated entry tamper erode alert queue trust and
  block EXP1 soak / lab iteration (including on-demand validation without waiting for cron).
- **SOC2 narrative:** An explained integrity exception needs a **durable, queryable record** (who,
  when, category, justification) — not only a resolved alert row whose payload is window-scoped.
- **Honest security posture:** We **do not** claim the entry became cryptographically valid. We claim
  the organization **acknowledged** a known invalid state under audited justification
  ([`integrity-assurance-honest-line.md`](../../integrity-assurance-honest-line.md),
  [`docs/SECURITY_POSTURE.md`](../../../docs/SECURITY_POSTURE.md)).

### Hard limits (unchanged)

- **No blind re-sign:** Conciliation must not rewrite `entry_hmac` to match tampered content.
- **Host trust boundary:** A host-rooted adversary could forge conciliation rows too — same ceiling as
  chain conciliation and HMAC itself.
- **Not tamper-proof:** UI may show “Explained” while verify API still reports violation — that is
  intentional.

### Explicit non-goals (this TB)

- Restoring original entry content from backup (operator out-of-band if ever).
- Automatic “healing” of HMAC signatures.
- New alert type per entry (queue noise; breaks C7 scoping).
- Replacing B3 batch widgets or snooze (C9).

## Current state (code baseline)

| Capability | Status |
|------------|--------|
| Entry HMAC verify + structured violations | Shipped (B2.5) |
| Chain verify + `MANIPULATION_CONCILIATION` skip | Shipped (B2) |
| `reconcileIntegrityRupture` (chain bridge + alert resolve) | Shipped (B2) |
| Entry conciliation persistence | **Gap** |
| Detection skip for conciliated entries | **Gap** |
| Dedupe beyond `windowStart` epoch | **Gap** (re-alert risk) |
| UI “explained violation” state | **Gap** (extends B2.5 badges) |
| Operator-triggered retroactive validation (detect + alert) | **Gap** — companion TB B2.7 |

Reference implementations to extend (not duplicate):

- `AuditLifecycleService.reconcileIntegrityRupture`
- `EntryHmacViolationCollector` / `NightlyIntegrityValidationService`
- `AlertService.raiseOrTouch` dedupe semantics

## Unified integrity alert model (coherence target)

`AUDIT_INTEGRITY_RUPTURE` remains the **single** manipulation-family alert for period validation.
It carries **two violation dimensions**:

```
AUDIT_INTEGRITY_RUPTURE
├── Chain dimension     failBoundary / resumeBoundary / chainViolations[]
└── Entry dimension     entryViolations[] (auditLogId, reason, …)
```

**Coherence rules (normative for this TB):**

1. **Detection** aggregates both dimensions; either can raise / keep open the same alert type.
2. **Investigation** (B2.5) treats both lists as first-class with deep links.
3. **Resolution** must address **both** when present:
   - Chain → existing `MANIPULATION_CONCILIATION` bridge.
   - Entry → new `AuditEntryIntegrityConciliation` record(s) per acknowledged row.
4. **Re-litigation:** Chain skip via checkpoint type (existing). Entry skip via conciliation registry
   + fingerprint match (new).
5. **Re-tamper:** Same entry, **different** observed fingerprint → **new** incident signal (C8-7
   spirit — do not reopen closed alert; raise/touch with updated incident fingerprint).

Parallel alert types (`AUDIT_ENTRY_HMAC_*`) are **rejected** for R1.

## Design decisions (maintainer alignment — D1–D6 decided 2026-07-02)

### D1 — Persistence: `AuditEntryIntegrityConciliation` registry

**Status:** `decided` (2026-07-02)

**Decision:** New table `ezkey_audit_entry_integrity_conciliation` — operational index for lookup,
**distinct** from `ezkey_audit_log` mutation. Java entity `AuditEntryIntegrityConciliation`; service
`AuditEntryIntegrityConciliationService` (parallel to `AuditChainIncident` / `AuditChainIncidentService`).

| Column | Purpose |
|--------|---------|
| `conciliation_id` | Surrogate PK (`BIGINT GENERATED ALWAYS AS IDENTITY`) |
| `audit_log_id` | Target entry id (part 1 of partitioned entry identity) |
| `audit_log_created_at` | Target entry `created_at` (part 2); immutable snapshot at conciliation time |
| `violation_reason` | `HMAC_MISMATCH` or `MISSING_ENTRY_HMAC` at conciliation time |
| `observed_state_fingerprint` | SHA-256 hex (64 chars) of canonical entry form **at conciliation** (see D2) |
| `category` | Reuse `IntegrityRuptureConciliationCategory` |
| `justification` | Operator narrative (10–500 chars, aligned with lifecycle reconcile) |
| `external_ticket_reference` | Optional (max 128 chars) |
| `source_alert_id` | **NOT NULL** FK → `ezkey_alert` — alert row persists as `RESOLVED` after reconcile (no DELETE) |
| `conciliated_by_admin_id` | **NOT NULL** FK → `ezkey_admin` |
| `conciliated_at` | TIMESTAMPTZ UTC |
| `status` | `AuditEntryIntegrityConciliationStatus`: `ACTIVE` (R1 default) or `SUPERSEDED` |
| `created_at` / `updated_at` | Row touch timestamps (same pattern as `ezkey_audit_chain_incident`) |

**Entry reference (partition + purge aware):** store composite snapshot `(audit_log_id,
audit_log_created_at)` matching partitioned `ezkey_audit_log` identity. **No FK** to
`ezkey_audit_log` — archive-sealed partitions are physically purged (`SEALED → PURGEABLE → delete`)
while this registry must survive for SOC 2 operator narrative. Audit log uses RANGE+LIST
(`created_at`, `api_name`) partitioning — a FK target would need `(audit_log_id, created_at,
api_name)`; V15 does **not** add a parent PK (conciliation needs no FK). See
`docs/DATABASE_PARTITIONING_IMPLEMENTATION.md` § Flyway greenfield patterns.

**Unique constraint:** partial unique index — one `ACTIVE` conciliation per `audit_log_id`:

```sql
CREATE UNIQUE INDEX uq_entry_integrity_conciliation_active_audit_log
    ON ezkey_audit_entry_integrity_conciliation (audit_log_id)
    WHERE status = 'ACTIVE';
```

**Audit meta-event:** `AUDIT_ENTRY_INTEGRITY_CONCILIATED` (new `EventType`) with JSON
`event_details` mirroring row + fingerprint — INC-1 pattern (first-class audit, table as index).
Bundled reconcile emits one `AUDIT_INTEGRITY_RUPTURE_CONCILIATED` (existing) plus one
`AUDIT_ENTRY_INTEGRITY_CONCILIATED` per conciliated entry.

**Excluded:** storing conciliation only in alert payload or checkpoint notes; optional FK to
`MANIPULATION_CONCILIATION` checkpoint (traceability via `source_alert_id` + meta-events is sufficient
for R1).

---

### D2 — Fingerprint: reliable skip vs re-tamper detection

**Status:** `decided` (2026-07-02)

**Formula (conciliation time):**

```
observed_state_fingerprint = SHA-256_hex( AuditHmacService.buildCanonicalForm(entry) )
```

- Lowercase hex, 64 characters (distinct from Base64 `entry_hmac`).
- Centralized via `AuditEntryIntegrityConciliationService.computeObservedStateFingerprint(entry)`
  (delegates to the same canonical builder used for HMAC verify).
- **`entry_hmac` is excluded** from the fingerprint (meaningless under mismatch; diagnostic only).

**Classification on each detection pass:**

| HMAC verify | Conciliation | Fingerprint vs ACTIVE | Alert-eligible | UI / API |
|-------------|--------------|----------------------|----------------|----------|
| OK | — | — | No | Verified intact |
| KO | none | — | **Yes** | Violation (open) |
| KO | ACTIVE | **match** | **No** (skip) | Violation + **Explained** |
| KO | ACTIVE | **mismatch** | **Yes** (re-tamper) | Violation (open / re-tamper) |

**`violation_reason` on conciliation row:** snapshot at conciliation time (`HMAC_MISMATCH` /
`MISSING_ENTRY_HMAC`); skip logic uses **fingerprint gate**, not reason alone.

**Re-conciliation after re-tamper:** on new reconcile for the same `audit_log_id`, transition prior
`ACTIVE` row → `SUPERSEDED`, then insert new `ACTIVE` with fresh fingerprint (preserves SOC2 history;
satisfies partial unique index).

**Re-tamper before re-conciliation:** mismatch makes the row alert-eligible immediately; prior
`ACTIVE` conciliation remains in DB but no longer suppresses alerts (fingerprint gate only — no
auto-`SUPERSEDED` at detection time).

**Deleted entry:** no entry conciliation possible (no target row). Chain-only path via
`MANIPULATION_CONCILIATION` + alert resolve.

**Reporting vs alert aggregation (D6):** integrity-check / badges report all HMAC KO rows with
conciliation status; alert aggregation excludes KO + ACTIVE + fingerprint match only.

---

### D3 — Operator workflow: bundled reconcile (R1 default)

**Status:** `decided` (2026-07-02)

**Primary path:** Extend `POST …/lifecycle/reconcile-integrity-rupture` (existing B2 endpoint).

**Request extension:** add `acknowledgedAuditLogIds` (`List<Long>`) to
`IntegrityRuptureReconciliationRequest`. Shared `justification`, `category`, and
`externalTicketReference` apply to chain bridge **and** all entry conciliations in one operator act.

**Reconcile algorithm (transactional order):**

1. Existing guards (HMAC active, heartbeat stale, OPEN alert, boundaries).
2. **Live re-verify** on `[failBoundary, resumeBoundary)` → entry violations (HMAC KO).
3. Exclude entries already **Explained** (KO + ACTIVE conciliation + fingerprint match) from the
   required acknowledge set.
4. If any remaining entry violations: `acknowledgedAuditLogIds` must **exactly match** that set
   (same IDs, same count) — otherwise `400`.
5. Existing chain reconcile (`MANIPULATION_CONCILIATION` + re-chain).
6. For each acknowledged entry: SUPERSEDE prior `ACTIVE` if re-tamper (D2), insert conciliation row,
   emit `AUDIT_ENTRY_INTEGRITY_CONCILIATED`.
7. Resolve alert (`INTEGRITY_RUPTURE_CONCILIATED`).

**Chain-only rupture** (zero live entry violations): B2 behavior unchanged; `acknowledgedAuditLogIds`
absent or empty.

**Partial conciliation:** **Not offered** in R1 — operator cannot resolve while unacknowledged entry
violations remain.

**Response extension:** `IntegrityRuptureReconciliationResult` adds `conciliatedAuditLogIds` and
`entryConciliationCount`.

**UI (slice 2):** Reconcile dialog shows **Verified now** list; all non-explained entry violations
must be checked before Submit.

**Secondary path** (`POST …/lifecycle/reconcile-entry-integrity-violation`): **deferred** out of slice
1 — bundled path covers R1 operator journey.

**Blocked when:** `AUDIT_CHAIN_HEARTBEAT_STALE` OPEN (unchanged).

---

### D4 — Alert dedupe: incident fingerprint (replace window-only key)

**Status:** `decided` (2026-07-02)

**Problem:** Current dedupe `AUDIT_INTEGRITY_RUPTURE:{windowStartEpochMillis}` does not stabilize
across nightly window slide, on-demand runs (B2.7), or re-alerts after chain-only reconcile.

**Decision:** Replace with **incident fingerprint** via shared utility
`IntegrityRuptureIncidentFingerprint.compute(...)`.

```
incidentFingerprint = SHA-256_hex( canonical UTF-8 JSON, keys sorted lexicographically )

{
  "failBoundary": "<ISO-8601 UTC>|null",
  "resumeBoundary": "<ISO-8601 UTC>|null",
  "entryViolations": [
    { "auditLogId": 123, "violationReason": "HMAC_MISMATCH" },
    ...
  ],                                    // alert-eligible only; sorted by auditLogId asc
  "chainViolations": [
    { "checkpointId": 45, "violationType": "CHAIN_HMAC_MISMATCH" },
    ...
  ]                                     // alert-eligible only; sorted by checkpointId, violationType
}

dedupeKey = "AUDIT_INTEGRITY_RUPTURE:" + incidentFingerprint   // ~88 chars; fits VARCHAR(256)
```

**Refinements (stability):**

- **Exclude** validation `windowStart` / `windowEnd` from fingerprint (window slide ≠ new incident).
- **`failBoundary` / `resumeBoundary`:** included **only when chain is alert-eligible** (chain
  violations or gaps not deferred by C8-6); **null** when chain intact (entry-only incidents).
- **Entry list:** alert-eligible violations only (exclude Explained per D2/D6).
- **Chain list:** alert-eligible chain findings post C8-6 classifier only.
- **Never** include alert payload snapshot or `entry_hmac` in the key.

**Effects:**

- Same incident → `raiseOrTouch` (occurrence++, payload refresh).
- Entry conciliated → fingerprint shrinks → touch with reduced set; empty + chain OK → no raise.
- Resolved alert + identical incident reappears → **new** OPEN alert (C8-7 — never reopen).
- **No backfill** for existing OPEN alerts on legacy window keys; they coexist until resolved.

**Payload:** alert JSON still carries `windowStart`, `windowEnd`, capped lists, boundaries for B2.5
investigation — only `dedupeKey` computation changes.

---

### D5 — API & UI honesty (extends B2.5 D3)

**Status:** `decided` (2026-07-02)

**API — enrich `EntryIntegrityViolation`** (inline, no parallel DTO):

```java
EntryIntegrityConciliationStatus conciliationStatus  // NONE | ACKNOWLEDGED | RE_TAMPER_SUSPECTED
EntryIntegrityConciliationSummary conciliationSummary  // null when NONE
```

**Summary record:** `conciliationId`, `conciliatedAt`, `category` (`IntegrityRuptureConciliationCategory`),
`conciliatedByAdminId`, `sourceAlertId`.

**Surfaces:** `GET …/integrity-check` (range) and `GET …/audit-logs/{id}/integrity-check` (single) —
conciliation lookup (**ACTIVE** only) + fingerprint compare (D2) at verify time.

**Mapping:**

| HMAC | ACTIVE conciliation | Fingerprint | `conciliationStatus` |
|------|---------------------|-------------|------------------------|
| KO | no | — | `NONE` |
| KO | yes | match | `ACKNOWLEDGED` |
| KO | yes | mismatch | `RE_TAMPER_SUSPECTED` |

**No dedicated** `GET …/integrity-conciliation` endpoint in R1 — single integrity-check covers entry
detail (B2.5 D3.3).

**UI — extend `EntryHmacDisplayState`:**

| State | Condition | Display |
|-------|-----------|---------|
| `violation` | HMAC KO, `NONE` | Red shield — open violation |
| `violationExplained` | HMAC KO, `ACKNOWLEDGED` | Amber shield — known invalid, acknowledged |
| `violationRetamper` | HMAC KO, `RE_TAMPER_SUSPECTED` | Red shield — changed since acknowledgement |

Copy must distinguish **cryptographic invalid** vs **operationally acknowledged** (Explained ≠ fixed).

**Session cache (extends B2.5):** add `conciliationByAuditLogId` to `IntegrityInvestigationSession`,
populated from verify responses; invalidate on logout, reconcile success, explicit verify refresh.
List column without active session falls back to **signed** (neutral) — no false Explained badge.

**ADR-0005 (2026-07-03):** opening audit log **Detail** runs single-entry integrity-check and
**seeds** the session for that row so list badges align on close (amber Explained after conciliation).
Reconcile clear unchanged; panel Verify remains the range-wide path.

**Slice 2 surfaces:** `entry-hmac-badge.tsx`, audit list + banner, alert detail « Verified now »
column, audit log detail HMAC row, i18n EN/FR.

---

### D6 — Detection orchestration skip (implements design-pack D5 for entries)

**Status:** `decided` (2026-07-02)

**Classifier:** central `EntryIntegrityViolationClassifier` (or methods on
`AuditEntryIntegrityConciliationService`) consumed by collector, retroactive orchestration (B2.7), and
`AuditIntegrityService`.

```text
classify(entry) → violation detail + conciliationStatus (D5) + alertEligible boolean

alertEligible = HMAC KO
             AND NOT (ACTIVE conciliation + fingerprint match)
```

**Split:**

| Path | Scope |
|------|-------|
| **Reporting** | All HMAC KO rows (integrity-check, badges, alert payload for investigation) |
| **Alert aggregation + dedupe fingerprint** | `alertEligible` subset only (D4) |

**Orchestration (`NightlyIntegrityValidationService` / `RetroactiveIntegrityValidationService`):**

- `shouldRaiseIntegrityAlert`: entry dimension raises when **any** alert-eligible entry remains;
  chain dimension — C8-6 logic **unchanged**.
- Alert payload includes **all** violations (incl. Explained) for B2.5 investigation labels.
- No raise when only Explained entries remain and chain intact.

**Registry (optional slice 1):** scope message may include open vs acknowledged entry counts
(e.g. `2 entry open, 1 acknowledged, chain OK`).

---

## Operator journey (end-to-end)

```text
1. Detection (nightly or future on-demand run)
   → AUDIT_INTEGRITY_RUPTURE raised/touched with chain + entry sections

2. Triage (Alerts)
   → Global Admin opens alert

3. Investigation (B2.5 — existing)
   → Auto re-verify: At detection vs Verified now
   → Investigate in Audit Logs → highlighted rows, badges

4. Decision
   → Operator confirms boundaries + category + justification
   → Acknowledges each listed entry violation (bundled reconcile)

5. Resolution (this TB)
   → MANIPULATION_CONCILIATION checkpoint created
   → AuditEntryIntegrityConciliation row per entry + AUDIT_ENTRY_INTEGRITY_CONCILIATED events
   → Alert RESOLVED (INTEGRITY_RUPTURE_CONCILIATED)

6. Steady state
   → Re-run validation: no duplicate alert if fingerprints unchanged
   → Audit log detail: red/explained badge + link to conciliation narrative
   → Compliance: meta-audit + conciliation table lookup
```

**Mental model for operators:** Chain conciliation = “we re-attached the checkpoint narrative.” Entry
conciliation = “we recorded that this specific row is a **known** invalid signature, investigated and
accepted under justification.” Neither means “the row is cryptographically healthy.”

## Relationship to adjacent work

| Artifact | Relationship |
|----------|----------------|
| B2.5 TB | Investigation UX — prerequisite; badge semantics **extended**, not replaced |
| **B2.7 TB** | [`TB-2026-07-02-retroactive-integrity-validation-operability.md`](TB-2026-07-02-retroactive-integrity-validation-operability.md) — generic orchestration + POST operator detect; **requires B2.6 D4/D6** for safe overlap with nightly |
| `I-2026-0007` / B3 widgets | May show conciliation counts later — out of scope here |
| Design pack D5 skip rule | This TB **completes** entry half of skip rule |

## In scope

### Slice 1 — Core persistence + detection

1. Flyway: `ezkey_audit_entry_integrity_conciliation` + indexes.
2. Entity, repository, `AuditEntryIntegrityConciliationService` (fingerprint compute, lookup, create).
3. Extend `reconcileIntegrityRupture` to create entry conciliations (D3).
4. New `EventType` + audit emission.
5. Entry collector / nightly orchestration: alert-eligible vs acknowledged split (D6).
6. Incident fingerprint dedupe in `raiseIntegrityRuptureAlert` (D4).
7. Unit tests: fingerprint match/mismatch, dedupe touch, partial chain-only reconcile rejected when
   entries open.

### Slice 2 — API + Admin UI coherence

1. `GET /audit-logs/{id}/integrity-conciliation` (or embedded in integrity-check).
2. Reconcile dialog: entry checklist + validation errors when incomplete.
3. Badge / banner copy EN/FR; alert detail shows conciliation status post-resolve.
4. `docs/ALERTS.md`, `docs/AUDIT_LOG_INTEGRITY.md`, Postman lifecycle folder.
5. OpenAPI refresh after stack.

### Slice 3 — Retroactive validation operability (companion B2.7)

Moved to dedicated TB — not a loose optional follow-on. See
[`TB-2026-07-02-retroactive-integrity-validation-operability.md`](TB-2026-07-02-retroactive-integrity-validation-operability.md).

Recommended program order on #269: **B2.6 slice 1 → B2.7 slice 1** (or combined refactor PR) → B2.6
slice 2 UI → B2.7 slice 2 UI.

## Out of scope

- Re-sign / repair entry HMAC in place.
- Snooze (C9).
- B3 dashboard widgets.
- Backfill conciliations for historically resolved alerts without entry records.
- Tenant Admin write access.

## Implementation sequence

1. Migration + domain model + fingerprint utility (shared with `AuditHmacService` canonical builder).
2. Conciliation service + unit tests.
3. Wire reconcile + audit events.
4. Nightly/collector skip + dedupe fingerprint.
5. Admin API endpoints + tests.
6. Admin UI reconcile dialog + badges + i18n.
7. Docs + Postman + manual lab.

## Validation

- [ ] Maven baseline (root reactor).
- [ ] Unit: fingerprint stable across re-read; mismatch on content edit; one ACTIVE per audit_log_id.
- [ ] Unit: dedupe touch same incident; new alert after resolve + re-tamper.
- [ ] clean-start lab:
  - Tamper one row → nightly alert with entryViolations.
  - Investigate (B2.5) → reconcile with justification.
  - Re-run `validateWindow` / integrity-check → **no new OPEN alert**; entry still HMAC KO.
  - Modify tampered row again → **new** alert or re-tamper badge path.
- [ ] Chain-only rupture (no entry violations) → reconcile unchanged behavior.
- [ ] Heartbeat stale still blocks reconcile.

## Open questions for maintainer review

1. ~~**Strict reconcile rule**~~ — settled in D3 (live re-verify exact match; Explained entries
   excluded).
2. ~~**Deleted entry**~~ — settled in D2 (chain-only path).
3. ~~**Table name / EventType naming**~~ — settled in D1.
4. **Slice 3 timing:** See B2.7 TB — recommend same #269 milestone, after B2.6 slice 1 (dedupe + skip).

## Links

- Extends: [`TB-2026-06-28-manipulation-integrity-remediation.md`](TB-2026-06-28-manipulation-integrity-remediation.md)
- Investigation UX: [`TB-2026-06-30-integrity-investigation-operability.md`](TB-2026-06-30-integrity-investigation-operability.md)
- Design pack: [`../../integrity-cluster-design-pack.md`](../../integrity-cluster-design-pack.md)
- Honest line: [`../../integrity-assurance-honest-line.md`](../../integrity-assurance-honest-line.md)
- Runtime: [`../../../docs/ALERTS.md`](../../../docs/ALERTS.md),
  [`../../../docs/AUDIT_LOG_INTEGRITY.md`](../../../docs/AUDIT_LOG_INTEGRITY.md)
- Idea: [`ideas/I-2026-0005-checkpoint-integrity-break-remediation.md`](ideas/I-2026-0005-checkpoint-integrity-break-remediation.md)
- Companion B2.7: [`TB-2026-07-02-retroactive-integrity-validation-operability.md`](TB-2026-07-02-retroactive-integrity-validation-operability.md)
