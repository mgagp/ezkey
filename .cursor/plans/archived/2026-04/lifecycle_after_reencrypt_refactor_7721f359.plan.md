---
name: lifecycle after reencrypt refactor
overview: "The recent re-encryption refactor splits orchestration, batch creation, target SQL queries, and parallel execution. This updates the encryption key lifecycle work plan: Phase A should reuse `ReencryptionTargetQueryService` and the same target discovery as batch creation, with explicit notes on parallelism and guard rails."
status: completed
completedNote: "Phase A delivered; see Completion summary and Follow-up (separate plan) below."
todos:
  - id: spec-crossref
    content: Add SPEC subsection linking lifecycle verification to ReencryptionTargetQueryService + target discovery (optional extract ReencryptionTargetRegistry)
    status: completed
  - id: phase-a-core
    content: Implement KeyUsageVerificationService (or equivalent) delegating to ReencryptionTargetQueryService and shared target list
    status: completed
  - id: phase-a-api-ui
    content: Extend EncryptionKeyResponse + encryption-keys UI for lifecycle/remaining records
    status: completed
  - id: docs-reenrypt
    content: Update REENCRYPTION_OPERATIONS.md for new service split and parallel workers note
    status: completed
isProject: false
---

# Encryption key lifecycle plan (updated after re-encryption refactor)

## What changed in the codebase

Re-encryption is no longer a single large `ReencryptionService` with all responsibilities inlined. The current layout is:

| Component | Role |
|-----------|------|
| [`ReencryptionService`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java) | Orchestration only: scheduler, manual triggers, delegates to creation/processing/parallel runner |
| [`ReencryptionBatchCreationService`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchCreationService.java) | Creates batch rows (`REQUIRES_NEW`); owns `discoverReencryptableTargets()` and `getPrimaryKeyFromKeyset()` |
| [`ReencryptionTargetQueryService`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java) | **Single** `countRecordsEncryptedWithKey(table, column, keyId)` and `fetchRecords(...)` using `ENC:{keyId}:%` |
| [`ReencryptionBatchProcessingService`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchProcessingService.java) | Processes one batch per transaction (`REQUIRES_NEW`) |
| [`ReencryptionBatchParallelRunner`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchParallelRunner.java) | Optional parallel batch execution; **mutex per `target_table`** so at most one batch per table at a time when workers &gt; 1 |

This aligns with the lifecycle spec’s intent: **one canonical prefix-based counting implementation** for “remaining usage,” shared by migration and future verification.

## Impact on the lifecycle strategy ([`docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md`](c:/github/ezkey/docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md))

### Strengthened (no change to product goals)

- **Phase A “source of truth”** should implement verification by **reusing** [`ReencryptionTargetQueryService.countRecordsEncryptedWithKey`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java), not re-implementing SQL switches.
- **Target inventory** must stay aligned with [`ReencryptionBatchCreationService.discoverReencryptableTargets()`](c:/github/ezkey/ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchCreationService.java) (same `Reencryptable` sample entities / columns). Prefer **one shared API** for “list all `(table, column)` targets” to avoid drift between batch creation and “drained” verification (today discovery lives on the creation service; lifecycle code should call it or extract a small `ReencryptionTargetRegistry` later).

### Adjustments to the implementation blueprint

1. **Naming the future service**
   The spec’s `KeyUsageVerificationService` becomes a thin facade: iterate targets (same discovery as batch creation), sum `targetQueryService.countRecordsEncryptedWithKey`, persist snapshot fields, expose DTO fields. No need for a second counting stack.

2. **Guard rails and parallelism**
   Parallel batch processing does **not** change the definition of “drained,” but it reinforces that lifecycle eligibility must consider **batch queue state** (e.g. `PENDING` / `IN_PROGRESS` / `FAILED` for that key) as already described in the spec. Table-level mutex reduces cross-column races on the same row but does not remove the need for “no active work” checks.

3. **Admin API**
   [`EncryptionKeyController`](c:/github/ezkey/ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java) still maps entities via `toResponse`; additive lifecycle fields will compose verification + optional batch summary — unchanged contract shape, only new fields.

4. **Docs**
   Update [`docs/REENCRYPTION_OPERATIONS.md`](c:/github/ezkey/docs/REENCRYPTION_OPERATIONS.md) when documenting lifecycle: point to the split services and `parallelBatchWorkers` so operators understand that “drained” is orthogonal to parallel execution.

## Recommended doc tweak (when you implement)

Add a short subsection to [`docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md`](c:/github/ezkey/docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md) §5 (source-of-truth) explicitly naming:

- `ReencryptionTargetQueryService` as the counting implementation
- `ReencryptionBatchCreationService#discoverReencryptableTargets` (or a future extracted registry) as the target list

## Can we start the initiative?

**Yes.** The refactor removes duplication for counting and clarifies boundaries; Phase A (derived lifecycle + remaining counts + API/UI) can proceed **without** undoing the parallel design. The main technical task is to **wire lifecycle verification to existing query + discovery** and optionally extract shared target discovery to prevent future drift.

## Suggested Phase A sequence (unchanged in spirit, updated in touchpoints)

1. Core: verification method using `ReencryptionTargetQueryService` + same targets as batch creation; optional persisted snapshot columns on `EncryptionKey` or a small side table.
2. Admin API: extend `EncryptionKeyResponse` with `lifecycleStage`, `remainingRecords`, `lastVerifiedAt`, etc.
3. Admin UI: [`ezkey-admin-ui/src/pages/encryption-keys.tsx`](c:/github/ezkey/ezkey-admin-ui/src/pages/encryption-keys.tsx) + i18n + help.
4. Tests: unit tests around verification sums; reuse patterns from [`ReencryptionServiceTest`](c:/github/ezkey/ezkey-core/src/test/java/org/ezkey/security/ReencryptionServiceTest.java) for target discovery.

Phase B/C (decommission schedule/cancel, final deletion) remain as in the spec; they build on the same verification and batch-state checks.

---

## Completion summary

This initiative is **closed**. The refactor-aligned lifecycle work proceeded as planned: verification reuses `ReencryptionTargetQueryService` and the same re-encryption target discovery as batch creation; Admin API and encryption-keys UI expose lifecycle fields and derived “remaining” counts; operations docs reference the service split and parallel workers.

Archived todos (all **completed**): SPEC cross-reference, `KeyUsageVerificationService` + shared targets, `EncryptionKeyResponse` + UI, `REENCRYPTION_OPERATIONS.md` updates.

## Discussion recap (recent) — `records_encrypted` vs derived counts

**Out of scope for this plan (separate work plan):** the persisted column **`records_encrypted`** on `ezkey_encryption_key` is intended to reflect **cumulative encrypt usage** (increment when ciphertext is written using that key). It is **not** wired through all application encryption paths today, so the UI can show **0** even when data exists—operators rely on **derived** metrics instead.

**What this plan delivered instead:** **live prefix-based verification** (`ENC:{keyId}:%`) via `ReencryptionTargetQueryService`, aligned with batch targets, plus **`records_reencrypted`** updates on completed re-encryption batches. That gives a truthful **“remaining usage”** story without depending on `records_encrypted` being accurate yet.

**Follow-up initiative (recommended separate plan):** give `records_encrypted` its intended meaning by:

- Inventorying every code path that produces encrypted values and defining the counting rule (e.g. +1 per row/column write).
- A **single** increment path (shared service or carefully scoped hooks), with **atomic** updates (e.g. `UPDATE … SET records_encrypted = records_encrypted + :n`) so parallel writers stay correct; watch for **row hot-spot** contention on the PRIMARY key row if needed.
- Clear semantics vs **`records_reencrypted`** and optional migration/re-encrypt edge cases (historical cumulative vs current stock).

Treat that as **orthogonal** to lifecycle UI/verification: same product area, different epic (“encryption key usage counters” or similar).
