## Plan: Audit Logs Date Presets & Backend Date Filtering

**TL;DR** — The audit logs main filter bar has From/To date inputs that are **silently broken** (the backend ignores `createdAfter`/`createdBefore`). This plan adds a time-period preset dropdown to the main filter bar (reusing the exact pattern already working in the `IntegrityPanel` within the same file), and fixes the backend to actually support date filtering — mirroring the proven pattern from `AuthAttemptController` / `AuthAttemptService`.

**Steps**

### Backend — Fix date filtering support

1. **Add `createdAfter` / `createdBefore` parameters to `AuditLogController.getAuditLogs()`**
   in [AuditLogController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java#L182-L206): add two `@RequestParam(required = false) OffsetDateTime` parameters, following the exact pattern used in [AuthAttemptController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuthAttemptController.java#L209-L214). Pass them through to the service call at [line 220](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java#L220).

2. **Add date predicates to `AuditLogService.findByFilters()`**
   in [AuditLogService.java](ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java#L160-L218): add `OffsetDateTime createdAfter` and `OffsetDateTime createdBefore` parameters to the method signature, and add two JPA `Predicate` blocks inside the specification lambda — identical to [AuthAttemptService.java lines 263-268](ezkey-core/src/main/java/org/ezkey/authattempt/service/AuthAttemptService.java#L263-L268):
   ```
   if (createdAfter != null) → cb.greaterThanOrEqualTo(root.get("createdAt"), createdAfter)
   if (createdBefore != null) → cb.lessThanOrEqualTo(root.get("createdAt"), createdBefore)
   ```

3. **Update Javadoc** on both the controller method and service method to document the new `@param` entries.

### Frontend — Add preset dropdown to the main filter bar

4. **Extract `applyPreset` into a reusable utility or duplicate it locally.**
   The function already exists at [audit-logs.tsx lines 138-196](ezkey-admin-ui/src/pages/audit-logs.tsx#L138-L196) inside `IntegrityPanel`. Two options:
   - **(a) Extract** to a shared helper (e.g., `src/lib/date-presets.ts`) returning `{ from: string, to: string }` — cleaner, DRY.
   - **(b) Duplicate** the `applyPreset` logic inside `AuditLogsPage` — simpler, self-contained.
   
   **Decision: option (a)** — extract to a shared utility, since the same pattern could later benefit `auth-attempts.tsx` which also has bare From/To inputs.

5. **Add `preset` state and the preset `<Select>` to the main filter bar** in [audit-logs.tsx around line 680](ezkey-admin-ui/src/pages/audit-logs.tsx#L680-L700):
   - Add `const [preset, setPreset] = useState('')` alongside existing `dateFrom` / `dateTo` state.
   - Insert a `<Select>` dropdown **before** the From/To inputs, with the same options as `IntegrityPanel`: Full range, Today, Yesterday, Last 24 hours, Last 7 days, Last 30 days, Last week (Mon–Sun), Last month, Last quarter.
   - On preset change: call the extracted utility, set `dateFrom` / `dateTo` accordingly.
   - On manual From/To change: clear the preset (`setPreset('')`) — already the pattern used in `IntegrityPanel` at [line 371](ezkey-admin-ui/src/pages/audit-logs.tsx#L371).
   - Add a "Clear" link when dates are set (same pattern as [IntegrityPanel lines 375-382](ezkey-admin-ui/src/pages/audit-logs.tsx#L375-L382)).

6. **Refactor `IntegrityPanel`** to use the same extracted utility instead of its inline `applyPreset`, keeping behavior identical.

### Files touched

| File | Change |
|---|---|
| [AuditLogController.java](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/AuditLogController.java) | Add `createdAfter`, `createdBefore` params + pass to service |
| [AuditLogService.java](ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java) | Add `createdAfter`, `createdBefore` params + date predicates |
| [audit-logs.tsx](ezkey-admin-ui/src/pages/audit-logs.tsx) | Add preset dropdown to main filter bar, refactor `IntegrityPanel` |
| **NEW** `src/lib/date-presets.ts` | Shared `computeDateRange(preset): { from, to }` utility |

### Verification

- **Backend unit test**: update or add test for `AuditLogService.findByFilters()` verifying `createdAfter`/`createdBefore` predicates produce correct results.
- **Backend integration test**: call `GET /api/v1/audit-logs?createdAfter=...&createdBefore=...` and verify filtered results.
- **Manual UI test**: open Audit Logs, select "Last 7 days" → verify From/To inputs auto-populate with correct dates → verify the table shows only recent entries. Manually change From → verify preset clears to blank. Click "Clear" → verify both dates reset.
- **Build**: `mvn clean verify` for backend; `npm run build` for admin-ui.

### Decisions

- Extract `applyPreset` to shared utility rather than duplicating — enables reuse in `auth-attempts.tsx` later.
- Backend fix is **mandatory** — without it, date filtering (preset or manual) remains silently broken.
- Preset list matches the `IntegrityPanel`'s existing list for consistency across the same page.
