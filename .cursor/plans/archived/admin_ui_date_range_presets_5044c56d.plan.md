---
name: Admin UI date range presets
overview: Unify temporal filtering in the admin UI by introducing shared named date-range presets (Today, Yesterday, Last 7 days, etc.) resolved purely in the UI and sent to the existing API as createdAfter/createdBefore. No backend changes; reuse and generalize the preset logic already present in the Audit Logs integrity section.
todos: []
isProject: false
status: completed
completedAt: "2026-03-08"
---

# Admin UI — Uniform date range presets

**Status: Completed** — Implemented shared date-range presets, `DateRangeFilter` component (Auth Attempts, Audit Logs list + integrity section), and backend fix for audit-logs `createdAfter`/`createdBefore` filtering.

## Recommendation: UI-only presets (no backend keywords)

**Keep the API as-is.** Send only concrete ISO-8601 timestamps (`createdAfter` / `createdBefore`). Resolve named periods (e.g. "Last week") in the UI and pass the resulting dates to the existing parameters.

**Why this is the right approach:**

- **Common practice:** Admin and audit UIs (e.g. GCP, AWS, Datadog, Grafana) typically offer quick ranges in the UI and still call APIs with explicit from/to timestamps. The API stays simple, testable, and language-agnostic.
- **No accidental complexity:** No new API contract, no keyword parsing or i18n on the backend, no timezone interpretation on the server.
- **Flexibility:** Different clients can offer different presets; the API does not need to know about "last week" or locale.

So: **presets are a presentation concern.** When the user selects "Last week", the UI computes the corresponding start/end in the user's local context and sends those as `createdAfter` / `createdBefore`.

---

## Current state (inventory)


| Location                                                              | Purpose                        | From/To state                                          | Presets today                             |
| --------------------------------------------------------------------- | ------------------------------ | ------------------------------------------------------ | ----------------------------------------- |
| [auth-attempts.tsx](ezkey-admin-ui/src/pages/auth-attempts.tsx)       | List filter                    | `dateFrom`, `dateTo` → `createdAfter`, `createdBefore` | **None**                                  |
| [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx) (list)      | List filter                    | `dateFrom`, `dateTo` → same                            | **None**                                  |
| [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx) (integrity) | Chain / Entry integrity checks | `checkFrom`, `checkTo` → `from`, `to` query params     | **Yes** (inline `applyPreset`, ~80 lines) |
| Integrations list                                                     | —                              | API supports `createdAfter`/`createdBefore`            | No date filter in UI                      |
| Enrollments list                                                      | —                              | API supports same                                      | No date filter in UI                      |


So today:

- **Two list views** (Auth Attempts, Audit Logs) have From/To date inputs but **no presets**.
- **One section** (Audit Logs → Chain/Integrity) already has a full preset dropdown (Today, Yesterday, Last 7d, Last 30d, Last week, Last month, Last quarter) implemented **inline** in the page.

---

## Target UX (admin daily use)

Presets should match what an admin (global or tenant) typically needs for **audit and auth-attempt review**:

- **Today** — current day (start of day → end of day, local).
- **Yesterday** — previous calendar day.
- **Last 7 days** — **rolling**: from (now − 7 days) to now. This is the chosen semantics and matches common admin expectations (e.g. "what happened in the last 7 days").
- **Last 30 days** — **rolling**: from (now − 30 days) to now. Same convention.
- **Last week (Mon–Sun)** — previous Monday to Sunday (already in audit-logs).
- **Last month** — previous calendar month.
- **Last quarter** — previous quarter (already in audit-logs).
- **Clear / Full range** — no date filter.

**Out of scope for this plan:** "Last 24 hours" (rolling) is not required and is left out.

No new dependency: keep using the native `Date` and the existing stack (Vite, React, Tailwind). No need for a date library unless we later hit limits (e.g. timezone edge cases).

---

## Implementation direction

### 1. Shared preset logic (single source of truth)

- **Add** a small module, e.g. `src/lib/date-range-presets.ts` (or under `src/components/` if you prefer), that:
  - Defines a **list of preset options** (id + label): e.g. `today`, `yesterday`, `last-7d`, `last-30d`, `last-week`, `last-month`, `last-quarter`, plus a "clear" option.
  - Exposes a function `**getPresetDateRange(presetId: string): { from: string; to: string } | null`** that returns `YYYY-MM-DD` for `from` and `to` (or `null` for "no range"), using **local** calendar semantics (e.g. "today" = local start/end of day when later converted to ISO).
  - Optionally: `**dateRangeToApiParams(from: string, to: string): { createdAfter: string; createdBefore: string }`** that converts two `YYYY-MM-DD` strings to ISO for the API (start-of-day and end-of-day in **local** timezone, then to ISO), so timezone behavior is consistent and documented in one place.
- **Refactor** the existing `applyPreset` in [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx) (lines 136–211) to call this shared logic and only set `checkFrom` / `checkTo` from the returned `from`/`to` (and set preset to empty when user manually changes the date inputs).

### 2. Reusable UI block (recommended — do it)

- **Add** a small controlled component, e.g. `DateRangeFilter` (or a name that fits the project's component convention), that:
  - Renders a **preset dropdown** (same options as above) plus **From** and **To** date inputs.
  - Accepts `value: { from: string; to: string }` and `onChange(value)` (or separate `dateFrom`/`dateTo` and setters).
  - When the user selects a preset, computes `from`/`to` via `getPresetDateRange`, updates internal state, and calls `onChange`. When the user edits From/To manually, clears the preset selection and calls `onChange`.
  - Optionally includes a "Clear" link when `from` or `to` is set.
- Use this component in:
  - **Auth Attempts** list filter (replace the two raw date inputs).
  - **Audit Logs** list filter (same).
  - **Audit Logs** Chain/Integrity section (replace the current preset Select + two inputs by this component, so the three places share one UX and one preset list).

### 3. Consistency and timezone

- **Sending to the API (request):** The shared helper must build `createdAfter` / `createdBefore` from the user's chosen range using **local** start-of-day and end-of-day, then convert to ISO. That way "Today" and "Yesterday" match the admin's calendar in their timezone. Centralize this in `dateRangeToApiParams` (or equivalent) so all callers behave the same.
- **Display (response):** The API returns timestamps in UTC (per [ENDPOINT.md](docs/ENDPOINT.md)). The plan **documents** that the UI will render these in the **user's local timezone** so the experience is consistent with the admin's local clock. Existing formatting (e.g. `formatDate`, `formatRelativeTime` in [utils.ts](ezkey-admin-ui/src/lib/utils.ts)) already uses the browser's locale; the implementation will keep that behaviour and ensure any new date display uses the same approach (local rendering of UTC data).

### 4. Scope boundaries (what not to do)

- **Backend:** No new query parameters, no keywords like `period=last_week`. No changes to Admin API specs or Java controllers.
- **Enrollments / Integrations lists:** The API already supports `createdAfter` / `createdBefore`; the UI does not currently expose date filters there. Adding the same date-range filter + presets to those lists is **optional** for this plan; if you do it, use the same shared preset module and component for uniformity.
- **Seal archive / Gap declaration** (audit-logs): Those forms use their own date fields for lifecycle operations; they are not "list filters" and do not need the same preset UX unless you explicitly want it later.

---

## Angles to watch

1. **Timezone:** Centralize "date string → API ISO" in one place (local start/end of day → ISO). Render API timestamps in the user's local timezone so the experience matches their clock (see §3 above).
2. **Last 7 / 30 days:** Use **rolling** semantics: "Last 7 days" = from (now − 7 days) to now; "Last 30 days" = from (now − 30 days) to now. Document this in code; it matches common admin expectations.
3. **i18n:** No internationalization in this plan; UI copy remains hand-crafted English. i18n is out of scope and may be a separate piece of work later.
4. **Existing integrity presets:** The Audit Logs integrity section already has a working preset list; the refactor should preserve that behaviour and move it into the shared module + component so the main list filters reuse it. Remove the "Last 24h" option when unifying on the shared preset list.

---

## Summary

- **Approach:** UI-only presets; API unchanged (`createdAfter` / `createdBefore`).
- **Unify:** One shared preset definition and date computation, and a **reusable `DateRangeFilter` component**, used by Auth Attempts list, Audit Logs list, and Audit Logs Chain/Integrity.
- **UX:** Same preset set everywhere (Today, Yesterday, Last 7/30 days rolling, Last week, Last month, Last quarter, Clear). No "Last 24 hours".
- **Quality:** Document and implement local-timezone handling: (1) when building API params, use local start/end of day then ISO; (2) when displaying API timestamps, render in the user's local timezone so the experience matches their clock. No i18n in this plan; English only.
