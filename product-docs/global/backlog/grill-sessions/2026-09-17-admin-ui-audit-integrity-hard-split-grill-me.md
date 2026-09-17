# Grill Me — Admin UI Audit trail vs Integrity hard split

## Session control

| Field | Value |
|-------|--------|
| **Intention note** | [`../../admin-ui-audit-integrity-hard-split-intention.md`](../../admin-ui-audit-integrity-hard-split-intention.md) |
| **Started** | `2026-09-17` |
| **Status** | `open` — **G3 paused** pending Christophe |
| **Griller** | Julie |
| **Operator** | Marc |
| **Product witness** | Alex |
| **Security witness** | Christophe (asked 2026-09-17) |
| **Resume at** | G3 (after Christophe) |

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
| **G1** | Nav | **A** — New **sidebar item** (Global Admin only); `/audit-logs` stays trail-only for all roles. (Marc 2026-09-17) |
| **G2** | Remediation home | **A** — Alerts detail **Resolve** deep-links into Integrity remediation UI (single home for reconcile / confirm). (Marc 2026-09-17) |

## Open questions

### G1 — Nav — **settled A**

New Global-Admin-only sidebar item; `/audit-logs` remains the journal for all roles.

### G2 — Remediation home — **settled A**

Alerts stay the signal list; Resolve deep-links into Integrity. Reconcile / confirm archived (and peers) live on Integrity — one atelier.

### G3 — Integrity-alert investigation landing — **paused**

Marc’s constraint before choosing A/B/C: must **not** create a second (unsigned) event journal. Hard split must stay **multiple views on the same** HMAC/chain `audit_log`. Fear of dual-citizen audits (strong vs weak). Posture = SOC 2–adjacent honesty, not parity / not dogmatic re-signing.

Julie working claim for Christophe: Integrity = verify/remediate/checkpoints on **those same rows**, not a weaker parallel trail; real dilution risk is presenting operational indexes as cryptographic audit (INC-1).

| Option | Meaning |
|--------|---------|
| **A** | Keep the investigation table on **Audit trail** (Integrity = verify + remediate only). |
| **B** | Move investigation landing to **Integrity** (trail stays everyday-calm). |
| **C** | Thin bridge: Alerts → Integrity for investigate/verify/resolve; trail only for “view around event”. |

**Do not settle G3 until Christophe replies.**

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
| G1 | **A** — GA-only sidebar item; `/audit-logs` trail for all | 2026-09-17 |
| G2 | **A** — Alerts Resolve → Integrity remediation (single home) | 2026-09-17 |
| G3 | _paused — awaiting Christophe_ | 2026-09-17 |
| G4 | _pending_ | |
| G5 | _pending_ | |

## Links

- Intention note (parent)
- [`../../integrity-wave-b-operator-end-state-compass.md`](../../integrity-wave-b-operator-end-state-compass.md)
- Prior roles: grill A2 in `integrity-cluster-D4-D6-grill-me.md`
