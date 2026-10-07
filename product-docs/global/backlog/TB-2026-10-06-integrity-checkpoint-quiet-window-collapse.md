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
- **Updated at:** `2026-10-07`
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

## Operational quality matrix (Global Admin)

Audience: a pragmatic Global Admin for whom Ezkey is never core business — Integrity
work must stay efficient, simple, and understandable. Criteria language is FR/EN-agnostic
(observable chrome and behavior, not locale slogans). Use after UI delivery and after
Julie’s Walk Gate GO (see Walk / observation protocol below).

| Quality | Observable criterion | Observation method | Evidence (screenshot) |
|---|---|---|---|
| **Non-confusion grey vs orange** | Quiet collapse and real undeclared gaps do **not** share the same visual language or wording (honesty of chrome). Grey summary copy/style ≠ orange Gap copy/style. Collapsing quiet `REGULAR` must not invent orange client-side Gap rows solely from skipped empties; expand then collapse must not change real vs false gap semantics. | Idle quiet stretch with compress on; compare collapsed vs expanded; place a real undeclared gap in range only if the lab can produce one without breaking integrity. | Collapsed quiet (grey only, no spurious orange) + orange Gap chrome distinct if present |
| **Expand discoverability** | Operator understands they can reopen each chain link; continuity remains inspectable (not “gone”). Expand control has a clear affordance; expanding restores each individual `REGULAR` row. | Locate the expand control on a grey quiet summary; activate it; confirm every collapsed window reappears as its own row. | Affordance visible on summary + expanded individual `REGULAR` rows |
| **No false reassurance** | Collapse ≠ “all is fine.” A quiet window with `createdAt` clearly after window end shows a distinct late/catch-up badge and is not silently treated as normal quiet. **Operability preference:** when the collapsed stretch includes a late-written window, the late badge should appear on the **grey summary** (otherwise collapse reads as false reassurance until expand). Placement after expand alone remains acceptable only if observation notes which placement shipped. | Induce catch-up if possible (scheduler pause then resume, or documented lab path); else mark **N/A** with reason — do **not** fake timestamps by raw DB rewrite that breaks integrity. | Late badge readable (prefer summary; note if expand-only), when available |
| **Non-REGULAR signals always present** | `GAP_DECLARATION`, `MANIPULATION_CONCILIATION`, and sealed archive types (as applicable) in range stay visible as first-class rows; never folded into the grey quiet summary and never hidden by any leftover hide-empty control. | Ensure at least one declared gap / conciliation is in the viewed range (declare via the normal operator path if available; do **not** invent crypto-breaking DB edits). | Non-`REGULAR` row visible beside grey quiet |
| **Recent default + URL-only** | Fresh open of Verify shows ~last 24h, newest-first (not the oldest history page). Compress/filter state lives in the URL only; a shared URL reproduces state; leaving and returning without that URL does not silently hide rows (no localStorage sticky hide). | Cold open / new session without stale URL params; then copy URL with compress on and open a clean session without params. | Top of timeline is recent; URL bar + list (optional but preferred for URL half) |
| **Bounded idle scan** | Idle ~1h of quiet `REGULAR` windows compresses to one (or few) expandable grey summary rows, not a page of empty zeros (~12 rows flooding the table). | Require enough quiet to exercise collapse of ~10 empty `REGULAR` windows (prefer ≥30–60 min idle, or an equivalent lab-compressed quiet stretch that yields ~10 consecutive quiet windows); open Integrity → Verify → checkpoint timeline with compress on. Observation notes must record the window count collapsed. | Collapsed quiet view (bounded summaries) + notes with collapsed window count |
| **Walk Gate–derivable asserts** | Every quality in this matrix maps to a concrete Isabelle observation + screenshot (actionable, not slogans). No matrix row may be reported as pass on wording alone. | Walk protocol below: for **each** of the six qualities above, record pass/fail + notes + screenshot path (or explicit N/A + reason). | Observation report covering all rows |

## Walk / observation protocol

For Isabelle (post-UI observation), after Julie’s Walk Gate. This protocol is the
actionable form of **Walk Gate–derivable asserts** in the matrix above.

1. **Prerequisite:** UI landed on a follow-on PR; Walk Gate Julie **GO** first.
2. **Timebox:**
   - For **Bounded idle scan**: require enough quiet to exercise collapse of ~10 empty
     `REGULAR` windows (prefer ≥30–60 min idle, or an equivalent lab-compressed quiet
     stretch that yields ~10 consecutive quiet windows). Observation notes must record
     the window count collapsed. Two–three 5-min windows alone are **not** enough to
     judge that row.
   - Other matrix qualities may use a shorter quiet stretch once collapse chrome exists,
     unless they depend on the long idle case.
   - Crypto integrity forbids casual checkpoint row injection — prefer real cadence /
     declare-gap flows over raw DB edits.
3. **Every matrix row → assert:** for each Quality in the Operational quality matrix
   (including the six chrome/behavior rows and the Walk Gate–derivable asserts row as
   report completeness), record **pass/fail** + **notes** + **screenshot path**
   (or **N/A** + reason when a lab path is unavailable — never invent integrity-breaking
   fixtures).
4. **Optional locale spot-check (Julie/Isabelle):** when both FR and EN are available,
   briefly confirm quiet-summary wording in each locale (Must #8 — FR+EN copy; not an
   8th matrix Quality row).
5. **Deliverable:** short observation report + screenshots on the UI PR or issue `#673`,
   structured as a checklist against this matrix (not free-form slogans).

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
- **Isabelle checklist (after Julie Walk Gate):** Operational quality matrix (Global
  Admin) above — every Quality row → pass/fail + notes + screenshot path (see Walk /
  observation protocol).
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
