# Tracer Bullet Brief — `TB-2026-10-06` Integrity checkpoint quiet-window collapse

## Metadata

- **ID:** `TB-2026-10-06-integrity-checkpoint-quiet-window-collapse`
- **Status:** `draft`
- **Related idea:** _(none — Lane D re-entry; intention locked with stakeholders; no new `I-*`)_
- **Lane:** `D` (post-delivery change — replace unsafe hide-empty UI shape)
- **Posture:** `single-pass`
- **Component:** `admin-ui`
- **Priority:** `P2`
- **GitHub issue:** `#673` (visibility only — this TB is canon)
- **Issue labels:** `lane:d`, `type:fix`, `type:security`, `component:admin-ui`, `priority:p2`, `status:needs-challenge`
- **Supersedes (hide-empty UI shape only):** `TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish` — that TB remains **`done`**; do **not** reopen it. Only the shipped « hide empty windows » / `entryCountMin=1` operator shape is superseded by this lock.
- **Created at:** `2026-10-06`
- **Updated at:** `2026-10-06`
- **Captured by:** Marc (analysis + ticket); stakeholders Julie (operability) + Christophe (security), lock `2026-10-06`

## Objective

Help a Global Admin scan the Integrity **checkpoint timeline** efficiently without losing
security signals. Quiet windows (0 audits every ~5 minutes) are noisy on idle instances
(~288 rows/day), but **hiding** them is unsafe. Prefer **collapsing** quiet `REGULAR`
windows into an expandable grey summary — never remove visual presence of chain links or
non-`REGULAR` checkpoint types.

This TB records the full UI intention lock in the documentary corpus so it is not
GitHub-only. Issue `#673` is optional board visibility.

## Prior context (do not reopen)

[`TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish`](TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md)
(`done`, Wave C) shipped the embedded/timeline polish including optional **hide empty
windows** (`entryCountMin=1`). That hide-empty shape is now **RED** (Christophe): fix or
remove before extending. This Lane D TB supersedes that UI shape only; the rest of the
prior TB (four types, badges, deep-link expand, matrix promotion) stays delivered history.

Surface today: Admin UI → Integrity → **Verify** → collapsible « Chronologie des
checkpoints » / « Checkpoint timeline »
(`ezkey-admin-ui` `integrity.tsx` / `CheckpointTimelineTable`). API already supports
`entryCountMin` on `GET /api/v1/audit-logs/chain-checkpoints`.

## Problem with current « Hide empty windows »

1. `entryCountMin=1` also hides `GAP_DECLARATION` and `MANIPULATION_CONCILIATION` (written
   with `entry_count=0`).
2. Client-side gap rows (orange) compare consecutive *visible* rows → false gaps when
   empties are filtered out.
3. Default sort is oldest-first; undeclared gaps are only inferred within the current page.
4. Late-backfilled quiet windows (scheduler catch-up within lookback) look like normal
   zeros; `createdAt` is not shown.

## Locked UI intention

### Must

1. **Remove or replace** the current hide-empty checkbox so it cannot hide non-`REGULAR`
   rows or invent false gaps.
2. **Collapse consecutive quiet `REGULAR` windows** (`entry_count = 0`) into one **grey**
   expandable summary row, e.g. FR « Calme · 14:05 → 15:00 · 11 fenêtres · 0 entrée » /
   EN equivalent. Operator must be able to **expand** to see each chain link.
3. **Never** collapse or hide non-`REGULAR` checkpoints (`GAP_DECLARATION`,
   `MANIPULATION_CONCILIATION`, sealed archive types as applicable).
4. Keep **real undeclared gaps** as distinct **orange** rows (not the same visual language
   as grey quiet).
5. **Default view:** last ~24 hours, newest-first (or equivalent “recent” default).
   Timeline must not open on the oldest page of history.
6. **Late-written windows:** badge when `createdAt` is clearly after window end
   (catch-up / clock skew / logging pause). Do not fold these into “normal quiet” without
   the badge.
7. Optional compress/filter state in the **URL** only (shareable). **No** sticky
   localStorage preference that leaves chain links hidden across visits.
8. **FR + EN** copy; no Mode C / internal jargon.

### Should

- Unit/UI tests: collapse does not hide GAP/conciliation; no false gap rows when
  collapsed; late badge; URL round-trip.
- **Walk Gate:** Julie before Isabelle when UI lands.

### Out of scope

- Changing checkpoint cadence / `EMPTY_WINDOW` digest semantics.
- Changing deny-list / TEMP console behavior.
- Sticky “always hide empties” operator preference.

## Acceptance (operator)

- Idle hour → one expandable grey quiet summary (or a few), not ~12 empty rows flooding
  the table.
- Declared gap / conciliation rows always visible when in range.
- Expanding quiet summary restores individual `REGULAR` rows; chain continuity remains
  inspectable.
- No orange “Gap” row solely because quiet windows were collapsed.
- Fresh open of Verify → recent window by default.
- Shared URL reproduces compress/filter state; leaving and returning without that URL
  does not silently hide rows.
- Late-written quiet window shows a distinct badge.
- Lock lives in this TB in-repo (corpus); issue `#673` is pointer only.

## Boundaries in scope (when implementation starts)

- Admin UI Integrity Verify checkpoint timeline: replace hide-empty with quiet-window
  collapse; default recent window; late-written badge; URL filter state; FR+EN.
- Matrix row **Audit chain checkpoints** in
  [`admin-ui-paginated-screens-matrix.md`](../admin-ui-paginated-screens-matrix.md): drop
  `entryCountMin=1` hide-empty prescription; cite this TB (minimal pointer may land with
  this docs PR; full matrix rewrite can wait for UI delivery).

## Out of scope (implementation)

- Admin API / checkpoint cadence / `EMPTY_WINDOW` semantics changes.
- TEMP console / deny-list changes.
- Sticky hide preference.
- Reopening or rewriting `TB-2026-07-05` Status.

## First executable slice

Docs-first (this PR): promote the lock into the corpus + optional one-line matrix
forward pointer. UI delivery is a follow-on PR against this TB (not against reopened
Wave C).

## Evidence plan

- **Corpus:** this TB; issue `#673` links here.
- **UI (later):** automated tests for collapse/GAP/false-gap/late badge/URL; Walk Gate
  Julie → Isabelle; Admin UI build.
- **Docs (on UI merge):** matrix row + locales; do not leave hide-empty as recommended
  operator shape.

## Exit criteria

- Global Admin can scan a quiet day without drowning in empty `REGULAR` rows, while
  GAP/conciliation and real orange gaps stay visible and distinct.
- Hide-empty / `entryCountMin=1` is no longer the operator control for quiet noise.
- Walk Gate passed (Julie before Isabelle); FR+EN present.
- Matrix and issue `#673` point at this TB as canon.

## Links

- Visibility: [GitHub issue #673](https://github.com/mgagp/ezkey/issues/673)
- Prior (done — cite only): [`TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md`](TB-2026-07-05-admin-ui-audit-chain-checkpoints-polish.md)
- Matrix: [`../admin-ui-paginated-screens-matrix.md`](../admin-ui-paginated-screens-matrix.md)
- Integrity IA: [`../admin-ui-audit-integrity-hard-split-intention.md`](../admin-ui-audit-integrity-hard-split-intention.md)
- Operator API: [`../../../docs/AUDIT_LOG_INTEGRITY.md`](../../../docs/AUDIT_LOG_INTEGRITY.md)
- Methodology: [`../../methodology/README.md`](../../methodology/README.md) (GitHub issues optional; corpus is canon)
