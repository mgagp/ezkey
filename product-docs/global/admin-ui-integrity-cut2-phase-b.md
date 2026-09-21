# Admin UI — Integrity cut-2 phase B + B′ (atelier modes + incidents pager)

## Metadata

- **Document ID:** `admin-ui-integrity-cut2-phase-b`
- **Status:** `intention-locked` (Marc + Julie, 2026-09-21 — **docs lock only**; implementation not started)
- **Owner (intention):** Julie / Marc
- **QA:** Isabelle via Walk Gate (after an implementation PR fills the walkable SHA)
- **Purpose:** Intention lock for **cut-2 phase B + B′** together, still on the **single** `/integrity` atelier: in-page **Observe | Verify | Remediate** modes, plus incidents-list pagination UI. **Not** a new sidebar item, **not** sub-routes, **not** Alerts redesign, **not** phase C status-strip / API work.
- **Related:**
  - Parent IA: [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md) (cut 2)
  - Phase A (delivered density rules): [`admin-ui-integrity-cut2-phase-a.md`](admin-ui-integrity-cut2-phase-a.md)
  - Job surface map: [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md)
  - Paginated list pattern: [`admin-ui-paginated-screens-matrix.md`](admin-ui-paginated-screens-matrix.md)
  - Walk Gate: [`backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md)
  - Cut-3 lab seeds (reuse for non-healthy / reconcile): [`docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md`](../../docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md)
  - Walk / Done canon: [`ui-walk-done-gate.md`](ui-walk-done-gate.md)

---

## Product intention (locked Marc + Julie, 2026-09-21)

- Keep **one** `/integrity` atelier (no new nav, no sub-routes, no nav split).
- Deliver **phase B + B′ together** in one walkable UI slice.
- **B (core):** In-page modes on the same page — **Observe | Verify | Remediate** (segmented control / local tabs). Modes are jobs inside the atelier, not new surfaces.
- **B′:** Operational incidents list gets a real pager. API `GET …/lifecycle/incidents` already returns a Spring `Page` (`page` / `size` / `sort`). UI today hardcodes `page=0&size=50` with no pager. Normalize to the existing Admin UI paginated-list pattern. **API change is not the first lever.**
- Phase A progressive disclosure stays as **density rules inside modes** (collapse / expand still apply; modes do not replace A).
- Phase **C** (richer status-strip / API) stays later — out of this note’s delivery.

---

## What it looks like

Mode chrome is a compact segmented control (or equivalent local tabs) at the top of `/integrity`. Labels:

| Mode (product) | EN label | FR label | Operator job (one line) |
|----------------|----------|----------|-------------------------|
| Observe | Observe | Observer | Glance / lifecycle overview when the chain is healthy |
| Verify | Verify | Vérifier | Read-only / detective verification (chain, entry HMAC, run validation) |
| Remediate | Remediate | Remédier | Exceptional maintenance, actionable incidents, gaps, reconcile |

Same atelier URL. Switching mode changes which blocks are primary / visible; it does **not** invent a second Integrity route.

---

## Default mode + deep-link mapping

| Entry condition | Default mode | Timeline / density notes |
|-----------------|--------------|--------------------------|
| Healthy bare `/integrity` | **Observe** | Phase A: Remediate cluster + checkpoint timeline stay collapsed |
| Non-green: undeclared gaps, actionable incident (`RECOVERED_PENDING_DECLARATION` / `IN_PROGRESS`), sealed awaiting confirm, or chain non-green | **Remediate** | Phase A auto-expand rules still apply inside Remediate |
| `action=reconcile` (e.g. Alerts Resolve) | **Remediate** | Timeline **not** forced open by reconcile alone (keep phase A rule) |
| `source=integrity-alert` / `focusCheckpointId` / gap locate | **Verify** | Timeline force-open as today (investigation / locate path) |
| Optional `?mode=observe\|verify\|remediate` | That mode | Preferred stable deep-link if we add the param; deriving mode from existing params alone is also OK |

Manual mode switch must always be available once the page is loaded (operator can leave the auto-selected mode).

---

## UI impacts

| Area | Impact |
|------|--------|
| `/integrity` chrome | Add Observe / Verify / Remediate segmented modes; regroup existing blocks under the active mode |
| Phase A density | Keep disclosure rules **inside** modes (healthy collapse, non-healthy expand, deep-link exceptions) |
| Locales | EN + FR labels for mode chrome (table above); no orphan keys |
| Alerts | Unchanged chrome; Resolve / Investigate still deep-link into `/integrity` with existing params |
| Dashboard | Unchanged chrome; follow-up / job-card exits still land on `/integrity` |
| Nav / routes | **No** new sidebar item; **No** `/integrity/observe` (etc.) sub-routes |
| Incidents list (B′) | Pager controls; requests must pass `page` / `size` (and keep sensible `sort`); empty and error states remain usable |

---

## Operating / links impacts

- Greenfield posture unchanged: **no** redirects, dual-read, or shims for old `/audit-logs?integrity=…` params.
- Existing deep-link emitters keep working; mode selection derives from the mapping table (and optional `mode` param if added).
- No change to Alerts or Dashboard as signal surfaces — they are not the atelier.

---

## B′ contract (incidents pagination)

- Surface: Operational incidents list on `/integrity` (under Remediate / density rules as today).
- API already pages: `GET /api/v1/audit-logs/lifecycle/incidents?page=&size=&sort=`.
- UI must stop hardcoding only `page=0&size=50` with no controls.
- Follow the existing Admin UI paginated-list pattern (same family as other GA lists / checkpoint timeline pager).
- Empty state and load/error states must remain usable (no blank dead end when page is empty or the request fails).
- **Do not** open with an API redesign; close the UI gap first.

---

## Non-goals

- Alerts list / detail redesign.
- Tamper-proof (or similar disallowed) copy — Christophe honesty red lines stay.
- Second audit / event journal.
- Phase **C** richer status-strip or status-strip API.
- Pixel polish campaign unrelated to mode chrome + pager.
- New Integrity nav item or sub-routes.
- Tenant Admin access to `/integrity`.

---

## Implementation locus (when coding starts)

- Admin UI: `ezkey-admin-ui/src/pages/integrity.tsx` (mode chrome + regroup; incidents pager).
- Phase A helpers remain authoritative for open/closed density: `ezkey-admin-ui/src/lib/integrity-progressive-disclosure.ts` (extend only if mode mapping needs shared pure rules).
- **Docs-only this PR:** no Admin UI / API code in the intention lock.

---

## Done

Walk Gate PASS on **one** SHA — see [`WALK-2026-09-21-integrity-cut2-phase-b.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md). Fill the walkable SHA/PR on that gate before Isabelle walks.
