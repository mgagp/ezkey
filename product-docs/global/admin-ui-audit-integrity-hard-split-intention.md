# Admin UI — Audit trail vs Integrity hard split (intention)

## Metadata

- **Document ID:** `admin-ui-audit-integrity-hard-split-intention`
- **Status:** `ready` (grilling complete — 2026-09-17; Marc accepted the pack)
- **Owner (intention):** Julie (Admin UI opérabilité)
- **Product direction:** Alex
- **Engineering sequencing:** Patrick (refactor craft — asked 2026-09-17)
- **Security posture:** Christophe
- **Exploratory QA (later):** Isabelle
- **Purpose:** Intention / IA compass for splitting the overgrown Audit Logs surface.
  **Not** an implementation plan, API redesign, or pixel spec.
- **Related:**
  - [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md) ← job → surface map
  - [`integrity-wave-b-operator-end-state-compass.md`](integrity-wave-b-operator-end-state-compass.md)
  - [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md)
  - [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md)
  - Grill (complete): [`backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md`](backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md)

---

## Purpose

Keep Admin UI coherent with product intent: operable, simple, pragmatic, efficient — Ezkey is never the adopter’s core business. Audit Logs grew a second product (integrity / remediation) under a collapsible panel. This note locks the mental model before refactor.

---

## Settled (2026-09-17 — Marc + Alex + Christophe)

| Decision | Value |
|----------|--------|
| Direction | **Hard split** — Integrity first-class; not declutter-in-place on `/audit-logs`. |
| Priority | Not P0 / no hard date. After Wave B R1 — IA matches shipped semantics. No new September gate. |
| Delivery posture | **Fully greenfield** — no prod installs to spare; EXP1 disposable. No migration redirects, dual-read, or transition shims. |
| API gap pass | After split is walkable: close API↔UI gaps (`reconcile-integrity-rupture`, `confirm-archived`, peers) before calling the area done. |
| Alerts vs Integrity | Alerts = signal list. Integrity = investigation + remediation. Do not merge. |
| Proof model | **One proof, multiple views** on the same `audit_log` (HMAC + chain). No second event journal. Alerts / incidents / conciliation = operational indexes. Conciliation = explanation not rewrite. |
| Nav | New **Global Admin only** sidebar item. `/audit-logs` trail for all roles. |
| Nav label | **Integrity**. Seal / archive / confirm = remediation under that screen. |
| Remediation | Alerts Resolve → Integrity (single atelier). |
| Investigation | Integrity for investigate / verify / resolve; trail only for around-event; same `audit_log`. |
| Deep-links | Rewrite in-app emitters to the new Integrity route. **No** compat layer for old `/audit-logs?integrity=…` params. |
| QA | Isabelle exploratory once walkable. |
| Quality bar | Clean split; careful refactor; proportional SME atelier; no crypto shadow zones under ordinary browsing. |

**Vocabulary**

- **Hard split** = separate nav/journeys: everyday audit trail vs Integrity — both views of the **same** cryptographic journal.
- **Declutter** (rejected) = reorganize the accordion in place on `/audit-logs`.

**Copy red lines (Christophe)**

- Say: tamper-**evident**; Integrity verifies chain/HMAC of trail rows; alerts = signals; remediation explains/reconciles, does not replace the journal.
- Never say: tamper-proof / immutable (while export SPI not shipped); “complete audit guaranteed”; SOC 2 equivalence; that Alerts or incident stores **are** the audit; that Resolve silently rewrites integrity.

---

## Mental model

1. **`/audit-logs` — Audit trail** (all roles): who did what; around-event bridges from Integrity.
2. **Integrity** (GA only): investigate → verify → remediate on the same `audit_log` rows.
3. **Alerts**: signal list; deep-link into Integrity.

See [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md) for the full job → surface table and journeys.

---

## Delivery cuts (Marc 2026-09-17)

| Cut | What | What it is not |
|-----|------|----------------|
| **1 — First-class surface** | Refactor: lift today’s Integrity panel onto its own route + GA nav, rewire in-app deep-links (greenfield, no shims). Prove the locked journeys still work. | Not a visual redesign. Not pixel polish. Not a new forensic product. |
| **2 — UI detail inside Integrity** | Later: challenge layout, hierarchy, and chrome **inside** the Integrity surface. | Not blocking cut 1. |
| **3 — API↔UI gap pass** | After cut 1 is walkable: surface OpenAPI ops still missing a caller (`reconcile-integrity-rupture`, `confirm-archived`, peers) on Integrity. | Not a second audit store. |
| **QA** | Isabelle exploratory once cut 1 (then cut 3) is walkable. | Not exploratory QA as the design phase. |

Engineering sequencing of cut 1: Patrick (craft / file split). IA is not reopened.

---

## Non-goals

No API redesign in this note; no second audit store; no pixel campaign in cut 1; no crypto semantics change; no Alerts merged into Integrity; no dead transition/compat code for deep-links.

---

## Next artifacts

1. ~~Job → surface map~~ — done. Marc accepted the pack on PR #562.
2. Patrick sequencing read on the cut-1 refactor (in flight).
3. Implementation of cut 1, then Isabelle.
4. Cut 3 API↔UI gap pass before “done.” Cut 2 (UI detail) is a later phase.
