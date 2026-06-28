# Tracer Bullet Brief — `TB-2026-06-27` Encryption keys: async re-encryption triggers

## Metadata

- **ID:** `TB-2026-06-27-admin-ui-encryption-reencryption-async`
- **Status:** `done`
- **Related idea:** `I-2026-0002-reencryption-batch-async-button`
- **Parent context:** `I-2026-0028` P3 residual; matrix rows encryption keys / re-encryption batches (`implemented`)
- **Feature:** `F-encryption-key-rotation`
- **Lane:** `A` (API contract + Admin UI operational UX)
- **Posture:** `single-pass` with design pack gate
- **GitHub issue:** #264 (closed via PR #265)
- **Created at:** `2026-06-27`
- **Closed at:** `2026-06-28`

## Objective

Make **manual re-encryption triggers** non-blocking: HTTP returns immediately after enqueue;
operators observe progress via the **existing batches table** on `/encryption-keys`. Align all three
manual paths (per-key, full trigger, create batches + execute) on the same async contract.

## Current state (analysis)

### Already shipped

- **Admin UI** `encryption-keys.tsx` (~1.2k lines): paginated keys list (ID first, lifecycle, records),
  batch table (status, progress, resume), rotate key, detail dialog, reason fields on mutations.
- **Admin API** `EncryptionKeyController`: list keys, list batches, resume batch, rotate, manual triggers.
- **Grill done** (2026-05-19, blitz D1/D2): per-key trigger confirmed **synchronous** today.

### Gap (resolved 2026-06-28)

Manual trigger endpoints previously processed batches inline (`200 OK` + synchronous summary). **Fixed:**
enqueue-only + **202 Accepted**; UI fire-and-forget with batches table as progress surface.
`POST /reencrypt/create-batches` remains **200** (create-only, by design).

### Matrix posture

Rows **`implemented`** — async triggers + existing paginated keys/batches UI; operator column canon
still *TBD* for future editorial pass (non-blocking).

## In scope

1. **Admin API:** enqueue-only manual triggers; **`202 Accepted`** + minimal body (`message`, optional
   `batchIds` when cheap). Keep **4xx** for validation failures (no PRIMARY, pending key, etc.).
2. **Audit:** distinguish *trigger accepted* vs *batch completed* on manual path.
3. **Admin UI:** toast + invalidate batches/keys queries; dialogs close without waiting for batch
   completion; remove result grid that implies synchronous completion.
4. **OpenAPI** refresh + Orval regen (part of contract change).
5. **Matrix:** fill encryption keys + re-encryption batches rows → `implemented`.
6. **Tests:** controller/service tests for 202 path; functional smoke on clean-start.

## Out of scope

- Batch engine, scheduler cron, parallelism tuning (`ezkey.encryption.reencryption.*` config).
- RFC 9457 migration for rotate errors (separate `F-rfc9457-errors` track).
- Alerts list (separate P3 slice).

## Design decisions (from I-2026-0002, retained)

| Decision | Choice |
|----------|--------|
| HTTP success | `202 Accepted` |
| Progress surface | Existing batches table (no new widget) |
| UX | Toast "started" + query invalidation |
| Failure before enqueue | Synchronous 4xx unchanged |

## Implementation sequence

1. API: extract enqueue from `processBatch` in request thread (`@Async` or delegate to existing scheduler hook).
2. Response DTOs + OpenAPI annotations.
3. UI: simplify trigger dialogs (fire-and-forget).
4. Postman + matrix + promote I-2026-0002 → `done` on closeout.

## Validation

- Maven baseline + targeted admin-api tests — OK
- `npm run build` (Admin UI) — OK
- Maintainer: functional test (Global Admin → Encryption Keys → trigger/create batches) — OK
- Maintainer: elective suite including concurrent re-encryption test — OK

## Closeout

- **I-2026-0002** → `done`
- Method log: [`method-logs/ML-2026-06-28-admin-ui-encryption-reencryption-async-closeout.md`](method-logs/ML-2026-06-28-admin-ui-encryption-reencryption-async-closeout.md)

## Links

- Idea: [`ideas/I-2026-0002-reencryption-batch-async-button.md`](ideas/I-2026-0002-reencryption-batch-async-button.md)
- Grill: [`grill-sessions/blitz-2026-05-08-1-D1-D2-grill-me.md`](grill-sessions/blitz-2026-05-08-1-D1-D2-grill-me.md)
- Ops doc: [`../../../docs/REENCRYPTION_OPERATIONS.md`](../../../docs/REENCRYPTION_OPERATIONS.md)
- Matrix: [`../../admin-ui-paginated-screens-matrix.md`](../../admin-ui-paginated-screens-matrix.md)
