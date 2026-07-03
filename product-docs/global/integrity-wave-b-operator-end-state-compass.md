# Integrity cluster — Wave B operator end-state compass

## Metadata

- **Document ID:** `integrity-wave-b-operator-end-state-compass`
- **Status:** `active` (session handoff — 2026-07-02)
- **Purpose:** Single entry point for fresh sessions: target end-state, shipped vs planned, doc map,
  implementation order, open clarifications. Supplements
  [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md) (normative design) with
  **operator-complete** closure criteria.
- **Program:** GitHub #269; milestone compass
  [`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md)
- **Session source:** Analysis 2026-07-02 (entry conciliation asymmetry + retroactive validation
  operability). Method log:
  [`backlog/method-logs/ML-2026-07-02-entry-integrity-conciliation-kickoff.md`](backlog/method-logs/ML-2026-07-02-entry-integrity-conciliation-kickoff.md)

---

## Target end-state (maintainer topo)

When Wave B integrity work through **B2.6 + B2.7** is complete, Ezkey should provide **coherent,
operable** handling of audit log integrity at **two cryptographic layers**:

| Layer | What breaks | Detect | Identify / investigate | Explain & persist | No duplicate alert | Honest verify after close |
|-------|-------------|--------|------------------------|-------------------|--------------------|---------------------------|
| **Checkpoint chain** | Missing links, digest mismatch, undeclared gaps | Nightly + operator retroactive run + GET verify | Alert payload, chain violations, timeline deep links | `MANIPULATION_CONCILIATION` or gap/heartbeat paths | Incident fingerprint dedupe (B2.6 D4) | Chain verify green for conciliated window |
| **Per-entry HMAC** | Field tamper, missing signature | Same detect paths | Entry violations, row badges, audit log deep links | `EntryIntegrityConciliation` registry + audit event (B2.6) | Skip alert-eligible + dedupe (B2.6 D6/D4) | HMAC still KO; API/UI show **Explained** |

**Principles (non-negotiable):**

- Tamper-**evident**, not tamper-proof — invalid signatures stay invalid; we do not re-sign to erase history.
- Explanations are **audited** (meta-events + durable lookup), bounded by host trust boundary.
- **Three API postures:** read-only verify (GET), detect + alert (retroactive run POST), lifecycle
  resolution (reconcile / declare-gap / heartbeat declare).

---

## Rupture families (full cluster — not only manipulation)

Manipulation (`AUDIT_INTEGRITY_RUPTURE`) is one family. Complete integrity **operability** also includes:

| Family | Alert | Resolution path | Status |
|--------|-------|-----------------|--------|
| **Gap / downtime** | `AUDIT_CHAIN_GAP_PENDING` | Declare gap → `GAP_DECLARATION` checkpoint | Shipped |
| **Heartbeat / partial outage** | `AUDIT_CHAIN_HEARTBEAT_STALE` | Restore + incident declare | Shipped |
| **Manipulation (chain + entry)** | `AUDIT_INTEGRITY_RUPTURE` | Reconcile + chain bridge + entry conciliation (B2.6) | Partial — chain shipped; entry B2.6 |
| **Detect on demand** | (same alert type) | Operator POST retroactive run (B2.7) | Planned B2.7 |

Grill reference: [`backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md`](backlog/grill-sessions/integrity-cluster-D4-D6-grill-me.md) (C7 priority, C8-7 no reopen).

---

## Shipped vs remaining (2026-07-02)

### Shipped (B0–B2.5)

- Rolling attach, gap alerts, heartbeat guard + degraded 503, nightly batch (B1).
- Chain reconcile `MANIPULATION_CONCILIATION` (B2).
- Structured violations, investigation UX, honest HMAC badges (B2.5).
- GET verify: `integrity-check`, `chain-integrity`, per-entry check.

### Remaining for maintainer topo (B2.6 + B2.7)

| ID | TB | Delivers |
|----|-----|----------|
| **B2.6** | [`TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md`](backlog/TB-2026-07-02-entry-integrity-conciliation-and-alert-coherence.md) | Entry conciliation table, fingerprint skip, incident dedupe, reconcile UI extensions, Explained badges |
| **B2.7** | [`TB-2026-07-02-retroactive-integrity-validation-operability.md`](backlog/TB-2026-07-02-retroactive-integrity-validation-operability.md) | `RetroactiveIntegrityValidationService`, POST run, Verify vs Run validation UI |

### Adjacent (not blocking topo logic; Wave B exit polish)

| ID | Item | Role |
|----|------|------|
| **B3** | `I-2026-0007` dashboard widgets | Last-run visibility, open-alert count banner — **in review** |
| **C9** | Snooze | Deferred |
| **Runtime docs** | `docs/ALERTS.md`, `docs/AUDIT_LOG_INTEGRITY.md` | Update on implementation, not analysis |

---

## Operator journey (complete manipulation path)

```text
Detect     → nightly cron OR POST integrity-validation/run (B2.7)
Alert      → AUDIT_INTEGRITY_RUPTURE (chain + entry sections)
Investigate→ alert auto re-verify; Investigate in Audit Logs (B2.5)
Verify     → GET reports (optional, read-only)
Resolve    → Reconcile dialog: chain bridge + per-entry acknowledgment (B2.6)
Steady     → no duplicate alert; entry HMAC still invalid but Explained; chain conciliated
```

Gap and heartbeat paths remain **sequential priority** when multiple alerts open (C8-11).

---

## Recommended implementation order (#269)

1. **B2.6 slice 1** — DB + conciliation service + dedupe + detection skip + reconcile wiring.
2. **B2.7 slice 1** — orchestration rename + POST (same PR acceptable if same service touched).
3. **B2.6 slice 2** — reconcile UI, Explained badges, conciliation lookup API.
4. **B2.7 slice 2** — Verify / Run validation UI.
5. **Docs + Postman + spec refresh** — same change sets.
6. **B3** — when B2.6/B2.7 validated in lab.

---

## Fresh-session bootstrap (agents)

1. Read **this compass** + [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md).
2. Read active TB under review: **B2.6** then **B2.7** (status `under-review` until maintainer signs).
3. Runtime: [`docs/ALERTS.md`](../../docs/ALERTS.md), [`docs/AUDIT_LOG_INTEGRITY.md`](../../docs/AUDIT_LOG_INTEGRITY.md).
4. Honest limits: [`integrity-assurance-honest-line.md`](integrity-assurance-honest-line.md).

**Grill-me vs questions:** For B2.6/B2.7, **no full grill** — decisions D1–D6 are drafted; use **targeted
maintainer confirmation** on open questions listed in each TB (or promote TB to `ready`). Full grill
only if implementation reveals conflict with C7/C8 grill (e.g. partial conciliation scope change).

---

## Open clarifications (identified — not lost)

| # | Topic | Where | Recommendation |
|---|--------|-------|----------------|
| 1 | Reconcile must match **live re-verify** entry set vs alert snapshot | B2.6 open Q1 | Live re-verify at reconcile |
| 2 | Deleted entry (no row) — entry conciliation N/A | B2.6 open Q2 | Chain-only reconcile |
| 3 | EventType naming for entry conciliation + completion `triggerSource` | B2.6 Q3, B2.7 Q1 | Min churn: add fields to existing events |
| 4 | POST `raiseAlert: false` dry-run | B2.7 Q2 | Yes for API; UI defaults true |
| 5 | Operator max window cap | B2.7 Q5 | **Decided 2026-07-03:** no default cap — POST parity with GET verify; optional `operator-max-window-hours` only when explicitly set |
| 6 | Partial entry conciliation (close alert with subset) | B2.6 D3 | Out of R1 — confirm unchanged |
| 7 | Integrity of conciliation records themselves | Session | Table + signed audit meta-event; no HMAC on conciliation row R1 (host boundary) — document only |

---

## Corpus map (session captured?)

| Maintainer concern | Documented? | Primary artifact |
|--------------------|-------------|------------------|
| Detect chain + entry | Yes | Design pack layer 2; B1/B2.7 TB |
| Trace detect → resolve | Yes | B2.5 journey + B2.6 journey + this compass |
| No duplicate alerts | Yes | B2.6 D4, D6; design pack D5-13 |
| Explain + persist explanation | Yes (chain); Planned (entry) | B2 shipped; B2.6 D1/D2 |
| Leave entry HMAC invalid | Yes | B2.6 objective, honest line |
| API: explained without rewriting history | Yes (planned) | B2.6 D5 conciliationStatus |
| UI: verify vs run validation | Yes | B2.7 D5 |
| UI: navigate to problems | Yes | B2.5 D4 |
| UI: reconcile dialogs + justification | Yes (chain); Extend (entry) | B2 UI + B2.6 slice 2 |
| No explain same entry twice | Yes | B2.6 unique ACTIVE per audit_log_id |
| Ad hoc report / take action | Yes | B2.7 POST |
| DB + audit for lifecycle acts | Yes (chain); Planned (entry) | B2 events; B2.6 table + event |
| Batch visibility | **Shipped (B3)** | Dashboard widgets + `openAlertCount` banner |

**Verdict:** Session analysis is **captured** for implementation planning. **Not yet** in runtime
`docs/*` until code lands. **Single gap in corpus before today:** no one-page end-state compass —
**this document** closes that for fresh sessions.

---

## TB status gate

Promote B2.6 and B2.7 from `under-review` → `ready` after maintainer confirms open questions (or
accepts recommendations as decided). Then implementation may start on `feature/269-…` branch per TB.
