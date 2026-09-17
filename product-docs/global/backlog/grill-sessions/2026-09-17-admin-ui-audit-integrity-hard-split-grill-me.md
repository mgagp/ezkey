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
| **Resume at** | G5 |

## Settled before grilling

| ID | Topic | Decision |
|----|--------|----------|
| **S0** | Split shape | **Hard split** — Integrity is first-class; not declutter-in-place. No crypto shadow zones under ordinary browsing. |
| **S1** | Priority | Not P0 / no hard date. Deliberate Admin UI opérabilité **after Wave B R1** — IA matches shipped semantics. Not a demoted re-read; no new September gate. (Alex) |
| **S2** | Phase order | UI hard split until **walkable**, then API↔UI gap closure before calling the area done. |
| **S3** | QA | Isabelle exploratory once walkable. |
| **S4** | Roles baseline | Prior integrity grill A2: GA for crypto ops; Tenant Admin consults trail only. |
| **S5** | Alerts vs Integrity | Alerts = exceptional **signal list**; Integrity = **investigation + remediation** home. Do not merge. (Alex) |
| **S6** | Naming | **G4-A** — Nav label **Integrity**; seal/archive/confirm = remediation under it. (Marc 2026-09-17; Alex boundary) |
| **S7** | Proof model | **One proof, multiple views** on the same `audit_log` (HMAC + chain). No second event journal. Complementary tables = operational indexes. Conciliation = explanation not rewrite. (Christophe) |
| **G1** | Nav | **A** — New GA-only sidebar item; `/audit-logs` trail for all. |
| **G2** | Remediation home | **A** — Alerts Resolve → Integrity. |
| **G3** | Investigation landing | **C** — Integrity investigate/resolve; trail only around-event; same `audit_log`. |
| **G4** | Nav label | **A** — **Integrity**. |

## Open questions

### G5 — Deep-link compatibility — resume here

Today bookmarks / alert links use params on `/audit-logs` (`integrity=1`, `source=integrity-alert`, `focusCheckpointId`, highlights, etc.).

| Option | Meaning |
|--------|---------|
| **A** | **Redirect** old `/audit-logs?…` integrity params to the new Integrity route (preserve operator muscle memory / alert payloads). |
| **B** | **Break** old links; update only in-app emitters (alerts, dashboard) — bookmarks may 404-intent until manually fixed. |
| **C** | Dual-read for a transition window (both old and new URLs work), then remove dual-read later. |

Default lean: **A** or **C** — silent break is hostile for GA incident response.

---

## Operator answers

| ID | Answer | Date |
|----|--------|------|
| G1 | **A** — GA-only sidebar; `/audit-logs` trail for all | 2026-09-17 |
| G2 | **A** — Alerts Resolve → Integrity | 2026-09-17 |
| G3 | **C** — Integrity + trail around-event bridge; same audit_log | 2026-09-17 |
| G4 | **A** — Nav label **Integrity** | 2026-09-17 |
| G5 | _pending_ | |

## Links

- Intention note (parent)
- [`../../integrity-wave-b-operator-end-state-compass.md`](../../integrity-wave-b-operator-end-state-compass.md)
