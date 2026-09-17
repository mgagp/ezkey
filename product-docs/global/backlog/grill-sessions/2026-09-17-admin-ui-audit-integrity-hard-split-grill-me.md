# Grill Me — Admin UI Audit trail vs Integrity hard split

## Session control

| Field | Value |
|-------|--------|
| **Intention note** | [`../../admin-ui-audit-integrity-hard-split-intention.md`](../../admin-ui-audit-integrity-hard-split-intention.md) |
| **Started** | `2026-09-17` |
| **Status** | `open` |
| **Griller** | Julie |
| **Operator** | Marc |
| **Product witness** | Alex |
| **Security witness** | Christophe (replied 2026-09-17) |
| **Resume at** | G4 |

## Settled before grilling

| ID | Topic | Decision |
|----|--------|----------|
| **S0** | Split shape | **Hard split** — Integrity is first-class; not declutter-in-place. No crypto shadow zones under ordinary browsing. |
| **S1** | Priority | Not P0 / no hard date. Deliberate Admin UI opérabilité **after Wave B R1** — IA matches shipped semantics. Not a demoted re-read; no new September gate. (Alex) |
| **S2** | Phase order | UI hard split until **walkable**, then API↔UI gap closure before calling the area done. |
| **S3** | QA | Isabelle exploratory once walkable. |
| **S4** | Roles baseline | Prior integrity grill A2: GA for crypto ops; Tenant Admin consults trail only. |
| **S5** | Alerts vs Integrity | Alerts = exceptional **signal list**; Integrity = **investigation + remediation** home. Do not merge. (Alex) |
| **S6** | Naming boundary | If “Lifecycle” appears in the label, it means only audit seal/archive/confirm — **not** [`lifecycle-model.md`](../../lifecycle-model.md). Prefer nav **Integrity** if collision risk. (Alex — finalize in G4) |
| **S7** | Proof model | **One proof, multiple views** on the same `audit_log` (HMAC + chain). No second event journal. Complementary tables (alerts, incidents, conciliation) = operational indexes, not audit citizens. Conciliation = explanation not rewrite. (Christophe 2026-09-17) |
| **G1** | Nav | **A** — New **sidebar item** (Global Admin only); `/audit-logs` stays trail-only for all roles. (Marc 2026-09-17) |
| **G2** | Remediation home | **A** — Alerts detail **Resolve** deep-links into Integrity remediation UI (single home for reconcile / confirm). (Marc 2026-09-17) |
| **G3** | Investigation landing | **C** — Alerts → Integrity for investigate/verify/resolve; trail only for view-around-event; same `audit_log`; crypto unchanged. Matches Christophe reco (B or C; C preferred). (Marc 2026-09-17) |

## Open questions

### G1 — Nav — **settled A**

### G2 — Remediation home — **settled A**

### G3 — Investigation landing — **settled C**

Christophe: B or C OK; **C** most honest with S5. Red lines: no parallel journal; no claim that remediate rewrites sealed history; never present alerts/incidents as the cryptographic audit.

### G4 — Surface naming (i18n / nav label) — resume here

| Option | Meaning |
|--------|---------|
| **A** | Nav label **Integrity**; seal/archive/confirm are remediation actions under it. |
| **B** | Nav label **Integrity & Lifecycle**, with Lifecycle **narrowly** = seal/archive/confirm only (not entity lifecycle-model). |

### G5 — Deep-link compatibility

Preserve or redirect: `integrity=1`, `focusCheckpointId`, `source=integrity-alert`, highlight params. Prefer redirects over silent break during refactor.

---

## Operator answers

| ID | Answer | Date |
|----|--------|------|
| G1 | **A** — GA-only sidebar item; `/audit-logs` trail for all | 2026-09-17 |
| G2 | **A** — Alerts Resolve → Integrity remediation (single home) | 2026-09-17 |
| G3 | **C** — Integrity investigate/resolve; trail only around-event; same audit_log | 2026-09-17 |
| G4 | _pending_ | |
| G5 | _pending_ | |

## Links

- Intention note (parent)
- [`../../integrity-wave-b-operator-end-state-compass.md`](../../integrity-wave-b-operator-end-state-compass.md)
- Prior roles: grill A2 in `integrity-cluster-D4-D6-grill-me.md`
