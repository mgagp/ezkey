# Plan: API Keys Screen UX Enhancement

The current API Keys page is a flat, non-clickable list with 8 columns, no sorting, no detail view, and no update capability — yet the Admin API already supports `GET /{id}`, `PATCH /{id}` (description + IP whitelist), and `DELETE /{id}?reason=`. The goal is to bring the API Keys screen to parity with the established detail-page pattern (used by Integrations, Enrollments, Tenants), cover all backend operations, and optimize the list columns for operator needs — all while staying simple and pragmatic.

**Two user profiles drive column/feature choices:**
- **Global Admin (IT ops):** needs cross-tenant visibility, stale-key detection, security posture (IP restrictions, expiration)
- **Tenant Admin (domain user):** needs per-integration key management, quick status check, self-service create/edit/revoke

## Steps

### 1. Optimize list columns in `api-keys.tsx`

**Remove:**
- **ID** column — internal numeric ID has no operational value in the list; visible on detail page
- **Key** column (`integrationKey` truncated to 22 chars) — truncated key is visual noise, not copy-friendly; move to detail page

**Keep (reorder for scannability):**
| # | Column | Why |
|---|--------|-----|
| 1 | **Integration** | Primary grouping context for both admin types |
| 2 | **Description** | Identifies *what service* uses this key |
| 3 | **Status** | Quick health check (Active / Revoked / Expired / Expiring Soon) |
| 4 | **Created** | Key age — critical for rotation awareness (use `createdAt` field, currently not shown) |
| 5 | **Expires** | When rotation is forced |
| 6 | **Last Used** | Stale-key detection — unused keys = security risk |

**Add:**
- **IP lock icon** — a small `Shield` icon (or similar) in the Status or as a discrete column if `ipWhitelist` is non-empty, so admins see at a glance which keys are IP-restricted
- **"Expiring Soon" badge variant** — if `expiresAt` is within 7 days, render as `<Badge variant="warning">Expiring Soon</Badge>` instead of plain `Active`

**Move inline Revoke button** to a small icon-only action (or keep as-is but as an icon button to save horizontal space), since the primary revoke flow will be on the detail page.

### 2. Enable sorting on the DataTable

The `DataTable` component already supports `sortKey`, `currentSort`, and `onSort` props. Add `sortKey` to columns and wire up local sort state in `ApiKeysPage`. Priority sortable columns: **Status**, **Created**, **Expires**, **Last Used**. Default sort: Active keys first, then by `createdAt` descending.

### 3. Add status filter

Add a second `<Select>` filter alongside the existing Integration filter, allowing filtering by status: **All**, **Active**, **Revoked**, **Expired**. This is client-side filtering (data is already in memory, not paginated). Helps Global Admins quickly find revoked/expired keys for cleanup.

### 4. Add detail page route and component

**Route:** Add `/api-keys/:id` in `routes.tsx`, following the exact pattern of `/integrations/:id` (lazy-loaded, `ProtectedRoute`-wrapped).

**New file:** `src/pages/api-key-detail.tsx`

**Data fetch:** `useParams()` → `useQuery` calling `GET /api/v1/api-keys/{keyId}` — same pattern as `integration-detail.tsx`.

**Layout (Card + InfoRow pattern):**

**Left card (2/3 width):** "API Key Details"
| Row | Content |
|-----|---------|
| ID | Numeric API key ID |
| Integration | Name (linked to `/integrations/:integrationId`) |
| Integration Key | Full `ezkey_ikey_...` with Copy button (mono font) |
| Description | Text (editable context) |
| Status | Badge (Active / Revoked / Expired) |
| Created | Formatted timestamp |
| Expires | Formatted timestamp or "Never" |
| Last Used | Formatted timestamp or "Never" |
| IP Whitelist | List of IPs/CIDRs or "No restrictions" |

**Right card (1/3 width):** "Actions"
- **Edit** button → opens `EditApiKeyDialog` (see step 5)
- **Revoke** button (destructive, only if active) → opens enhanced `RevokeDialog`

If key is revoked, show a **Revocation Info** section:
- Revoked At: timestamp
- Revoked By: `revokedByUsername`

**Breadcrumb:** `API Keys → Key #42`

### 5. Add Edit (PATCH) dialog

**New component:** `EditApiKeyDialog` in the detail page file.

**Editable fields** (per `ApiKeyUpdateRequestDto`):
- **Description** — text input, max 255 chars
- **IP Whitelist** — textarea, one IP/CIDR per line (null = ignore, empty = remove restrictions)

**Sends:** `PATCH /api/v1/api-keys/{keyId}` with `version` field for optimistic locking. On 409 Conflict, show "This key was modified by another user. Please refresh and try again."

**Form:** React Hook Form + Zod, pre-filled with current values. Follows the same dialog pattern as `CreateApiKeyDialog`.

### 6. Enhance Revoke dialog with reason

Modify the existing `RevokeDialog` in `api-keys.tsx`:
- Add an optional **Reason** text input (min 10 chars when provided, per API constraint)
- Pass as `?reason=encodeURIComponent(reason)` query param on `DELETE`
- This dialog will be usable from both the list (quick action) and the detail page

### 7. Make list rows clickable

Wire up `onRowClick` on the `DataTable` in `api-keys.tsx`:
```tsx
onRowClick={(row) => navigate(`/api-keys/${row.apiKeyId}`)}
```
Following the exact pattern used by `integrations.tsx` and `enrollments.tsx`.

### 8. Enhance footer count with status breakdown

Replace the simple count with a status-aware summary:
`12 keys · 8 active · 2 expired · 2 revoked`

This gives operators instant insight into the health of their API key estate without needing to filter.

## Verification

- Manual test: Create a key, verify list shows optimized columns with sorting
- Manual test: Click a row → navigates to detail page with all fields
- Manual test: Edit description + IP whitelist → PATCH succeeds, detail page updates
- Manual test: Revoke with reason → key status changes, reason persisted
- Manual test: Global Admin sees all keys; Tenant Admin sees only their tenant's keys
- Manual test: "Expiring Soon" badge appears for keys expiring within 7 days
- Manual test: Status filter works (Active/Revoked/Expired)
- Manual test: Sorting works on all sortable columns
- Verify: No TypeScript errors (`npm run typecheck`)
- Verify: Follows neo-brutalism theme conventions from `AGENTS.md`

## Decisions

- **Detail page over expandable rows:** Consistent with Integrations/Enrollments/Tenants pattern; DataTable doesn't support expansion; avoids new component work
- **ID and Key columns removed from list:** Operators identify keys by Integration + Description; raw key prefix is noise at list level; both remain accessible on detail page
- **Client-side sorting & status filter:** Data is not paginated (plain array); no need for server-side sort/filter complexity
- **Edit limited to description + IP whitelist:** This is exactly what the PATCH endpoint supports; expiration and integration are immutable by design (create new key for changes)
- **Reason field optional on revoke:** API accepts it as optional (but enforces min 10 chars if provided); keeps quick-revoke frictionless while enabling audit trail
