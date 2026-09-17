# Admin UI — Audit trail vs Integrity hard split (intention)

## Metadata

- **Document ID:** `admin-ui-audit-integrity-hard-split-intention`
- **Status:** `ready` (grilling complete — 2026-09-17)
- **Owner (intention):** Julie (Admin UI opérabilité)
- **Product direction:** Alex
- **Security posture:** Christophe
- **Exploratory QA (later):** Isabelle
- **Purpose:** Intention / IA compass for splitting the overgrown Audit Logs surface.
  **Not** an implementation plan, API redesign, or pixel spec.
- **Related:**
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
| Phase 2 | After split is walkable: close API↔UI gaps (`reconcile-integrity-rupture`, `confirm-archived`, peers) before calling the area done. |
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

---

## Non-goals

No API redesign in this note; no second audit store; no pixel work; no crypto semantics change; no Alerts merged into Integrity; no dead transition/compat code for deep-links.

---

## Next artifacts

1. Job → surface map (one page).
2. Engineering sequencing (Patrick / domain owners); Isabelle walks live UI.
3. Phase 2 API↔UI gap pass after walkable split.
