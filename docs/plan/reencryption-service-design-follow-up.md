# Re-encryption batch pipeline — design follow-up (planning context)

**Purpose:** Background for a future planning or implementation session focused on **separating responsibilities**, **reducing operational risk**, and **improving performance** of the re-encryption batch pipeline. It summarizes domain constraints, recent fixes, current pain points, and candidate directions—without prescribing a single architecture.

**Audience:** Engineers and coding assistants preparing a refactor or performance pass on `ReencryptionService` and related code in `ezkey-core`.

**Related docs:** [REENCRYPTION_OPERATIONS.md](../REENCRYPTION_OPERATIONS.md), [ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md](../ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md).

---

## 1. Domain recap

- After key rotation, ciphertext still prefixed with **old** key IDs must be migrated to the **current PRIMARY** key. Work is tracked in `ezkey_reencryption_batch` (one row per `(old_key, target_table, target_column)` with progress counters).
- **Live systems** keep accepting traffic: new rows may still be encrypted with an older key until migration completes; **concurrent updates** (e.g. `version` bumps on enrollments, new auth attempts) are **expected**, not exceptional.
- Admin and scheduler paths call into the same core service to **create** batches, **process** them, and **trigger** full runs.

---

## 2. Issues addressed in recent debugging (stability)

These were correctness and data-integrity problems under concurrency—not cosmetic:

| Topic | Problem | Direction of fix |
|-------|---------|-------------------|
| **Transaction boundaries** | Batch rows created in a transaction that was not visible to `REQUIRES_NEW` processing | Creation/processing boundaries adjusted so batch rows are committed before processing (e.g. `REQUIRES_NEW` for batch creation where appropriate). |
| **Poisoned batch transaction** | Failed `flush` in the same TX as the batch cursor | Persist work in **separate** transactions (`REQUIRES_NEW`) per row or small unit so one failure does not mark the whole batch TX rollback-only. |
| **Optimistic locking / row churn** | Concurrent `version` updates vs re-encrypt writes | **Pessimistic write** locks when loading rows for persistence; bounded retries where relevant. |
| **Double mutation in one TX** | Mutating managed entities in the batch loop caused unexpected flushes before locked persist | **Two-phase** flow in the batch loop: classify candidates (read-only checks) then persist in a second phase with explicit per-id handling. |
| **`records_total` vs reality** | Snapshot count at batch creation could be **lower** than rows actually processed when new matching rows appear during the run | **`ReencryptionBatch.updateProgress`**: raise `records_total` to `max(previous_total, done + failed + skipped)` so DB checks `chk_reencryption_batch_*` remain satisfied and progress stays ≤ 100%. |

These fixes favor **correctness under load** over naïve throughput.

---

## 3. Current design pressure points

### 3.1 `ReencryptionService` as a multi-responsibility unit

`ReencryptionService` currently combines, in one large class:

- **Orchestration** — trigger full re-encryption, scheduler hooks, ordering of batch creation vs processing.
- **Batch lifecycle** — status transitions, progress updates, completion, audit events, key statistics.
- **Data access patterns** — repository queries, pagination/cursors, target-specific fetch (`LIKE` prefix scans).
- **Cryptography** — decrypt with old material, encrypt with new key via `EncryptionService`.
- **Concurrency control** — pessimistic loads, `REQUIRES_NEW` entry points, `EntityManager.flush`, error handling per row.
- **Self-invocation** — methods that must run through the Spring proxy for transaction propagation.

This concentration makes **reasoning**, **testing**, and **performance tuning** harder: any change risks unintended interactions between orchestration and low-level persistence.

### 3.2 `self` injection (symptom, not root cause)

Spring AOP applies `@Transactional` (and similar) only on **calls through the proxy**. Internal `this.method()` calls skip advice, so **`REQUIRES_NEW`** and other boundaries would not apply as intended.

**Workaround in use:** inject a reference to the proxied bean (often named `self`) and call `self.persist…()` / `self.process…()` so transactional boundaries are honored.

**Why it matters for design:** It is a signal that **transactional boundaries are modeled inside one class** instead of at **clear collaborator** boundaries. A refactor should aim to replace “self calls” with **explicit collaborators** whose public API is naturally proxied (separate `@Service` / `@Component` beans, or smaller facades).

---

## 4. Correctness vs performance (observed trade-off)

Recent changes prioritized **safe behavior** under concurrent churn:

- **Pessimistic locking** serializes conflicting writers on the same row and avoids subtle lost-update / OL failures.
- **Per-row `REQUIRES_NEW`** adds transaction overhead and round-trips compared to bulk updates in one transaction.

**Operational observation:** With **thousands of rows** and **operational churn**, full re-encryption can feel **noticeably slow**. That is consistent with: lock contention, many short transactions, throttle/sleep settings, and sequential batch processing—not necessarily a bug, but a **scalability** concern.

A future design session should treat **performance** as a first-class requirement alongside correctness: measure (batch duration, rows/sec, lock wait time, DB stats), then decide whether to adjust **granularity** (batch size), **parallelism** (careful—locks and keyset), **lock scope** (column/row vs unnecessary work), or **scheduling** (off-peak, rate limits).

---

## 5. Candidate directions for decomposition (non-prescriptive)

Use these as **planning axes**; the actual split should follow measured bottlenecks and team preference for module boundaries.

1. **Batch orchestrator** — Chooses which batch to run, drives high-level state machine (PENDING → IN_PROGRESS → COMPLETED/FAILED), coordinates audit and key stats. No direct row-level SQL/crypto.

2. **Work unit executor** — Given `(batch, record id or cursor)`, loads with appropriate lock, calls crypto, saves. **Single place** for `REQUIRES_NEW` and retry policy.

3. **Batch factory / planner** — Counts candidates, creates `ReencryptionBatch` rows with initial `records_total` estimates; documents that totals may increase under live traffic (entity layer already aligns totals on progress).

4. **Target adapters** — One small component per target or per table family: how to list candidates, map to `Reencryptable`, column names. Reduces `if (enrollment) … else if (authAttempt) …` spread.

5. **Crypto façade** — Thin wrapper over `EncryptionService` for “re-encrypt this field” to keep `EncryptionService` usage and error mapping in one place.

6. **Metrics / observability** — Structured logs or Micrometer timers: rows/sec, failures, lock waits, batch duration—so performance work is evidence-based.

**Testing:** Prefer **unit tests** on small collaborators; keep **integration** tests for end-to-end batch + DB; retain or extend **elective** tests under concurrent activity (`ReencryptionFullTriggerConcurrentActivityElectiveTest` and similar).

---

## 6. Performance planning checklist (for the follow-up session)

- [ ] Baseline: row counts, batch sizes, throttle (`TinkProperties.Reencryption`), scheduler limits.
- [ ] Identify dominant cost: DB round-trips, lock wait, crypto CPU, or HTTP trigger timeout.
- [ ] Evaluate **safe parallelism** (e.g. multiple batches for disjoint targets vs same-table parallelism risks).
- [ ] Revisit **pessimistic lock scope** and **hold time** (minimal work while holding lock).
- [ ] Consider **read-only prefetch** vs **lock-then-mutate** patterns only where proven safe.
- [ ] Document operator expectations: live traffic will **extend** migration duration; `records_total` may grow.

---

## 7. Key code entry points (for navigation)

| Area | Typical location |
|------|------------------|
| Core service | `ezkey-core/.../ReencryptionService.java` |
| Batch entity / progress | `ezkey-core/.../ReencryptionBatch.java` (`updateProgress`, constraints in DB migration `V3__...`) |
| Repositories | `EnrollmentRepository`, `AuthAttemptRepository` (e.g. `findByIdForReencryptionUpdate`) |
| Elective regression | `ezkey-tests/.../ReencryptionFullTriggerConcurrentActivityElectiveTest.java` |

---

## 8. Summary for the next session

**Goal:** Evolve from a **monolithic** `ReencryptionService` with **self-proxy** workarounds toward **clear modules**, **explicit transaction boundaries**, and **measurable performance**—without regressing correctness under concurrent **live** workloads.

**Non-goals implied:** A rewrite for rewrite’s sake; removing pessimistic locking without a replacement strategy; sacrificing observability.

This document is intended to be **stable context**; update it when major behavioral or structural decisions are made.
