# Grill Me — Admin UI Audit trail vs Integrity hard split

## Session control

| Field | Value |
|-------|--------|
| **Intention note** | [`../../admin-ui-audit-integrity-hard-split-intention.md`](../../admin-ui-audit-integrity-hard-split-intention.md) |
| **Started** | `2026-09-17` |
| **Status** | `complete` |
| **Griller** | Julie |
| **Operator** | Marc |
| **Product witness** | Alex |
| **Security witness** | Christophe |
| **Resume at** | — |

## Settled decisions

| ID | Topic | Decision |
|----|--------|----------|
| **S0** | Split shape | **Hard split** — Integrity first-class; not declutter-in-place. |
| **S1** | Priority | Not P0 / no hard date. After Wave B R1 — IA matches shipped semantics. No new September gate. (Alex) |
| **S2** | Phase order | UI hard split until walkable, then API↔UI gap closure before “done.” |
| **S3** | QA | Isabelle exploratory once walkable. |
| **S4** | Roles | GA for crypto ops; Tenant Admin trail only. |
| **S5** | Alerts vs Integrity | Alerts = signal list; Integrity = investigation + remediation. Do not merge. (Alex) |
| **S6** | Nav label | **Integrity** (G4). Seal/archive/confirm = remediation under that screen. |
| **S7** | Proof model | **One proof, multiple views** on the same `audit_log`. No second event journal. Complementary tables = operational indexes. Conciliation = explanation not rewrite. (Christophe) |
| **S8** | Delivery posture | **Fully greenfield** — private repo, no prod installs, EXP1 disposable. No migration / redirect / dual-read / transition shims. (Marc 2026-09-17) |
| **G1** | Nav | New GA-only sidebar item; `/audit-logs` trail for all. |
| **G2** | Remediation | Alerts Resolve → Integrity (single atelier). |
| **G3** | Investigation | Integrity for investigate/verify/resolve; trail only for around-event; same `audit_log`. (Christophe-aligned) |
| **G4** | Label | Sidebar **Integrity**. |
| **G5** | Deep-links | **Greenfield clean-cut:** rewrite in-app emitters to the new Integrity route. **No** redirects, dual-read, or dead transition code for old `/audit-logs?integrity=…` params — not even for local-dev comfort. |

## Closed questions (no open IA items)

G1–G5 settled. Implementation may proceed from the intention note + this grill.

**Note on letter labels:** G1–G4 used A/B/C options during grilling. G5 is recorded in plain language only (no letter) to avoid remapping ambiguity after the greenfield reframe.

## Delivery closeout (2026-09-18)

Cut 1 implemented on PR `#563` (merged to `main`). Docs pack remains this grill + the parent intention + job-surface map — **no** `I-*` / `TB-*` invented for the split.

| Item | Disposition |
|------|-------------|
| Cut 1 — first-class `/integrity` | **Delivered.** Trail without atelier; GA sidebar; in-app emitters rewritten; no shims. |
| Cut-1 leftover chrome (page caret / outer box) | **Delivered** in the same PR. Not the full cut-2 layout challenge. |
| Dashboard job-card collapse + Open Integrity CTA | Companion hygiene in the same PR. |
| Cut 2 — layout inside Integrity | **Open.** Later. |
| Cut 3 — API↔UI gaps | **Open.** After walkable (cut 1 is walkable). |
| Isabelle exploratory | **Open.** Cut 1 is on a stack. |

## Links

- Intention note (parent)
- [`../../integrity-wave-b-operator-end-state-compass.md`](../../integrity-wave-b-operator-end-state-compass.md)
