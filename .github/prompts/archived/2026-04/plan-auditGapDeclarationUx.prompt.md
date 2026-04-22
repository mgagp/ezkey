# Plan: Simplify Audit Chain Gap Declaration UX

**Status:** ✅ Completed — April 2026. Phase 1 (UI restructure) shipped and validated end-to-end against the local clean-start Docker stack. Phase 2 (`ezkey_alert` table + cheaper undeclared-gaps endpoint) remains as future work, tracked here for reference.

**TL;DR** — Replace the current free-form "Declare gap" dialog (empty date pickers
+ anchor checkpoint ID) with a per-gap, intent-driven flow: each detected gap in
the "Undeclared gaps" list exposes two explicit actions — **Declare** (primary,
prefilled, justification only) and **Locate in timeline** (secondary, current
focus/scroll behavior). Remove the standalone top-level "Declare gap" entry
point. Auto-run the chain check when the integrity section is expanded so the
gaps list is never gated behind a manual click. Backend contract unchanged.

---

## Steps

### Phase 1 — UI restructure (no backend change)

1. **Remove the standalone top-level "Declare gap" button** in
   `ezkey-admin-ui/src/pages/audit-logs.tsx` (around line 962). Declaration
   becomes reachable only from a detected gap row.
2. **Per-gap row redesign** in the "Undeclared gaps for consultation" list
   (around lines 992–1015): replace the current single-click toggle that does
   double-duty (focus + clear focus) with an informational row + two explicit
   action buttons:
   - **`Declare`** (primary) — opens the dialog prefilled with this gap's
     `gapStart` / `gapEnd`.
   - **`Locate in timeline`** (secondary, ghost) — same button, label flips
     to **`Clear focus`** when this gap is the currently focused one.
     Reuses existing `focusGapAndNavigate()` behavior.
   - The row stays informational (gap label, period, duration). Click on the
     row body should **not** do anything navigational — only the buttons act.
   - Add an **empty-state** rendering: when the chain check has run and
     returned zero gaps, show an explicit *"No undeclared gaps detected for
     the selected period."* block instead of hiding the section. Reassures
     the cold-handover operator that the absence of items is a confirmed
     verdict, not a missing run.
3. **Refactor the gap dialog** (around lines 1196–1275):
   - Drop the `gapStart`, `gapEnd`, `anchorCheckpointId` `<Input>` controls.
   - Replace with a read-only summary block showing the period
     (`formatDateWithTimezone(gapStart) → ... (~N min)`).
   - Keep only the `ReasonFieldRow` (justification, min 10 chars, preset group
     `audit_chain_justification`).
   - Update the dialog title and intro copy to reflect *"You are about to
     formally declare this detected gap. Describe what happened during this
     period (crash, planned maintenance, etc.). If multiple events occurred in
     the same window, mention each in the justification."*.
   - **Reduce dialog width** from `size="lg"` to the default (or `"md"`):
     with only a summary block + one justification field, `lg` leaves the
     dialog visually half-empty.
   - On submit, send timestamp mode only:
     `{ gapStart, gapEnd, justification }` — no `anchorCheckpointId`.
   - **On success** (see new step 7): reset the dialog state and trigger the
     post-declaration refresh sequence.
4. **Wire selection state**: introduce `selectedGapForDeclaration` (replaces
   the implicit dependency on `gapStart`/`gapEnd` form fields). Setting it
   opens the dialog; clearing it (cancel/close/success) resets.
5. **Remove now-dead state**: `gapStart`, `gapEnd`, `gapAnchorId` setters and
   the `resetGapForm` helper if no longer used elsewhere.
6. **Auto-run chain check on section expand** (or on first mount when the
   integrity section is opened) so the gaps list is populated without
   requiring the operator to click *Run integrity check* first.
   - **Default window**: last 7 days (UI-side default). Operator can
     re-run with a wider range via the existing date picker. Rationale: it
     covers the common operational reality (recent crash / planned
     maintenance) without forcing a costly scan over months of audit data.
   - Debounce/cache to avoid hammering on every expand toggle.
   - **Note**: this is a UX shortcut for the operator already on the page,
     **not** the discoverability mitigation — that responsibility stays with
     the backend detection (today's `AUDIT_CHAIN_GAP_PENDING` audit entry,
     tomorrow's `ezkey_alert` row — see *Phase 2*).
   - **Tension surfaced (out of scope here, captured in Phase 2 below)**:
     the chain-integrity endpoint currently *requires* `from`/`to`. Picking
     a sensible default belongs on the backend (configurable lookback for
     this kind of scan) so the policy is the same whether the call comes
     from the UI, an operator script, or a future scheduled health check.
7. **Post-declaration refresh sequence** (consistency — reduces operator
   cognitive effort): on a successful `declareGap` response, in order:
   1. close the dialog and clear `selectedGapForDeclaration`,
   2. clear `focusedGap` if it matched the declared gap,
   3. re-run the chain integrity check (the just-declared gap should
      disappear from the list),
   4. invalidate / refetch the chain-checkpoints query (a new
      `GAP_DECLARATION` checkpoint was inserted and downstream checkpoints
      were re-chained — the timeline must reflect this).
8. **i18n**: add/rename keys
   - `gapDialog.title` → EN *"Declare detected gap"* / FR
     *"Déclarer la lacune détectée"*
   - `gapDialog.intro` → new guidance copy (multi-event hint included)
   - `gapDialog.detectedPeriod` → label for the read-only block
   - `integrity.gapAction.declare` → EN *"Declare"* / FR *"Déclarer"*
   - `integrity.gapAction.locate` → EN *"Locate in timeline"* /
     FR *"Localiser dans la chronologie"*
   - `integrity.gapAction.clearFocus` → EN *"Clear focus"* /
     FR *"Retirer le focus"*
   - `integrity.noUndeclaredGaps` → empty-state copy
   - Remove keys tied to dropped fields (`gapStart`, `gapEnd`,
     `anchorCheckpointId`, `anchorPlaceholder`).
   - Update both EN and FR locales.
9. **Update inline `ContextHelp` copy** to reflect the new flow (no more
   mention of anchor checkpoint ID or manual range).

### Verification & documentation (this slice)

10. Update `docs/AUDIT_LOG_INTEGRITY.md` operator workflow section:
    declaration is now strictly *select detected gap → justify*.
    Anchor-checkpoint mode remains documented for the API layer (Postman,
    CLI) but is no longer surfaced in the Admin UI.
11. Manual exploratory validation (clean-start) — see *Verification*.

### Out of scope of this slice (deferred follow-ups)

- **Backend `anchorCheckpointId` removal**: do **not** remove. Still useful
  for DB/CLI operability; only the UI stops surfacing it.
- **API contract changes** for the gap declaration endpoint: none required.
- **Phase 2 — backend gap-discovery query & SQL governance**: scoped below.
- **Phase 2 — minimal alert subsystem (`ezkey_alert`)**: scoped below as the
  bridge to the next iteration. Out of this slice's implementation scope, but
  intentionally captured here so the discoverability story is end-to-end.
- **Automated browser test for the new flow**: tracked in *Future testing
  strategy* below. Manual exploratory validation in this slice.

### Phase 2 — Minimal alert subsystem (next slice, sketched here for continuity)

**Context — what already exists on the backend:**
[`AuditChainScheduler.detectPreLookbackGap()`](ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditChainScheduler.java#L184)
runs on the regular checkpoint cron (default every 5 min). Each tick it
compares `latest.windowEnd` to the start of the rolling lookback window
(default 60 min). When the latest checkpoint falls before that horizon, it
emits an `AUDIT_CHAIN_GAP_PENDING` audit entry (`EventStatus.FAILURE`,
`apiName = ADMIN_API`, payload = `gapStart` / `estimatedGapEnd` /
`estimatedGapMinutes` / `anchorCheckpointId` / message). The dashboard query
today picks that audit entry up and surfaces the operator alert.

**Problem with the current shape:** the alert is encoded *as an audit entry*.
That pollutes the audit trail with what is really an operator-action signal,
couples the alert lifecycle to audit retention/sealing, and forces every
future alert type to be modeled as an audit row.

**Direction (kept deliberately minimal — essential complexity only):**

- Introduce a new `ezkey_alert` table — Ezkey owns its own minimal alert
  surface, consistent with the *self-contained / on-prem / eat-your-own-dog-food*
  positioning. No external alerting dependency.
- Minimal columns (sketch, to be confirmed in the Phase 2 plan):
  `alert_id`, `alert_type` (e.g. `AUDIT_CHAIN_GAP_PENDING`), `severity`,
  `status` (`OPEN` / `RESOLVED`), `created_at`, `resolved_at`, `payload`
  (JSON — gap boundaries, anchor checkpoint id, etc.), `dedupe_key` (so the
  scheduler tick doesn't insert a new row on every run for the same gap).
- **Producers (Phase 2):**
  1. `AuditChainScheduler.detectPreLookbackGap()` — replace the
     `auditLogService.log(alert)` call with an upsert into `ezkey_alert`
     keyed on `dedupe_key` (e.g. `"gap:" + anchorCheckpointId`).
  2. The chain-integrity verification path (whatever the UI's *Run integrity
     check* triggers, and the auto-run from step 6) — same upsert when it
     observes an undeclared gap.
- **Consumers (Phase 2):**
  1. New dashboard widget on the Admin UI dashboard page listing open
     alerts; replaces the current audit-derived alert rendering.
  2. The audit-logs page can keep listing detected gaps from the integrity
     verification report; declaring a gap should mark the matching
     `ezkey_alert` row as `RESOLVED` (atomic with the existing
     `GAP_DECLARATION` checkpoint creation).
- **Cleanup:** remove the `AUDIT_CHAIN_GAP_PENDING` audit emission and the
  dashboard query that scans for it. The `EventType` enum value can stay
  deprecated for one release if any historical audit rows still reference it.
- **Out of scope even for Phase 2**: notification channels (email, webhook,
  Slack, etc.). The `ezkey_alert` table is a passive operator-visible
  surface; channels are a later concern.

**Why mention this here at all:** to make explicit that this slice's
*Risk to call out — discoverability* is **not** unaddressed. The backend
already raises a structured signal on every scheduler tick; this slice adds a
modest UX shortcut (auto-run on expand); Phase 2 then promotes that signal
out of the audit trail and into a dedicated minimal alert surface.

### Phase 2 — Backend gap-discovery query & SQL governance (next slice, sketched here)

**Why surfaced now:** step 6 (auto-run on expand) reuses the existing
chain-integrity endpoint, which (a) requires `from`/`to` and (b) performs a
full HMAC re-verification of every checkpoint and audit entry in the range.
For on-demand UI use that's overkill, and at scale (months of audits) it
becomes a costly SQL + crypto operation. Picking the default window in the
UI works for now, but the policy belongs on the backend.

**Direction:**

1. **Externalize a default lookback** for gap-detection scans (e.g.
   `ezkey.audit.chain.gap-scan.default-lookback-days = 7`). When the API is
   called without `from`/`to`, it applies this default instead of returning
   400.
2. **Externalize a hard upper bound** on the scanned range (e.g.
   `ezkey.audit.chain.gap-scan.max-lookback-days = 90`) to cap worst-case
   SQL cost regardless of caller. Rejecting an over-wide request is
   acceptable — the operator can iterate.
3. **Decide between two API shapes:**
   - **Option A — piggyback on the existing chain-integrity endpoint**
     (`GET /api/v1/audit-logs/chain-integrity` already returns
     `undeclaredGaps` alongside the verification report).
     - **Pros**: zero new surface area; one fewer thing to document; the
       caller gets the gap list as a side effect of a check it would have
       done anyway.
     - **Cons**: forces every UI "is there a gap?" call to also re-verify
       every HMAC in range — the heaviest work for the lightest question;
       couples the UX shortcut to the heavy verification path; harder to
       cache (verification result is volatile).
   - **Option B — new dedicated endpoint**
     `GET /api/v1/audit-logs/lifecycle/undeclared-gaps`.
     - **Pros**: cheaper SQL (gap detection only needs to walk
       `audit_chain_checkpoint` for windowEnd → next windowStart
       discontinuities — no per-entry HMAC re-computation); cacheable;
       cleaner contract aligned with the operator's intent ("list gaps to
       declare"); pairs naturally with the `ezkey_alert` consumer in the
       next slice; lets the integrity endpoint keep its full-verification
       semantics for compliance/forensics use.
     - **Cons**: one more endpoint to document, version, and secure (Global
       Admin gate, OpenAPI, Postman).
   - **Recommended direction (to be confirmed in the Phase 2 plan):
     Option B.** The cost asymmetry is real (lightweight checkpoint walk vs
     full HMAC scan) and aligns with the *essential complexity only*
     principle — each endpoint expresses one operator intent. Option A's
     economy is illusory: it saves an endpoint at the cost of conflating two
     very different operations.
4. **Keep this slice's UI status quo**: the auto-run on expand still calls
   the existing chain-integrity endpoint. When the Phase 2 endpoint lands,
   the UI swaps the call site — no further visual change required.

**Notes for the future plan author:**

- This is also the right place to revisit pagination / streaming for
  long-range scans, and to decide whether `archive-eligibility` should
  remain co-located with gap discovery or stay separate (currently they
  serve different operator questions — likely keep separate).
- Whatever shape Phase 2 picks, both producers (scheduler tick, on-demand
  UI call) should converge on the same detection logic so the
  `ezkey_alert` upserts are consistent across paths.

---

## Relevant files

- `ezkey-admin-ui/src/pages/audit-logs.tsx`
  — primary edits: top-level *Declare gap* button (~L962), undeclared-gaps
  list rendering (~L992–1015), gap dialog form (~L1196–1275), state hooks
  (~L552–562), `focusGapAndNavigate` (~L651) stays as-is and is reused by the
  *Locate in timeline* action.
- `ezkey-admin-ui/src/locales/{en,fr}/audit-logs.json` (and `help` subtree if
  separate) — i18n updates.
- `docs/AUDIT_LOG_INTEGRITY.md` — operator procedure refresh.
- No changes to:
  - `ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java`
  - `ezkey-core/src/main/java/org/ezkey/audit/integrity/AuditLifecycleService.java`
  - OpenAPI spec / Postman collections (no contract change)

---

## Verification

1. `cd ezkey-admin-ui && pnpm lint && pnpm typecheck && pnpm test` (unit-level
   guard).
2. Manual exploratory pass (clean-start baseline, demo-mode themes/presets) —
   user-led:
   - **Cold-handover persona**: open the Audit Logs section, expand
     integrity → confirm the gaps list appears without any extra click,
     using the 7-day default window.
   - **Empty-state**: on a clean stack with no gaps, confirm the explicit
     *"No undeclared gaps detected"* block renders.
   - **Locate in timeline**: pick a gap → confirm visual anchor in the
     timeline; the same button now reads *Clear focus* and toggles the
     focus off when re-clicked.
   - **Declare**: confirm the dialog opens prefilled (read-only period
     matches the row), justification empty, submit disabled until ≥10
     chars; submit → confirm success state with the new `gapCheckpointId`,
     dialog closes, the declared gap disappears from the list, and the
     timeline reflects the new `GAP_DECLARATION` checkpoint with re-chained
     downstream checkpoints.
   - **Multi-event case**: declare a single detected gap with a
     justification mentioning *"crash followed by planned maintenance"* →
     confirm it's accepted as one declaration.
   - **Race / 409 case** (if reproducible): declare a gap that was filled
     between render and submit → confirm the inline 409 surfaces in the
     dialog with a refresh-list CTA rather than a generic toast.
3. After confirming the UI flow:
   `mvn spotless:apply && mvn checkstyle:check && mvn clean install -DskipTests`
   (only relevant if any backend/test changes sneak in; expected to be a
   no-op for this UI-only plan).

### Future testing strategy (out of scope here)

- No Playwright scenario currently covers the audit-logs gap declaration
  flow. Add one in a follow-up dedicated to test coverage:
  - seed a synthetic undeclared gap via a fixture (or via the `clean-start`
    + downtime simulation pattern),
  - assert *Locate in timeline* paginates to the focused gap and highlights
    bordering checkpoints,
  - assert *Declare* prefills the dialog, accepts a justification, and the
    declared gap disappears from the list with the timeline updated.
- Track this alongside the Phase 2 backend slices so the test asserts the
  final endpoint shape, not the transitional one.

---

## Decisions

- **Anchor checkpoint mode removed from UI; kept on backend.** UI sends
  timestamp mode using values straight from the detected gap.
- **Top-level "Declare gap" button removed.** Declaration is reachable only
  from a detected gap row → enforces the invariant that declarations match
  detected holes.
- **"Locate in timeline" kept as a secondary action.** Justified by the
  cold-handover persona; exceptional flows benefit from extra guidance.
- **Auto-run chain check on integrity section expand.** UX shortcut for the
  operator already on the page; not the discoverability mitigation —
  discoverability is owned by the backend scheduler check today (audit-based)
  and by the `ezkey_alert` table tomorrow (Phase 2). Debounced/cached to
  avoid noisy re-runs.
- **Multi-event windows handled by free-text justification.** No need for a
  multi-row justification structure — keeps the contract and UI simple.
- **No backend / OpenAPI / Postman contract change** — only docs refresh.

---

## Further considerations

1. **Should we hide the manual "Run integrity check" button once auto-run is
   in place?** Recommend keeping it as an explicit "Re-run" affordance
   (operator may want to re-verify after declaring); just relabel.
2. **Should the `Declare` action require the gap to be currently focused?**
   Recommend **no** — adds a forced second click for the common case where
   the operator already knows what happened. Keep the actions independent.
3. **What if a detected gap disappears between list render and submit
   (scheduler races to fill it, or another admin declared it)?** Backend
   already returns 409. Surface that error inline in the dialog with a
   "refresh list" CTA rather than a generic toast.
4. **Adjacent gaps in the list — confirmation of reasoning.** Two undeclared
   gaps appearing as separate rows imply at least one well-formed checkpoint
   exists *between* them; otherwise gap detection would (and should) merge
   them into a single contiguous gap. This means the order in which the
   operator declares multiple gaps in the same session does not create
   adjacency conflicts: each declaration only re-chains checkpoints strictly
   after its own `gapEnd`. **Implication**: no operator-facing warning about
   declaration order is needed. **Verification owed**: a quick read of the
   gap-detection logic in `AuditLifecycleService` / chain-verification
   confirms it never reports two adjacent (touching) gaps as distinct rows.
   If that guarantee turns out to be missing, fix it in the backend rather
   than papering over it in the UI.
