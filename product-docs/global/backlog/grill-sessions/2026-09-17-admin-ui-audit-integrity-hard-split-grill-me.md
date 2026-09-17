# Grill Me — Admin UI Audit trail vs Integrity & Lifecycle hard split

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
| **S0** | Split shape | **Hard split** — Integrity & Lifecycle is first-class; not declutter-in-place. |
| **S1** | Cadence | No hard pub date; do it properly; Alex reflects priority. |
| **S2** | Phase order | UI hard split first; API↔UI gap closure second (end-to-end intention for final tests). |
| **S3** | QA | Isabelle exploratory once walkable. |
| **S4** | Roles baseline | Prior integrity grill A2: GA for crypto ops; Tenant Admin consults trail only. |

## Open questions

### G1 — Nav for the Integrity citizen (resume here)

After hard split, how does Global Admin **find** Integrity & Lifecycle?

| Option | Meaning |
|--------|---------|
| **A** | New **sidebar item** (Global Admin only); `/audit-logs` stays for all roles as trail-only. |
| **B** | Keep a single “Audit” nav parent with two children (Trail / Integrity) — only if we accept a nested nav pattern. |
| **C** | Other (operator specifies). |

**Constraint:** Tenant Admin must not stumble into crypto-ops chrome.

### G2 — Home for unfinished / uneven lifecycle actions

OpenAPI includes ops such as **reconcile integrity rupture** and **confirm archived**. Wave B compass places **Reconcile** on the alert → resolve journey. After hard split:

| Option | Meaning |
|--------|---------|
| **A** | Resolution actions live on **Alerts detail**; Integrity surface is verify / timeline / exceptional maintenance. |
| **B** | Resolution actions live on **Integrity** surface; Alerts only deep-link in. |
| **C** | Split by family (e.g. rupture reconcile on Alerts; archive confirm on Integrity). |

### G3 — Integrity-alert investigation landing

Today `source=integrity-alert` lands on Audit Logs with sessionStorage + affected-only table.

| Option | Meaning |
|--------|---------|
| **A** | Stay on **Audit trail** as investigation table (Integrity remains ops console). |
| **B** | Move investigation landing to **Integrity** (trail stays calm). |
| **C** | Thin bridge: Alerts → Integrity for verify/resolve; trail only for “view around event”. |

### G4 — Surface naming (i18n label)

Working label **Integrity & Lifecycle** matches current panel title. Confirm or rename for sidebar.

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
