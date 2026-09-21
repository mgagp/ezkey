# Admin UI — Integrity cut-2 phase A (progressive disclosure)

## Metadata

- **Document ID:** `admin-ui-integrity-cut2-phase-a`
- **Status:** `delivered` (UI density / progressive disclosure — phase A shipped PR `#588`, 2026-09-21). Density rules remain in force inside later modes.
- **Owner (intention):** Julie / Marc
- **QA:** Isabelle via Walk Gate
- **Purpose:** Intention lock for **cut-2 phase A** inside the single `/integrity` atelier: invisible when healthy, explicit when not. **Not** mode tabs, not incidents pager, not API redesign.
- **Next:** Phase **B + B′** (in-page Observe / Verify / Remediate modes + incidents pager) — locked: [`admin-ui-integrity-cut2-phase-b.md`](admin-ui-integrity-cut2-phase-b.md).
- **Related:**
  - Parent IA: [`admin-ui-audit-integrity-hard-split-intention.md`](admin-ui-audit-integrity-hard-split-intention.md) (cut 2)
  - Job surface map: [`admin-ui-audit-integrity-job-surface-map.md`](admin-ui-audit-integrity-job-surface-map.md)
  - Walk Gate: [`backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-a.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-a.md)
  - Next phase: [`admin-ui-integrity-cut2-phase-b.md`](admin-ui-integrity-cut2-phase-b.md) + [`backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-b.md)
  - Cut-3 lab seeds (reuse for non-healthy): [`docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md`](../../docs/lab/INTEGRITY_CUT3_EXPLORATORY_QA.md)
  - Walk / Done canon: [`ui-walk-done-gate.md`](ui-walk-done-gate.md)

---

## Product intention (locked Marc + Julie)

- Keep **one** `/integrity` atelier (no new nav).
- Phase A only: density / progressive disclosure.
- **Not** Observe / Verify / Remediate tabs yet; **not** incidents pagination.
- Goal: **invisible when healthy**, explicit when not — Ezkey is not the adopter’s core business.

## Behavioral contract (phase A)

| Surface | Healthy open | Non-healthy / deep-link |
|---------|--------------|-------------------------|
| Verification | Always primary; auto chain-verify on mount OK | Unchanged |
| Exceptional Maintenance | Collapsed | Auto-expand with Remediate cluster |
| Operational incidents | Collapsed | Auto-expand when actionable (`RECOVERED_PENDING_DECLARATION` / `IN_PROGRESS`) or deep-link |
| Undeclared gaps list | Empty compact or collapsed | Expand when gaps present |
| Checkpoint timeline | Collapsed | Force-open only for `focusCheckpointId`, gap locate, or `source=integrity-alert` — **not** for `action=reconcile` alone |

Deep-links that open Remediate: `action=reconcile`, `source=integrity-alert`, gap focus / locate, plus server non-green signals (gaps, actionable incident, sealed awaiting confirm, chain non-green).

## Non-goals

- No Alerts chrome changes.
- No API redesign / no second journal.
- No cut-2 phase B mode chrome.
- No incidents pager.
- No tamper-proof copy (Christophe honesty).

## Implementation locus

- Admin UI: `ezkey-admin-ui/src/pages/integrity.tsx`
- Pure open/closed rules: `ezkey-admin-ui/src/lib/integrity-progressive-disclosure.ts`

## Done

Walk Gate PASS on one SHA — see [`WALK-2026-09-21-integrity-cut2-phase-a.md`](backlog/walk-gates/WALK-2026-09-21-integrity-cut2-phase-a.md).

**A delivered / B locked:** Phase A UI is on `main` (PR `#588`). Cut-2 layout continues with phase **B + B′** — [`admin-ui-integrity-cut2-phase-b.md`](admin-ui-integrity-cut2-phase-b.md) (intention-locked 2026-09-21; implementation CA after Julie review).
