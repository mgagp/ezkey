# Re-encryption batch pipeline — phased plan (design, parallelism, observability)

> **Historical.** Phase 1/2 framing below is superseded. Living operator and collaborator canon is
> [`docs/REENCRYPTION_OPERATIONS.md`](../REENCRYPTION_OPERATIONS.md) (service split, parallel mutex,
> Micrometer, sharding). Do not treat Micrometer as still out of scope.

**Purpose:** Actionable scope for **Phase 1** implementation (solid refactor + cross-target parallelism + operator-grade feedback), and explicit **Phase 2** follow-up (heavy-table sharding). Updates the earlier planning note with agreed boundaries so work stays **pragmatic** (avoid accidental complexity).

**Audience:** Engineers and coding assistants implementing or reviewing changes to `ReencryptionService` and related code in `ezkey-core`, Admin API, and Admin UI.

**Related docs:** [REENCRYPTION_OPERATIONS.md](../REENCRYPTION_OPERATIONS.md), [ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md](../ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md).

---

## Phase 1 — This plan (deliverable)

**Primary goals (in order):**

1. **Decompose** [`ReencryptionService`](../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java): replace the monolith with **clear collaborators** (orchestration, batch creation, row processing / persistence, crypto boundaries). Replace **`self`-only** transaction boundaries with **injected beans** whose public methods carry `@Transactional` / `REQUIRES_NEW` as intended.

2. **Parallelism across distinct batch jobs** — bounded **thread pool** so **different** re-encryption batches (typically **different** `(target_table, target_column)` / batch rows) can advance **concurrently**. **Invariant:** do **not** run two workers on the **same** logical migration `(target_table, target_column, old_key_id)` at once (avoids lock chaos; matches prior planning choice).

3. **Minimum observability for operators (Admin UI)** — enough feedback that a daily operator running migration over **hundreds of thousands** of rows does **not** feel the system is **frozen** because **chunks are large** and progress appears stuck. This is **not** the same as a full **Micrometer + Grafana** stack.

**Explicitly out of scope for Phase 1:**

- **Micrometer / Prometheus / Grafana** as a required deliverable. Ezkey may adopt formal metrics **later** project-wide; tying Phase 1 to a metrics pipeline + external dashboards crosses the **pragmatic complexity** line for this iteration.
- **Intra-batch sharding** (multiple threads chewing **one** batch / one heavy table split by ID ranges). That is **Phase 2** (see below).

**Configuration:** extend [`TinkProperties.Reencryption`](../../ezkey-core/src/main/java/org/ezkey/config/TinkProperties.java) with **a few** knobs only (e.g. parallel worker count, queue bound). Avoid a large surface of tunables.

---

## Phase 2 — Separate future plan (after Phase 1 lands)

**Problem shape:** Re-encryption touches **four** `(table, column)` targets. **Two** stay comparatively **small** in practice; the **runtime cost** of full migration is dominated by **`ezkey_enrollment`** and **`ezkey_auth_attempt`** (multiple columns each → multiple batch rows, large row counts).

**Goal:** **Sharding + multi-threaded** processing **within** those heavy targets (ID-range partitions, strict locking/retry rules, coordination). This is the **“nerf de la guerre”** and deserves its **own** work package once Phase 1 provides a **clean seam** (executors, batch runner contracts, tests).

**Dependency:** Phase 1’s **modular design** is the **foundation**; do not entangle Phase 2 prematurely into Phase 1.

---

## Observability — what Phase 1 should mean (operator UX)

**Intent:** The operator looks at [encryption keys / batches in Admin UI](../../ezkey-admin-ui/src/pages/encryption-keys.tsx) and sees **credible movement**: `records_done`, `progress_pct`, `last_batch_at`, status — **without** opening PostgreSQL.

**Practical levers (examples; choose what fits implementation):**

- **UI:** while any batch is `PENDING` / `IN_PROGRESS`, use a **sensible auto-refresh** (or clear manual refresh) so the table is not static for minutes.
- **Backend:** ensure batch rows are **updated frequently enough** that progress is not stuck until a huge internal slice finishes (tune internal chunking vs `batchSize` / save frequency **without** weakening correctness).
- **API:** existing [`GET .../reencryption-batches`](../../ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java) remains the **source of truth** for the UI; optional small additions only if they reduce confusion (avoid duplicating metrics systems).

**Micrometer later:** When the project adds **central** observability, **then** wiring timers/counters to the **same** code paths will be natural. Phase 1 should **not** block on that.

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

Spring AOP applies `@Transactional` only on **calls through the proxy**. Internal `this.method()` calls skip advice, so **`REQUIRES_NEW`** would not apply as intended.

**Workaround in use:** inject a proxied `self` and call `self.persist…()` / `self.process…()`.

**Design direction:** Replace with **explicit collaborators** (separate `@Service` beans) so transaction boundaries are **module** boundaries, not intra-class hacks.

---

## 4. Correctness vs performance (trade-off)

Recent stability work prioritized **safe** behavior under churn (pessimistic locks, per-row `REQUIRES_NEW`), which **costs** throughput. Phase 1 **improves perceived and actual wall-clock** when **multiple batches** exist, by **parallelizing distinct jobs**. It does **not** yet attack the dominant cost on a **single** huge enrollment/auth_attempt batch (Phase 2).

---

## 5. Candidate decomposition (Phase 1)

1. **Orchestrator** — selects batches, applies `maxBatchesPerRun` / duration, dispatches to executor; no row crypto.
2. **Batch creation service** — counts + inserts `ReencryptionBatch` rows.
3. **Batch processor / row worker** — cursor, candidate gate, `REQUIRES_NEW` persist; single place for retry policy.
4. **Target adapters** (optional incremental) — table/column-specific listing.
5. **Executor** — bounded pool; **one batch per worker** with **distinct** target identity guard.

**Testing:** unit tests on collaborators; integration + elective concurrent tests (`ReencryptionFullTriggerConcurrentActivityElectiveTest`).

---

## 6. SQL / JPA note

Ciphertext transformation stays in **Java** (Tink). “Batch SQL UPDATE” without crypto in the app is not a drop-in optimization. Levers: shorter transactions, JDBC batching of **updates after** encrypt, parallel **distinct** batches (Phase 1), sharding (Phase 2).

**Optional later:** time-tiered chunk sizes (hot vs cold rows) — **not** Phase 1 unless proven trivial; avoid accidental “factory” logic.

---

## 7. Performance checklist (Phase 1)

- [ ] Baseline: row counts, `TinkProperties.Reencryption` (`batchSize`, `throttleMs`).
- [ ] Confirm parallel path only schedules **non-overlapping** batch identities.
- [ ] Operator test: long run still shows **moving** progress in UI without raw SQL.

---

## 8. Key code entry points

| Area | Typical location |
|------|------------------|
| Core service | `ezkey-core/.../ReencryptionService.java` |
| Batch entity / progress | `ezkey-core/.../ReencryptionBatch.java` |
| Repositories | `EnrollmentRepository`, `AuthAttemptRepository` |
| Admin API batches | `ezkey-admin-api/.../EncryptionKeyController.java` |
| Admin UI | `ezkey-admin-ui/src/pages/encryption-keys.tsx` |
| Elective regression | `ezkey-tests/.../ReencryptionFullTriggerConcurrentActivityElectiveTest.java` |

---

## 9. OpenAPI

After API changes: maintainer runs `update-specs` per repo rules — **do not** hand-edit `specs/`.

---

## 10. Summary

| Phase | Focus |
|-------|--------|
| **1** | Solid **refactor**, **cross-batch** thread pool, **Admin UI–friendly** progress feedback — **no** Grafana/Micrometer mandate. |
| **2** | **Sharding + parallelism** inside **enrollment** and **auth_attempt** migrations — **separate** plan. |

This document is the **versioned** planning baseline; adjust when implementation decisions land.
