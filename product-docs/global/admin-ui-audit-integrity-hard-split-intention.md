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
| Priority framing | **Not P0** and **no hard date**. Not a demoted re-read. Frame as: deliberate Admin UI opérabilité program **after Wave B integrity R1 landed** — the semantics exist; the IA should now match them honestly. Pace = do it properly. Does **not** invent a new September gate. |
| Phase 2 | After the split is **walkable**: close **API ↔ UI** gaps (`reconcile-integrity-rupture`, `confirm-archived`, and any peers) so product intention is testable end-to-end — before calling the area “done.” |
| Alerts vs Integrity | **Alerts** = exceptional signal list. **Integrity** = investigation + remediation home. Do **not** merge Alerts into Integrity chrome. |
| Proof model | **One proof, multiple views** on the same `audit_log` (HMAC + chain). No second event journal. Alerts / incidents / conciliation = operational indexes, not audit citizens. Conciliation = explanation not rewrite. (Christophe) |
| Nav (G1) | New **sidebar item**, **Global Admin only**. `/audit-logs` stays trail-only for all roles. Tenant Admin never sees Integrity nav. |
| Remediation (G2) | Alerts detail **Resolve** deep-links into **Integrity** remediation UI — single atelier for reconcile / confirm archived / peers. |
| Investigation (G3) | **C** — Alerts → Integrity for investigate / verify / resolve; trail only for view-around-event; same `audit_log`; crypto unchanged. (Marc; Christophe reco) |
| Verification | Isabelle exploratory QA once walkable. |
| Quality bar | Clean split; careful refactor; no shadow zones; proportional SME atelier (not Phase-1 forensics factory). |

**Vocabulary (closed):**

- **Declutter** = stay on `/audit-logs`, reorganize the accordion / sections in place.
- **Hard split** = two distinct citizens (separate nav / journeys): everyday **audit trail** vs **integrity** (investigation + remediation) ops — both views of the **same** cryptographic journal.

**Naming boundary (Alex — pick one in grill G4):**

- If the nav keeps “Integrity & Lifecycle”, **Lifecycle** means only audit **seal / archive / confirm** paths — **not** the global entity model in [`lifecycle-model.md`](lifecycle-model.md).
- Prefer nav label **Integrity** if “Lifecycle” would confuse operators or docs; archive / confirm sit under it as remediation actions.

**Copy red lines (Christophe):**

- Say: tamper-**evident**; Integrity verifies chain/HMAC of trail rows; alerts = signals; remediation explains/reconciles, does not replace the journal; host-trust ceiling remains honest.
- Never say: tamper-proof / immutable (while export SPI not shipped); “complete audit guaranteed” (fail-open writes); SOC 2 equivalence; that Alerts or incident stores **are** the audit; that Resolve silently “fixes” integrity as a rewrite.

---

## Operator jobs we are serving

| Job family | Examples | Belongs after split |
|------------|----------|---------------------|
| **Browse / search trail** | Filters, pagination, row detail | Audit trail |
| **Entity / event context** | From enrollment / auth / integration; around-one-event | Audit trail (including G3 bridge) |
| **Integrity investigation** | Affected rows from rupture alert | Integrity (G3) — same `audit_log` rows |
| **Verify (read-only)** | Chain verify, entry HMAC verify | Integrity |
| **Detect + alert** | Run validation (may raise/touch rupture alert) | Integrity |
| **Observability** | Overview, checkpoint timeline | Integrity |
| **Exceptional remediation** | Seal archive, declare gap, confirm archived | Integrity (G2) |
| **Heartbeat incidents** | List + declare closure | Integrity (remediation) — signal via Alerts |
| **Rupture resolution** | Reconcile / conciliation | Integrity (G2); Alerts deep-link only |

---

## Current placement (as-is)

- **Route:** first-class `/audit-logs` (flat sidebar — not nested under Settings).
- **Problem:** not missing nav — **overgrowth inside one page** (`ezkey-admin-ui/src/pages/audit-logs.tsx` ~2.9k LOC).
- **Integrity panel:** collapsible (~1.3k LOC), Global Admin only; Tenant Admin sees list/detail/context only.
- Matrix already calls checkpoints **“Embedded in audit-logs Integrity panel”** — that embedding is what we undo.

---

## Proposed mental model

1. **`/audit-logs` — Audit trail (everyday, all roles)**  
   Who did what? Filter, read, open detail, entity/event context, and **view-around-event** bridges from Integrity. Not the rupture investigation home.

2. **Integrity — first-class sidebar (Global Admin only)**  
   Investigate affected trail rows → verify → detective run → remediate (seal/archive/confirm included). Reached from Alerts Resolve (G2) and as investigation landing (G3).

3. **Alerts — exceptional signal list**  
   Detection entry. Resolve / Investigate deep-link into Integrity. Not merged into Integrity chrome; not presented as the cryptographic audit.

---

## Open IA options (grilling)

See grill session file. Still open:

1. ~~Nav / roles~~ — **settled G1-A**.
2. ~~Remediation home~~ — **settled G2-A**.
3. ~~Investigation landing~~ — **settled G3-C**.
4. Nav label: **Integrity** vs **Integrity & Lifecycle** (with Lifecycle narrowly defined).
5. Deep-link compatibility (`integrity=1`, `source=integrity-alert`, etc.).

Flag Alex only if a choice would dilute the hard split or blur Alerts vs Integrity.

---

## Role story (baseline from prior integrity grill)

Already settled in integrity cluster grilling (**A2**): Global Admin only for cryptographic investigation / declaration / reattachment; Tenant Admin may consult audits but must not gain crypto-ops visibility. **G1** makes that asymmetry obvious in nav.

---

## Relationship to Alerts

Detect → Alert (signal) → Investigate/Resolve deep-link → Integrity (same `audit_log` rows + verify + remediate). Optional bridge back to trail for around-event context only.

---

## Non-goals

- No API contract redesign in this note.
- No second audit event store.
- No pixel / branding work.
- No implementation sequencing beyond “UI hard split first (walkable), API↔UI gap pass second (before done).”
- No change to cryptographic semantics (tamper-evident, Explained ≠ valid, etc.).
- No merge of Alerts into Integrity.

---

## Next artifacts

1. Close grill → promote settled rows into this note.
2. Job → surface map (one page).
3. Hand implementation sequencing to engineering (Patrick / domain owners); Isabelle walks live UI; Alex keeps priority framing as above.
