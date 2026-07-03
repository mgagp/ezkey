# Integrity cluster — Wave B operator end-state compass

## Metadata

- **Document ID:** `integrity-wave-b-operator-end-state-compass`
- **Status:** `active` (Wave B R1 closed — 2026-07-03)
- **Purpose:** Single entry point for fresh sessions: target end-state, shipped vs planned, doc map,
  implementation order, open clarifications. Supplements
  [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md) (normative design) with
  **operator-complete** closure criteria.
- **Program:** GitHub #269 (closed 2026-07-03); milestone compass
  [`operational-readiness-prioritization-2026-09.md`](operational-readiness-prioritization-2026-09.md)
- **Closeout:** [`backlog/method-logs/ML-2026-07-03-wave-b-integrity-cluster-closeout.md`](backlog/method-logs/ML-2026-07-03-wave-b-integrity-cluster-closeout.md)

---

## Target end-state (maintainer topo)

When Wave B integrity work through **B2.6 + B2.7 + B3** is complete, Ezkey provides **coherent,
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

| Family | Alert | Resolution path | Status |
|--------|-------|-----------------|--------|
| **Gap / downtime** | `AUDIT_CHAIN_GAP_PENDING` | Declare gap → `GAP_DECLARATION` checkpoint | Shipped |
| **Heartbeat / partial outage** | `AUDIT_CHAIN_HEARTBEAT_STALE` | Restore + incident declare | Shipped |
| **Manipulation (chain + entry)** | `AUDIT_INTEGRITY_RUPTURE` | Reconcile + chain bridge + entry conciliation (B2.6) | Shipped |
| **Detect on demand** | (same alert type) | Operator POST retroactive run (B2.7) | Shipped |

---

## Shipped (Wave B R1 — 2026-07-03)

- B0–B1: rolling attach, gap alerts, heartbeat guard + degraded 503, nightly batch, job registry.
- B2: chain reconcile `MANIPULATION_CONCILIATION`.
- B2.5: structured violations, investigation UX, honest HMAC badges.
- B2.6: entry conciliation registry, dedupe, reconcile UI extensions, Explained badges (PR #287).
- B2.7: `RetroactiveIntegrityValidationService`, POST run, Verify vs Run validation UI (PR #287–#288).
- B3: dashboard batch health widgets + open-alert count banner (PR #289).
- GET verify: `integrity-check`, `chain-integrity`, per-entry check.

### Deferred (post-R1)

| ID | Item | Role |
|----|------|------|
| **C9** | Snooze | Deferred |
| **Wave C** | `I-2026-0028` P3 Alerts list polish | Next operator UI slice per release compass |
| **Wave C** | Audit chain checkpoints matrix row | After alerts polish |
| **EXP1 soak** | Extended runtime scenarios | Wave D |

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

---

## Fresh-session bootstrap (agents)

1. Read **this compass** + [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md).
2. Wave B code is **done**; next product slice: **Wave C** —
   [`TB-2026-07-03-admin-ui-alerts-list-polish.md`](backlog/TB-2026-07-03-admin-ui-alerts-list-polish.md).
3. Runtime: [`docs/ALERTS.md`](../../docs/ALERTS.md), [`docs/AUDIT_LOG_INTEGRITY.md`](../../docs/AUDIT_LOG_INTEGRITY.md).
4. Honest limits: [`integrity-assurance-honest-line.md`](integrity-assurance-honest-line.md).

---

## Corpus map

| Maintainer concern | Documented? | Primary artifact |
|--------------------|-------------|------------------|
| Detect chain + entry | Yes | Design pack; B1/B2.7 shipped |
| Trace detect → resolve | Yes | B2.5 + B2.6 journey + this compass |
| No duplicate alerts | Yes | B2.6 D4, D6 |
| Explain + persist (entry) | Yes | B2.6 shipped |
| Batch visibility | Yes | B3 shipped |
| Alerts list polish | Planned | Wave C TB (draft) |

**Next execution:** promote and implement
[`TB-2026-07-03-admin-ui-alerts-list-polish.md`](backlog/TB-2026-07-03-admin-ui-alerts-list-polish.md).
