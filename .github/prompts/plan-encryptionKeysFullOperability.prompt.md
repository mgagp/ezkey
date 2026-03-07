# Plan: Encryption Keys Screen — Full Operability Enhancement

**TL;DR** — The encryption keys screen in `encryption-keys.tsx` already covers 5 of 9 controller endpoints. Four API operations are missing from the UI: per-key re-encryption, create-batches-only, get-key-by-id (for fresh detail), and get-primary (visual emphasis). The plan enriches the existing screen to expose all controller capabilities with a coherent, operable UX — matching patterns from `api-keys.tsx` and `integrations.tsx` — without adding unnecessary complexity.

## Steps

### 1. Add per-key "Re-encrypt" action in the keys table

In the keys table (`columns` array, ~line 280), add an **Actions column** at the end — consistent with the action column pattern in `api-keys.tsx`. For non-PRIMARY keys with status ENABLED, show a "Re-encrypt" button (small, secondary variant, with `RotateCcw` icon). Clicking it opens a confirmation dialog. This calls `POST /api/v1/encryption-keys/{keyId}/reencrypt` and uses the generated `ReencryptionKeyResponse` type. The dialog should show a warning (same style as `RotateKeyDialog`), confirm action, and display results (batches created/processed/failed) on success.

### 2. Add "Create Batches" button in the Re-encryption Batches section

In `ReencryptionBatchesSection` (~line 215), next to the existing "Trigger Full Re-encryption" button, add a secondary **"Create Batches"** button. This calls `POST /api/v1/encryption-keys/reencrypt/create-batches` and uses the generated `BatchCreationResponse` type. This is an intentionally lower-impact action (creates batches without processing them), so use `variant="secondary"` with a distinct icon (e.g., `Layers` or `ListPlus` from lucide-react). Show toast on success with the `batchesCreated` count and `message`.

### 3. Add batch detail dialog (row click on batches)

The batch table currently has no `onRowClick`. Add a `BatchDetailDialog` component (following the exact pattern of `KeyDetailDialog`) that shows all batch fields including those currently hidden from the table: `errorMessage`, `startedAt`, `completedAt`, `recordsSkipped`, `retryCount`. Use the same `Row` label/value layout as `KeyDetailDialog`. Add `onRowClick` to the batch `DataTable` to open this dialog. This gives full visibility into batch state for debugging failed batches — without cluttering the table itself.

### 4. Fetch fresh key data in KeyDetailDialog via `GET /{keyId}`

Currently `KeyDetailDialog` displays data from the list row (stale by the time the user clicks). Enhance it to call `GET /api/v1/encryption-keys/{keyId}` on open using `useQuery` with `enabled: !!keyData?.keyId`. Show a subtle loading indicator while fetching, then display the fresh data. This ensures `recordsEncrypted`, `recordsReencrypted`, and `notes` are always up-to-date.

### 5. Visual emphasis on the PRIMARY key row

Add a subtle visual distinction for the PRIMARY key row in the table. Options (consistent with neo-brutalism style):
- Add a small `Key` icon (size-3, accent color) inline with the ID in the first column for the PRIMARY row
- Or apply a left-border accent (`border-l-4 border-accent`) via `DataTable`'s `rowClassName` prop (if supported) or via a conditional class in the render

Check if `DataTable` supports `rowClassName`. If not, the inline icon approach is simpler and doesn't require `DataTable` modifications.

### 6. Add "Re-encrypt" button inside KeyDetailDialog for non-PRIMARY keys

In `KeyDetailDialog`, if the key status is not PRIMARY, add a "Re-encrypt this key" button at the bottom (next to "Close"). This provides an alternative entry point for per-key re-encryption from the detail view. Reuse the same confirmation dialog/mutation from Step 1.

### 7. Import missing generated types

Add imports for `ReencryptionKeyResponse` and `BatchCreationResponse` from `@/generated/admin-api/model` in the imports section at the top of the file.

### 8. Add batch status filter (lightweight)

In the `ReencryptionBatchesSection`, add a simple `Select` dropdown to filter batches by status (All / PENDING / IN_PROGRESS / COMPLETED / FAILED). Apply client-side filtering since the list is not paginated. This helps quickly find actionable (failed/pending) batches in a long list.

## API Coverage Matrix

| # | Endpoint | Method | Currently in UI | After Enhancement |
|---|----------|--------|:---:|:---:|
| 1 | `/api/v1/encryption-keys` | GET | Yes | Yes |
| 2 | `/api/v1/encryption-keys/primary` | GET | No | Implicit (visual emphasis) |
| 3 | `/api/v1/encryption-keys/{keyId}` | GET | No | Yes (fresh detail fetch) |
| 4 | `/api/v1/encryption-keys/rotate` | POST | Yes | Yes |
| 5 | `/api/v1/encryption-keys/reencryption-batches` | GET | Yes | Yes |
| 6 | `/api/v1/encryption-keys/reencryption-batches/{batchId}/resume` | POST | Yes | Yes |
| 7 | `/api/v1/encryption-keys/reencrypt/trigger` | POST | Yes | Yes |
| 8 | `/api/v1/encryption-keys/{keyId}/reencrypt` | POST | No | Yes (table + detail) |
| 9 | `/api/v1/encryption-keys/reencrypt/create-batches` | POST | No | Yes |

## Verification

- All 9 controller endpoints should be reachable from the UI
- Test key detail dialog fetches fresh data on open (check network tab)
- Test per-key re-encrypt action on a non-PRIMARY ENABLED key
- Test "Create Batches" button creates batches without processing
- Test batch row click opens detail dialog with full info
- Test batch status filter narrows the list correctly
- Confirm no TypeScript errors: `npx tsc --noEmit`
- Visual check: PRIMARY row has visual emphasis, neo-brutalism style consistency maintained

## Design Decisions

- Per-key re-encrypt is exposed both as a table inline action *and* inside the detail dialog — two natural entry points, no extra complexity
- "Create Batches" is a secondary button next to "Trigger Full Re-encryption" — keeps batch operations grouped, visually subordinate to the destructive trigger action
- Batch detail is a click-to-open dialog (not expandable row) — consistent with `KeyDetailDialog` pattern and simpler to implement
- `GET /primary` endpoint is NOT given a separate UI action — the primary is already highlighted in the list and the detail dialog shows primary status. No need for a dedicated "Show Primary" button.
