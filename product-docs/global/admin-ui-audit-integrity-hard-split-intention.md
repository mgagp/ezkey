# Admin UI — Audit trail vs Integrity hard split (intention)

## Metadata

- **Document ID:** `admin-ui-audit-integrity-hard-split-intention`
- **Status:** `ready` (grilling complete — 2026-09-17; Marc accepted the pack; cut-1 sequencing locked)
- **Owner (intention):** Julie (Admin UI opérabilité)
- **Product direction:** Alex
- **Engineering sequencing:** Patrick (cut 1 locked 2026-09-17 — not implementing)
- **Security posture:** Christophe
- **Exploratory QA (later):** Isabelle
- **Purpose:** Intention / IA compass for splitting the overgrown Audit Logs surface.
  **Not** an implementation plan, API redesign, or pixel spec.
- **Related:**
  - [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md)
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

See [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md).

---

## Delivery cuts (Marc 2026-09-17)

| Cut | What | What it is not |
|-----|------|----------------|
| **1 — First-class surface** | Refactor: lift today’s Integrity panel onto its own route + GA nav, rewire in-app deep-links (greenfield, no shims). | Not a visual redesign. Not a new forensic product. |
| **2 — UI detail inside Integrity** | Later: challenge layout and chrome **inside** Integrity. | Not blocking cut 1. |
| **3 — API↔UI gap pass** | After cut 1 is walkable: surface missing OpenAPI callers on Integrity. | Not a second audit store. |
| **QA** | Isabelle exploratory once cut 1 (then cut 3) is walkable. | Not the design phase. |

---

## Cut 1 sequencing (Patrick 2026-09-17)

IA holds. This cut is a **move**, not a second journal. Patrick is not implementing.

1. **One PR.** New Global-Admin-only route. Move the existing Integrity panel body onto that page. Do **not** split the panel into presentational files yet. `audit-logs.tsx` keeps the everyday trail only.
2. **Same PR, not a follow-up.** Rewrite every in-app emitter (Alerts Resolve / Investigate, any query that opened integrity inside audit-logs) onto the new route, then delete the embedded panel. No redirect, dual-read, or shim.
3. **One module** for the integrity surface (route + existing panel + its current API calls). Shared code with the trail is generated API types and existing audit-log reads only. Do not invent a client-side chain, a second event store, or a parallel integrity-journal model.
4. **Role gate:** `adminType` Global Admin (or `ROLE_GLOBAL_ADMIN`). Not a generic authenticated-admin gate. Tenant Admin must not see the surface.
5. **Do not touch:** HMAC/chain semantics, server emitters, tamper-proof copy, Alerts list behavior (only the link target), visual redesign, cut-3 API gaps. Those gaps stay documented holes, not mocked UI.

**Done:** trail page has no integrity atelier; Integrity is a first-class Global-only page; every in-app link lands there; one proof, same `audit_log` reads; no dead panel left in `audit-logs.tsx`.

---

## Non-goals

No API redesign in this note; no second audit store; no pixel campaign in cut 1; no crypto semantics change; no Alerts merged into Integrity; no dead transition/compat code for deep-links.

---

## Next artifacts

1. ~~Job → surface map~~ — done. Marc accepted the pack.
2. ~~Cut-1 sequencing~~ — locked above.
3. Implementation of cut 1 when Marc says go, then Isabelle.
4. Cut 3 after walkable. Cut 2 is a later phase.
