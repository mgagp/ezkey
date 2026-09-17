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
| **Resume at** | G1 |

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

## Open questions

### G1 — Nav for the Integrity citizen (resume here)

After hard split, how does Global Admin **find** Integrity?

| Option | Meaning |
|--------|---------|
| **A** | New **sidebar item** (Global Admin only); `/audit-logs` stays for all roles as trail-only. |
| **B** | Keep a single “Audit” nav parent with two children (Trail / Integrity) — only if we accept a nested nav pattern. |
| **C** | Other (operator specifies). |

**Constraint:** Tenant Admin must not stumble into crypto-ops chrome.

### G2 — Home for remediation actions (within S5)

OpenAPI includes **reconcile integrity rupture** and **confirm archived**. Alex rule: Alerts signal, Integrity remediates. Detail:

| Option | Meaning |
|--------|---------|
| **A** | Alerts detail keeps a **Resolve** entry that deep-links into Integrity remediation UI (single home for reconcile / confirm). |
| **B** | Full remediation UI embedded on Alerts detail; Integrity is verify/timeline only — **risk:** blurs S5; flag Alex if chosen. |
| **C** | Split by family only if needed (e.g. rupture reconcile vs archive confirm on different Integrity sections) — Alerts still only signals + deep-link. |

### G3 — Integrity-alert investigation landing

Today `source=integrity-alert` lands on Audit Logs with sessionStorage + affected-only table.

| Option | Meaning |
|--------|---------|
| **A** | Stay on **Audit trail** as investigation table (Integrity remains ops console). |
| **B** | Move investigation landing to **Integrity** (trail stays calm) — aligns with S5. |
| **C** | Thin bridge: Alerts → Integrity for verify/resolve; trail only for “view around event”. |

### G4 — Surface naming (i18n / nav label)

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
| G1 | _pending_ | |
| G2 | _pending_ | |
| G3 | _pending_ | |
| G4 | _pending_ | |
| G5 | _pending_ | |

## Links

- Intention note (parent)
- [`../../integrity-wave-b-operator-end-state-compass.md`](../../integrity-wave-b-operator-end-state-compass.md)
- Prior roles: grill A2 in `integrity-cluster-D4-D6-grill-me.md`
