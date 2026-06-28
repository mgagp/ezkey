# Backlog Idea — `I-2026-0002` Re-encryption batch UI: async button behavior

## Metadata

- **ID:** `I-2026-0002`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-05-08`
- **Updated at:** `2026-06-28`
- **Last reviewed at:** `2026-06-28`
- **Phase tags:** `P1-operability`, `P2-hardening`
- **Component tags:** `admin-ui`, `admin-api`
- **Captured by:** Marc

## Intent

Ensure the Admin UI re-encryption controls always trigger asynchronous batch execution and return control to the operator immediately, with progress observable through the existing batch table. No HTTP request should block while the actual re-encryption work runs.

## Problem and value

- **Problem:** When the operator triggers re-encryption for a specific encryption key from the Admin UI, the action *appears* to behave synchronously — the button stays in a busy state for the entire batch run. This perception needs to be validated against the actual implementation. If confirmed, the design does not scale: with hundreds of thousands of records to re-encrypt, the HTTP call cannot remain pending. If the perception is incorrect, the underlying cause of the apparent block must still be identified and addressed.
- **Expected value:** Predictable async UX consistent with the existing batch table; no risk of HTTP timeouts or blocked operators on large datasets; alignment with the broader `F-encryption-key-rotation` posture.

## Grilling decisions (2026-05-19)

See [`../grill-sessions/blitz-2026-05-08-1-D1-D2-grill-me.md`](../grill-sessions/blitz-2026-05-08-1-D1-D2-grill-me.md).

- **Confirmed:** per-key trigger is synchronous today (`ReencryptionService` processes batches before HTTP returns); UI blocking matches API.
- **R1:** no blocking re-encryption on Admin API; manual triggers enqueue background work only.
- **HTTP:** **`202 Accepted`** + minimal body (`message`; optional `batchIds` if cheap). List batches endpoint remains source of truth for Admin UI.
- **UX:** toast “started” + operator uses existing batches table (invalidate/refetch).
- **Contract:** same async pattern for per-key, full trigger, and create+execute paths.
- **Out of scope:** batch engine, parallelism, scheduler.

## Scope

- **In scope:**
  - Refactor manual trigger endpoints in `admin-api` to enqueue only (no inline `processBatch` in request thread).
  - Return **202 Accepted** with agreed response shape; update OpenAPI/Orval when implementing.
  - Admin UI: non-blocking trigger (toast + batches table refresh); remove dialog wait on HTTP completion.
  - Align all three manual trigger paths on the same async contract.
- **Out of scope:**
  - Re-encryption algorithm, batch persistence, sharding, parallel runner, scheduled `processReencryptionBatches`.

## Key assumptions

- The existing batch table is the operational progress surface (no new widget for R1).
- Background execution can reuse existing batch processing (scheduler or async invocation) once enqueue is separated from the HTTP thread.

## Risks and exceptions

- Enqueue failures (validation, no PRIMARY key) still return **4xx** synchronously; only successful enqueue returns **202**.
- Ensure audit events distinguish “trigger accepted” vs “batch completed” if today they imply completion on the manual path.

## Promotion notes

Promoted to **TB-2026-06-27-admin-ui-encryption-reencryption-async** (2026-06-27). GitHub **#264**.

## Closeout (2026-06-28)

**Shipped** via PR **#265** (merged to `main`). Manual triggers are enqueue-only (**202 Accepted**);
progress via existing batches table on `/encryption-keys`.

- TB: [`TB-2026-06-27-admin-ui-encryption-reencryption-async`](../TB-2026-06-27-admin-ui-encryption-reencryption-async.md) → `done`
- Method log: [`method-logs/ML-2026-06-28-admin-ui-encryption-reencryption-async-closeout.md`](../method-logs/ML-2026-06-28-admin-ui-encryption-reencryption-async-closeout.md)
- Matrix: encryption keys + re-encryption batches → `implemented`

## Links

- Related feature: `F-encryption-key-rotation`
- Related principles: `Design Principle #1` (simplicity), `#5` (operator-first)
