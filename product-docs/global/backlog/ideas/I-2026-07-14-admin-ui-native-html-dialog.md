# Backlog Idea — `I-2026-07-14` Admin UI native HTML `<dialog>` migration

## Metadata

- **ID:** `I-2026-07-14-admin-ui-native-html-dialog`
- **Status:** `triaged`
- **Priority:** `P2`
- **Created at:** `2026-07-14`
- **Updated at:** `2026-07-14`
- **Last reviewed at:** `2026-07-14`
- **Progression markers:** `P3-polish`
- **Component tags:** `admin-ui`
- **Lane:** `C`
- **Captured by:** Marc (+ agent HITL during `doctor-curated` pass-1)
- **GitHub issue:** _(none yet)_

## Intent

Migrate Admin UI modal and dialog-like surfaces from custom `role="dialog"` markup to the
native HTML `<dialog>` element (via the shared `Dialog` shell and any peer overlays), so keyboard
and screen-reader operators get browser-grade focus trapping, Escape, and backdrop semantics
without relying solely on hand-rolled behavior — while preserving neo-brutalism styling and
existing `dismissible` / size contracts.

## Problem and value

- **Problem:** React Doctor (`prefer-html-dialog`) flags custom `role="dialog"` surfaces (shared
  `Dialog`, help drawer, account timezone menu, context-help). Today focus trap, Escape, and
  backdrop are implemented in application code. That works for the current shell, but diverges
  from platform defaults and raises ongoing a11y maintenance cost as more overlays are added.
- **Expected value:** Stronger, more predictable accessibility on high-traffic operator paths
  (create/edit, recovery codes, audit detail, help). Less bespoke overlay plumbing over time.
  Clear separation: structural a11y work lives in a **program slice**, not in punctual
  `doctor-curated` hygiene passes.

## Origin (hygiene HITL, not a hygiene fix)

Captured during `hygiene/admin-ui-doctor-pass-1` HITL on keyword `doctor-curated`:

| Decision for that pass | Rationale |
|------------------------|-----------|
| **skip** (do not fix in the polish PR) | Real a11y signal, high design cost — wrong lane for 80/20 hygiene |
| **do not suppress** yet | Keeping the rule visible avoids forgetting a structural debt; optional suppress later if noise dominates shortlists |

Earlier hygiene handoffs already deferred the same rule (`HANDOFF-admin-ui-hygiene-followups.md`).

## Scope

- **In scope (candidate program — bound before TB):**
  - Shared [`ezkey-admin-ui/src/components/ui/dialog.tsx`](../../../ezkey-admin-ui/src/components/ui/dialog.tsx): adopt `<dialog>` + `showModal()` / controlled open, preserve `dismissible`, sizes (`sm`…`lg-wide`), title/`aria-labelledby`, neo-brutal borders/shadows.
  - Inventory peer overlays that are dialog-like but not the shared component (`help-drawer`, `account-timezone-menu`, `context-help`, others if found).
  - Focus management parity: initial focus, restore focus on close, Escape vs `dismissible={false}`.
  - EN/FR unchanged unless copy must change; prefer behavior-only.
  - Targeted Playwright / MCP smoke on login wait dialogs and one create/edit dialog (device-backed when workflow-critical).
- **Out of scope:**
  - Rewriting all large page dialogs’ internal state (`prefer-useReducer`, giant-component splits).
  - Redesigning neo-brutal visual language.
  - Mobile app overlays.
  - Treating this as a routine `doctor-curated` allotment.

## Key assumptions

- Native `<dialog>` can be styled to match neo-brutalism without abandoning the design system.
- Most operator dialogs already go through the shared `Dialog`; fixing the shell delivers most value first (80/20).
- Drawers / non-modal popovers may need a **separate** treatment (`show()` non-modal vs `showModal()`); not every overlay is a modal.
- Browser support for the deployed Admin UI audience is sufficient for `<dialog>` (modern evergreen).

## Risks and exceptions

- **Visual / layout regression** if `::backdrop` or top-layer stacking fights Caddy/CSP or existing z-index.
- **`dismissible={false}`** (forms, secrets-once, live tests) must remain hard-guaranteed — Escape and backdrop must not close when false.
- **Help drawer / timezone menu** may be non-modal patterns; forcing `showModal()` could be wrong UX.
- **Playwright selectors** and focus assertions may need updates after migration.
- Scope creep into “rewrite every overlay” — force a first TB on the shared `Dialog` only.

## Candidate first slice (TB candidate)

1. Spike: replace shared `Dialog` implementation with `<dialog>` behind the same React props API.
2. Manual + one automated smoke: open/close, Escape, backdrop, `dismissible={false}` form dialog.
3. Leave help drawer / timezone / context-help for a follow-up TB unless they already wrap shared `Dialog`.
4. Document the shell contract in Admin UI docs (or AGENTS) so hygiene passes keep deferring the rule until the TB is done — then re-evaluate suppress.

## Promotion notes

Move to `ready` and open a TB when:

1. Inventory classifies each flagged surface as **modal shell** vs **non-modal overlay**.
2. First-slice boundary is agreed: shared `Dialog` only vs include N overlays.
3. Validation ladder is explicit (unit/RTL if any + Playwright smoke targets).
4. Optional: GitHub issue with five label groups for board visibility (`lane:c`, `type:feature` or `type:refactor`, `component:admin-ui`, `priority:p2`, `status:ready` when coding starts).

Until then: status stays `triaged` / `incubating`; hygiene passes continue to **skip** this finding without code change.

## Links

- Tool signal: React Doctor rule `prefer-html-dialog` (P1 in curated reports)
- Shared shell: `ezkey-admin-ui/src/components/ui/dialog.tsx`
- Agent contract: `ezkey-admin-ui/AGENTS.md` § React Doctor curated pass (challenge design-decision items)
- Prior deferral: `product-docs/global/backlog/handoffs/HANDOFF-admin-ui-hygiene-followups.md`
- Detail dialog layout notes (related, not the same work): `docs/admin-ui/DETAIL_DIALOG_STABLE_LAYOUT.md` (if present)
- Hygiene branch at capture: `hygiene/admin-ui-doctor-pass-1`
