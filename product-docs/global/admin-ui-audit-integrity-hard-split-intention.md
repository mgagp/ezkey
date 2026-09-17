# Admin UI — Audit trail vs Integrity & Lifecycle hard split (intention)

## Metadata

- **Document ID:** `admin-ui-audit-integrity-hard-split-intention`
- **Status:** `draft` (grilling open — 2026-09-17)
- **Owner (intention):** Julie (Admin UI opérabilité)
- **Product direction:** Alex (priority / roadmap framing)
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

Keep Admin UI coherent with product intent: operable, simple, pragmatic, efficient — Ezkey is never the adopter’s core business. The Audit Logs page grew into a second product (cryptographic integrity / lifecycle ops) under a collapsible panel. This note locks the **mental model** and open IA choices before any refactor.

---

## Settled (2026-09-17 — Marc)

| Decision | Value |
|----------|--------|
| Direction | **Hard split** — Integrity & Lifecycle becomes a **first-class citizen**, not declutter-in-place on `/audit-logs`. |
| Quality bar | Clean split; careful refactor; **no shadow zones** for cryptographic audit integrity management. |
| Cadence | No publication urgency / no hard date — room to do it properly; product priority should reflect that (Alex). |
| Phase 2 | After UI split: close **API ↔ UI** gaps so final tests carry product intention end-to-end (ops present in OpenAPI but not clearly owned on an Admin surface). |
| Verification | Isabelle exploratory QA once the split is walkable. |

**Vocabulary (closed):**

- **Declutter** = stay on `/audit-logs`, reorganize the accordion / sections in place.
- **Hard split** = two distinct citizens (separate nav / journeys): everyday **audit trail** vs **integrity & lifecycle** ops.

---

## Operator jobs we are serving

| Job family | Examples | Belongs after split |
|------------|----------|---------------------|
| **Browse / search trail** | Filters, pagination, row detail | Audit trail |
| **Entity / event context** | From enrollment / auth / integration; around-one-event | Audit trail |
| **Integrity investigation landing** | Deep link from rupture alert; affected rows; HMAC honesty | Trail *and/or* Integrity — **grill** |
| **Verify (read-only)** | Chain verify, entry HMAC verify | Integrity |
| **Detect + alert** | Run validation (may raise/touch rupture alert) | Integrity |
| **Lifecycle observability** | Overview, checkpoint timeline | Integrity |
| **Exceptional maintenance** | Seal archive, declare gap | Integrity |
| **Heartbeat incidents** | List + declare closure | Integrity |
| **Rupture resolution** | Reconcile / conciliation (today partly Alerts-led) | **Grill** (Alerts vs Integrity) |

---

## Current placement (as-is)

- **Route:** first-class `/audit-logs` (flat sidebar — not nested under Settings).
- **Problem:** not missing nav — **overgrowth inside one page** (`ezkey-admin-ui/src/pages/audit-logs.tsx` ~2.9k LOC).
- **Integrity & Lifecycle:** collapsible panel (~1.3k LOC), Global Admin only; Tenant Admin sees list/detail/context only.
- Matrix already calls checkpoints **“Embedded in audit-logs Integrity panel”** — that embedding is what we undo.

---

## Proposed mental model

1. **`/audit-logs` — Audit trail (everyday)**  
   Who did what? Filter, read, open detail, enter/exit bounded investigation contexts. HMAC column remains **honest badges** when an integrity session exists, but the page is not the ops console.

2. **Integrity & Lifecycle — first-class (Global Admin)**  
   Observability → verification → detective run → exceptional maintenance. Clear hierarchy; help copy already calls seal/gap **exceptional** — the IA must match.

3. **Alerts — incident queue**  
   Remains the detection entry for rupture / gap / heartbeat. Deep links into trail and/or Integrity surfaces; ownership of **resolve** actions clarified in grill.

---

## Open IA options (grilling)

See grill session file. High-level:

1. Nav / roles for the new Integrity citizen (GA-only sidebar item vs other patterns).
2. Home for **reconcile rupture** and **confirm archived** after the split.
3. Where integrity-alert **investigation** lives (trail, Integrity, or thin bridge).
4. Naming of the Integrity surface (label i18n).
5. Deep-link compatibility (`integrity=1`, `source=integrity-alert`, etc.).

---

## Role story (baseline from prior integrity grill)

Already settled in integrity cluster grilling (**A2**): Global Admin only for cryptographic investigation / declaration / reattachment; Tenant Admin may consult audits but must not gain crypto-ops visibility. Hard split should **make that asymmetry obvious in nav**, not only via a panel that appears for GA on the same URL.

---

## Relationship to Alerts

Wave B compass journey: Detect → Alert → Investigate in Audit Logs → Verify → Resolve (Reconcile). Hard split must restate that journey without burying resolution or verification under the everyday trail.

---

## Non-goals

- No API contract redesign in this note.
- No pixel / branding work.
- No implementation sequencing beyond “UI hard split first, API↔UI gap pass second.”
- No change to cryptographic semantics (tamper-evident, Explained ≠ valid, etc.).

---

## Next artifacts

1. Close grill → promote settled rows into this note.
2. Job → surface map (one page).
3. Hand implementation sequencing to engineering (Patrick / domain owners); Isabelle walks live UI; Alex confirms priority framing.
