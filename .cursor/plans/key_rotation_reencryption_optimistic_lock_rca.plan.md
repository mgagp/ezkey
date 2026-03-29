---
name: Key rotation re-encryption optimistic lock RCA
overview: Root-cause analysis for ObjectOptimisticLockingFailureException during full re-encryption, elective-first test strategy with in-test churn-like activity, ordered fix validation, and a follow-on phase for high-cadence churn with periodic rotation and re-encryption.
status: validated — proceed in order (repro test → fix → verify)
---

# Key rotation, re-encryption, and optimistic-lock failures

## Validated decisions (2026-03-28)

- **Test strategy (primary):** Prefer an **elective** test that stays in the **same subject zone** as existing crypto/key work (e.g. alongside [`KeyRotationSyncWindowTest`](ezkey-tests/src/test/java/org/ezkey/tests/security/crypto/KeyRotationSyncWindowTest.java): tags `ELECTIVE`, `ENCRYPTION`, etc.).
- **Activity generation:** It is acceptable for that elective test to include an **in-test “slice” of operational churn** — background activity whose only purpose is to **create realistic concurrent updates** (enrollments, auth-adjacent flows) so the re-encryption path is exercised under contention. This is **not** a full operational-churn suite by itself; it is a **focused harness** inside one elective scenario.
- **Operational churn profile:** Treat **operational-churn** as a **good complementary path** (same stack, longer or profile-driven runs). The elective test remains the **first-class** gate for this bug class; churn can reuse helpers and later absorb extended scenarios.
- **Execution order:** Validate and resolve **in order**: (1) failing reproduction test, (2) fix, (3) regression + elective + targeted churn run as appropriate.

## Future phase (after this RCA/fix — not in scope of the immediate delivery)

**Goal:** Extend **operational churn** so that crypto and re-encryption batches are stressed **much more often than production** (where batches might run on the order of **quarterly**).

**Idea (parameters to tune later):**

- Introduce a **new key** on a fixed cadence (e.g. **every ~15 minutes** in the churn profile).
- Drive **re-encryption batches** on another cadence (e.g. **every ~20 minutes**), compatible with promotion/sync semantics and avoiding unsafe overlap (design TBD when implementing).

This becomes a **continuous probe** of: rotation, Tink keyset handling, batch creation, `processBatch`, and concurrent application traffic — aligned with [`docs/plan/operational-churn-ezkey.plan.md`](docs/plan/operational-churn-ezkey.plan.md) philosophy (Category B, production-representative contention).

---

## What failed (symptom)

- **`ObjectOptimisticLockingFailureException`** on `UPDATE ezkey_enrollment ... WHERE enrollment_id=? AND version=?` with **0 rows** updated.
- Surfaces on **auto-flush before native queries** (`findEncryptedEnrollmentProofTokenLike`, `findEncryptedDeviceProofTokenLike`) in [`ReencryptionService.processBatch`](ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java) during **manual full re-encryption** ([`triggerFullReencryption`](ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java)).
- [`Enrollment`](ezkey-core/src/main/java/org/ezkey/enrollment/domain/entity/Enrollment.java) uses **`@Version`** — concurrent updates to the same row invalidate the version expected at flush.

## Leading hypotheses (ordered)

1. **H1 — Concurrent updates** to the same enrollment rows while re-encryption runs (amplified by operational churn or overlapping HTTP work). Explains quiet environments vs busy ones.
2. **H2 — Long transaction + large persistence context**: [`triggerFullReencryption`](ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java) is `@Transactional` and calls `processBatch` via **self-invocation** (inner `@Transactional` on `processBatch` does not apply). Same pattern in scheduled [`processReencryptionBatches`](ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java). Widens the window for H1.
3. **H3 — Same table, different columns** (multiple `ezkey_enrollment` batches) increases touches per `enrollment_id` / `@Version` in a short wall-clock span when combined with H1.

## Elective test design (concrete)

- **Location:** `ezkey-tests`, package aligned with **`org.ezkey.tests.security.crypto`** (next to `KeyRotationSyncWindowTest`).
- **Tags:** `@Tag(TestTags.ELECTIVE)`, `@Tag(TestTags.ENCRYPTION)`; add `SLOW` / `TIME_DEPENDENT` if sync window or waits are required.
- **Steps (sketch):**
  1. Obtain admin token; ensure stack healthy.
  2. Establish **old-key re-encryption need** (rotate → wait for promotion / follow existing elective patterns from `KeyRotationSyncWindowTest`).
  3. Start **bounded background executor** (churn-like): low-rate loops that **update enrollments or realistic auth flows** — reuse helpers from operational-churn if available (`TenantAdminTestHelper`, etc.).
  4. Call **`POST /api/v1/encryption-keys/reencrypt/trigger`** ([`EncryptionKeyController`](ezkey-admin-api/src/main/java/org/ezkey/admin/controller/EncryptionKeyController.java)).
  5. Assert **HTTP success**, **no** `batches_failed` / audit noise indicating `ObjectOptimisticLockingFailureException`; optional DB spot-checks per [`ezkey-tests/AGENTS.md`](ezkey-tests/AGENTS.md).

**Optional:** A smaller **`ezkey-core`** integration test with two threads remains a **secondary** accelerator if the elective test is slow to iterate.

## Fix directions (after red test)

- **Per-batch transaction** (`REQUIRES_NEW`) via **separate bean** or **self-injection** so `processBatch` is actually transactional; or `EntityManager.flush/clear` between batches — tradeoffs documented at implementation time.
- **Bounded retry + refresh** on optimistic lock for re-encryption row updates (only if design review accepts).
- **Docs:** Update [`docs/REENCRYPTION_OPERATIONS.md`](docs/REENCRYPTION_OPERATIONS.md) only if operator semantics change.

## References

- [`ReencryptionService`](ezkey-core/src/main/java/org/ezkey/security/ReencryptionService.java)
- [`EncryptionEntityListener`](ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java) (`@PreUpdate` on enrollment encrypted fields)
- [`docs/REENCRYPTION_OPERATIONS.md`](docs/REENCRYPTION_OPERATIONS.md)

## Implementation todos (ordered)

1. **repro-elective** — Add elective test with in-test churn-like activity + `POST .../reencrypt/trigger`; expect **red** before fix.
2. **fix-transaction-boundary** — Implement chosen boundary/retry strategy; **green** on same test.
3. **verify** — `mvn` tests (module scope per repo rules); optional operational-churn profile run when churn helpers are touched.
4. **followup-churn-crypto** — Backlog: operational-churn profile with periodic rotation + periodic re-encryption (15m / 20m style cadence, tunable).
