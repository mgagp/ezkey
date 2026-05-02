---
name: audit log timestamps
overview: Adjust the Admin UI audit log list so operational investigations can correlate entries to exact incident timestamps without mental conversion from relative time.
todos:
  - id: update-audit-time-column
    content: Render audit log list timestamps as absolute timezone-explicit values, with optional relative context.
    status: completed
  - id: align-detail-created-time
    content: Make the audit log detail dialog use the same timezone-explicit timestamp format.
    status: completed
  - id: update-i18n-label-if-needed
    content: If adopting clearer wording, update EN/FR audit log column labels consistently.
    status: completed
  - id: validate-ui
    content: Run Admin UI validation and perform a focused manual check of the audit log investigation view.
    status: completed
isProject: false
---

> **Plan status:** **Completed** — The audit log list now prioritizes absolute, timezone-explicit timestamps for operational investigation, keeps relative age as secondary context, aligns the detail dialog timestamp display, and updates the EN/FR column label to `Timestamp` / `Horodatage`.

# Audit Log Timestamp Correction

## Finding

The current audit log list renders the `createdAt` column as relative time, which is poor for incident correlation. Operators investigating an incident at an exact time such as 08:20 need an absolute timestamp first, especially when comparing against operational incidents, backend logs, database records, or external monitoring.

Relevant current implementation:

- [`ezkey-admin-ui/src/pages/audit-logs.tsx`](ezkey-admin-ui/src/pages/audit-logs.tsx) renders the list `Time` column with `formatRelativeTime(r.createdAt ?? '')`.
- [`ezkey-admin-ui/src/lib/utils.ts`](ezkey-admin-ui/src/lib/utils.ts) already provides `formatDateWithTimezone()`, including an explicit timezone label for audit/chain contexts.
- [`docs/ENDPOINT.md`](docs/ENDPOINT.md) states API timestamps are UTC and client display is responsible for timezone conversion.

## Decision

Use an absolute, timezone-explicit timestamp as the primary audit log list value. Keep relative time only as secondary context, if useful, via muted text or tooltip.

Recommended display pattern:

- Primary visible value: `formatDateWithTimezone(createdAt)`.
- Optional secondary value: relative age in muted text, for quick recency scanning.
- Empty value: `-`.
- Keep `sortKey: 'createdAt'` unchanged so sorting remains server-side and globally correct.

This aligns the audit log list with the existing integrity and incident panels, which already use `formatDateWithTimezone()` for operational timestamps.

## Implementation Scope

Change only the Admin UI audit log presentation layer:

- Update the audit logs list column in [`ezkey-admin-ui/src/pages/audit-logs.tsx`](ezkey-admin-ui/src/pages/audit-logs.tsx).
- Consider updating the detail dialog created timestamp in the same file from `formatDate()` to `formatDateWithTimezone()` for consistency.
- Optionally adjust the column label from `Time` / `Heure` to `Timestamp` / `Horodatage` if we want the UI language to make the absolute nature explicit.
- Do not change backend API contracts, generated OpenAPI specs, or pagination behavior.

## Validation

- Run the Admin UI type/lint validation used by the project.
- Browser test judgment: this is a low-risk presentation change, but it affects an investigation workflow. Existing broad Playwright coverage is likely not worth extending; a quick manual browser check on `/audit-logs` is proportionate.
- Manual check: create or load audit events, verify the list shows exact local or tenant-timezone timestamps, verify sorting by the timestamp column still works, and verify the incident timestamps and audit log timestamps can be compared directly.
