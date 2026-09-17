# Admin UI — Audit trail vs Integrity hard split (intention)

## Metadata

- **Document ID:** `admin-ui-audit-integrity-hard-split-intention`
- **Status:** `draft` (grilling open — 2026-09-17)
- **Owner (intention):** Julie (Admin UI opérabilité)
- **Product direction:** Alex (priority / roadmap framing)
- **Security posture:** Christophe (G3 / one-proof constraint)
- **Exploratory QA (later):** Isabelle
- **Purpose:** Short intention / IA compass for splitting the overgrown Audit Logs surface.
  **Not** an implementation plan, API redesign, or pixel spec.
- **Related:**
  - [`integrity-wave-b-operator-end-state-compass.md`](integrity-wave-b-operator-end-state-compass.md)
  - [`integrity-cluster-design-pack.md`](integrity-cluster-design-pack.md)
  - [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md)
  - Grill: [`backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md`](backlog/grill-sessions/2026-09-17-admin-ui-audit-integrity-hard-split-grill-me.md)

---

## Purpose of this note

Keep Admin UI coherent with product intent: operable, simple, pragmatic, efficient — Ezkey is never the adopter’s core business. The Audit Logs page grew into a second product (cryptographic integrity / remediation ops) under a collapsible panel. This note locks the **mental model** and open IA choices before any refactor.

---

## Settled (2026-09-17 — Marc + Alex + Christophe)

| Decision | Value |
|----------|--------|
| Direction | **Hard split** — Integrity becomes a **first-class citizen**, not declutter-in-place on `/audit-logs`. No crypto-integrity shadow zones under ordinary audit browsing. |
| Priority framing | **Not P0** and **no hard date**. Deliberate Admin UI opérabilité **after Wave B integrity R1 landed** — IA matches shipped semantics honestly. No new September gate. |
| Phase 2 | After split is **walkable**: close **API ↔ UI** gaps (`reconcile-integrity-rupture`, `confirm-archived`, peers) before calling the area done. |
| Alerts vs Integrity | **Alerts** = signal list. **Integrity** = investigation + remediation home. Do not merge. |
| Proof model | **One proof, multiple views** on the same `audit_log` (HMAC + chain). No second event journal. Alerts / incidents / conciliation = operational indexes. Conciliation = explanation not rewrite. (Christophe) |
| Nav (G1) | New **sidebar item**, **Global Admin only**. `/audit-logs` trail-only for all roles. |
| Remediation (G2) | Alerts **Resolve** → Integrity remediation UI (single atelier). |
| Investigation (G3) | **C** — Integrity for investigate/verify/resolve; trail only for view-around-event; same `audit_log`. |
| Nav label (G4) | **Integrity**. Seal / archive / confirm = remediation actions under that screen (not a Lifecycle nav collision with [`lifecycle-model.md`](lifecycle-model.md)). |
| Verification | Isabelle exploratory once walkable. |
| Quality bar | Clean split; careful refactor; no shadow zones; proportional SME atelier. |

**Vocabulary (closed):**

- **Declutter** = stay on `/audit-logs`, reorganize in place.
- **Hard split** = separate nav/journeys: everyday **audit trail** vs **Integrity** — both views of the **same** cryptographic journal.

**Copy red lines (Christophe):**

- Say: tamper-**evident**; Integrity verifies chain/HMAC of trail rows; alerts = signals; remediation explains/reconciles, does not replace the journal; host-trust ceiling remains honest.
- Never say: tamper-proof / immutable (while export SPI not shipped); “complete audit guaranteed”; SOC 2 equivalence; that Alerts or incident stores **are** the audit; that Resolve silently rewrites integrity.

---

## Operator jobs we are serving

| Job family | Belongs after split |
|------------|---------------------|
| Browse / search trail; entity/event context; around-event bridge | Audit trail |
| Integrity investigation (affected rows); verify; detect+alert; checkpoints; seal/gap/confirm; heartbeat declare; rupture reconcile | Integrity |
| Exceptional signals | Alerts → deep-link Integrity |

---

## Current placement (as-is)

- `/audit-logs` first-class; overgrowth inside one ~2.9k LOC page; Integrity panel embedded (~1.3k LOC), GA only.

---

## Proposed mental model

1. **`/audit-logs` — Audit trail** (all roles): who did what; around-event bridges from Integrity.
2. **Integrity** (GA only, sidebar label **Integrity**): investigate → verify → remediate on the same `audit_log` rows.
3. **Alerts**: signal list only; deep-link into Integrity.

---

## Open IA options (grilling)

1. ~~G1–G4 settled.~~
2. **G5** — Deep-link compatibility for old `/audit-logs?integrity=…` / `source=integrity-alert` params.

---

## Non-goals

No API redesign; no second audit store; no pixel work; no crypto semantics change; no merge of Alerts into Integrity.

---

## Next artifacts

1. Close grill (G5) → mark intention `ready`.
2. Job → surface map.
3. Engineering sequencing + Isabelle exploratory; Alex priority framing as above.
