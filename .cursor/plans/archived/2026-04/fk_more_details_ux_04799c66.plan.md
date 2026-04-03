---
name: FK More Details UX
overview: Confirm and document current "More details" behavior (TanStack Query cache reuse), address QA confusion with clearer affordances (tooltips, optional post-load button state), and define a reusable, non-duplicated pattern for cross-cutting contextual help aligned with the existing Help drawer.
todos:
  - id: copy-tooltips
    content: Add EN/FR keys for tooltip (and optional post-load label); wire title/aria on all Related details buttons or extract RelatedDetailsButton
    status: completed
  - id: loaded-state
    content: Implement post-expand UX (label change, disabled, or icon) when isExpanded && !isLoading
    status: completed
  - id: help-pattern
    content: Add single help.json pattern block (patterns.fkRelatedDetails) and decide tooltip-only vs extend HelpContext with topicOverride + new HelpTopicId
    status: completed
  - id: qa-matrix
    content: Verify cold vs warm cache, multiple panels same entity, accessibility
    status: completed
isProject: false
---

# Admin UI: "More details" (FK panels) — UX clarity and reusable help

## 1. Confirmed behavior (matches your description)

**Hook:** `[ezkey-admin-ui/src/hooks/use-expandable-related-details.ts](ezkey-admin-ui/src/hooks/use-expandable-related-details.ts)`

- On first click, `expand()` sets `isExpanded` to `true`. Related **GET** queries (tenant, integration, enrollment, admin — whichever IDs are present) are **enabled only after** that click, avoiding extra API calls on initial load.
- Queries use **Orval-generated keys** (`getGetTenantQueryKey`, `getGetByIdQueryKey`, etc.), i.e. the **same cache entries** as anywhere else in the app that fetches those entities.
- **TanStack Query** (`[query-client.ts](ezkey-admin-ui/src/lib/query-client.ts)`: `staleTime` 30s) deduplicates and reuses data. If the user already visited a tenant/integration/etc. detail, the next "More details" for the same ID can resolve **immediately** with `isLoading === false` — no spinner, no visible network delay.

**Button label today:** On every screen that uses this hook (`[enrollment-detail.tsx](ezkey-admin-ui/src/pages/enrollment-detail.tsx)`, `[integration-detail.tsx](ezkey-admin-ui/src/pages/integration-detail.tsx)`, `[api-key-detail.tsx](ezkey-admin-ui/src/pages/api-key-detail.tsx)`, `[auth-attempts.tsx](ezkey-admin-ui/src/pages/auth-attempts.tsx)`, `[admins.tsx](ezkey-admin-ui/src/pages/admins.tsx)`, `[audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx)`), the pattern is the same:

- While loading: show `common:buttons.loading`.
- Otherwise: always show `common:detail.moreDetails` ("More details" / "Plus de détails") — **even after** expansion and successful load.

The button is `disabled` only when `isExpanded && isLoading`, so after a **cache hit** it stays **enabled**; repeated clicks call `expand()` again (no-op). That matches QA’s observation: **no obvious "new" effect** when data was already cached — the enrichment is there, but the control does not communicate "done" or "already applied."

```mermaid
flowchart LR
  click[User clicks More details]
  expand[isExpanded true]
  q[Queries enabled]
  cache{TanStack cache has data?}
  instant[No spinner isLoading false]
  fetch[Network fetch then data]
  ui[Inline rows and links updated]
  click --> expand --> q --> cache
  cache -->|yes| instant --> ui
  cache -->|no| fetch --> ui
```



**Strings:** Labels live under `[ezkey-admin-ui/src/locales/en/common.json](ezkey-admin-ui/src/locales/en/common.json)` / `fr` — `detail.moreDetails`, `detail.relatedContext`, etc.

---

## 2. Analysis: "Less details" and collapse


| Option                                        | Pros                                                           | Cons                                                                                                                                                                                                                            |
| --------------------------------------------- | -------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **"Moins de détails" / collapse**             | Matches QA’s toggle mental model; clears visual clutter        | Requires new state (`isExpanded` becomes a **toggle** or separate `isCollapsed`), and every panel must hide the extra `InfoRow`s when collapsed. **Does not** "discard" the query cache (and that would be wrong to tie to UX). |
| **No collapse; clarify what the button does** | Small change; aligns copy with one-shot "load related records" | Does not satisfy users who want to hide rows again                                                                                                                                                                              |


**Recommendation:** Treat **collapse** as an optional **phase 2** only if operators ask for it. The confusion you care about is **discoverability and feedback**, not reverting cached data. **"Less details" is not needed to explain caching** — it is only meaningful if you **hide** the enriched rows again.

---

## 3. UX improvements (high value, low complexity)

**Minimum (strongly recommended):**

1. **Tooltip + accessible description** on the button (and keep label short): one new i18n key pair (EN + FR), e.g. `common:detail.moreDetailsTooltip`, explaining that the action **loads related records from the API** (names/links), and that **if data was already loaded elsewhere, the update can be immediate**. Avoid duplicating long text in 6 files: centralize in `common.json`, wire `title` + `aria-describedby` (or a visually hidden helper text pattern if you prefer screen-reader parity beyond `title`).
2. **Post-load affordance** so the control does not look "broken" when cache is warm:
  - **Option A:** When `isExpanded && !isLoading`, change label to something like **"Related details shown"** / **"Détails liés affichés"** and optionally `aria-disabled` or `disabled` with tooltip "Already loaded" (product decision: still allow click as no-op vs hide button).
  - **Option B:** Replace text with icon + short status (checkmark) when loaded — same idea, less wording.
3. **Optional micro-feedback on first expand:** e.g. very subtle focus scroll to the first new `InfoRow` (only if not already in view) — use only if it does not feel noisy; tooltips + label change may be enough.

**Implementation note:** Today the same button block is **repeated** across pages. A small shared component (e.g. `RelatedDetailsButton` wrapping `Button` + tooltip + label logic) would keep behavior and copy **DRY** and make future tweaks one place.

---

## 4. Contextual help: cross-cutting pattern without 10× duplication

**Current system:** `[ezkey-admin-ui/src/context/help-context.tsx](ezkey-admin-ui/src/context/help-context.tsx)` + `[help-topics.ts](ezkey-admin-ui/src/lib/help-topics.ts)` — `topicId` is derived **only from `location.pathname`** via `resolveHelpTopicId`. `[HelpDrawer](ezkey-admin-ui/src/components/help/help-drawer.tsx)` renders `help:topics.<topicId>.*`. There is **no** API today to open the drawer on a **named pattern** that is not the current route.

**Problem:** A full paragraph on "FK related details" belongs in **one** place in i18n, but should be reachable from **many** panels without copying paste into every route topic.

**Reasonable alternatives:**


| Approach                                                                                                                                                | Duplication                                                                      | Effort     | Fit                                                |
| ------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------- | ---------- | -------------------------------------------------- |
| **A. Tooltip-only**                                                                                                                                     | None (one `common` key)                                                          | Low        | Solves clarity; **no** full help article           |
| **B. `help.json` section `patterns.fkRelatedDetails`** (title + body)                                                                                   | Single block; referenced by tooltip "Learn more" **or** a shared small component | Low–medium | Keeps long copy out of `common`                    |
| **C. Extend `HelpContext`:** e.g. `openHelp({ topicOverride?: HelpTopicId })` (or `patternId`) so `HelpDrawer` uses override when set, cleared on close | None for copy                                                                    | Medium     | Best if you want **drawer** parity with other help |
| **D. Add `enrollment-detail`, `api-key-detail`, …** to `resolveHelpTopicId` and duplicate the same paragraph in each `topics.*.body`                    | High                                                                             | High       | **Not recommended**                                |


**Recommendation:**

- **Phase 1:** **A + B** — short tooltip from `common` (or a single `help:patterns.fkRelatedDetails.shortLine` if you want all long-form help under `help`), plus optional **one** `help.json` entry under a dedicated path like `patterns.fkRelatedDetails` with `title`, `summary`, `body` (FR+EN parity per `[AGENTS.md](ezkey-admin-ui/AGENTS.md)`).
- **Phase 2 (if "Learn more" in drawer is required):** **C** — add optional topic override to `openHelp` so a `RelatedDetailsHelpLink` (or `HelpInlineButton` variant) opens the drawer on `**fk-related-details`** (new `HelpTopicId`) without tying it to the route. Route-based `?` shortcut continues to use pathname resolution when no override is active.

**Panel-level `?` vs global help:** A **reusable** `HelpInlineButton` that opens **pattern** `fk-related-details` (once C exists) next to **one** representative card title is optional; many teams prefer **tooltip + one global help topic** over per-panel duplicate icons. Decision: **one pattern topic** + optional inline link where density allows (e.g. Enrollment Info header), not a separate help article per panel.

---

## 5. Deliverables checklist

- EN/FR strings: tooltip (required); optional longer `help` pattern block.
- Adjust button label or disabled state when `isExpanded && !isLoading` (product pick: A vs B above).
- Optional: extract `RelatedDetailsButton` to dedupe the 6 call sites.
- Optional: `HelpTopicId` + `help:topics.fk-related-details.`* + `openHelp` override — only if drawer-based explanation is in scope.
- QA: cold cache (spinner), warm cache (immediate, new label/state), no FK (button hidden / audit placeholder behavior unchanged).

