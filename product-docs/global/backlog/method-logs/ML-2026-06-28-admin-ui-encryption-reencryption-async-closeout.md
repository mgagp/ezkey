# Method log — `ML-2026-06-28-admin-ui-encryption-reencryption-async-closeout`

## Metadata

- **Date:** `2026-06-28`
- **Slice:** Async manual re-encryption triggers (Admin API + Admin UI)
- **TB:** `TB-2026-06-27-admin-ui-encryption-reencryption-async`
- **Idea:** `I-2026-0002-reencryption-batch-async-button`
- **GitHub:** #264 (closed), PR #265 (merged)

## Summary

Delivered non-blocking manual re-encryption for Global Admin operators:

1. **Admin API / core:** `POST /reencrypt/trigger`, `POST /{keyId}/reencrypt`, and batch resume return
   **202 Accepted** and enqueue background processing (`ReencryptionService.enqueue*`).
2. **Admin UI:** toast « accepted », invalidation keys + batches tables; dialogs no longer wait on batch
   completion.
3. **Contracts:** OpenAPI refresh, Postman, Orval; matrix rows encryption keys / re-encryption batches →
   `implemented`.
4. **Tests:** `EncryptionKeyControllerTest` (202); elective
   `ReencryptionFullTriggerConcurrentActivityElectiveTest` aligned (202 + poll until batches settle).

## Validation

- Maintainer: functional test (Admin UI + Demo Device) — OK
- Maintainer: elective test suite — OK
- Agent: Maven controller tests; Admin UI build; clean-start → update-specs → generate:api

## Traceability

- Origin: Blitz D1 (2026-05-08) → `I-2026-0002` (grilled 2026-05-19)
- P3 follow-on from closed program `I-2026-0028` (encryption keys matrix rows were `draft`)

## Residual

- `create-batches` endpoint remains **200** (create-only; unchanged contract).
- Batch engine tuning, scheduler cron, RFC 9457 for rotate errors — out of scope (see TB).
- Matrix `draft`: alerts, audit chain checkpoints — separate slices when operator chooses.

## Closeout actions

1. **I-2026-0002** → `done`
2. **TB-2026-06-27** → `done`
3. **backlog/index.md** — `I-2026-0002` → Recently completed; **I-2026-0028** index row → `done`
4. **I-2026-0028** — P3 encryption keys / batches rows marked **Done**; residual narrowed
