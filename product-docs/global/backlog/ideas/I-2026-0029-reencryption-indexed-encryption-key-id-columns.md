# Backlog Idea — `I-2026-0029` Re-encryption: indexed encryption key id columns

## Metadata

- **ID:** `I-2026-0029`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-06-28`
- **Updated at:** `2026-07-26`
- **Last reviewed at:** `2026-07-26`
- **Phase tags:** `P2-hardening`
- **Component tags:** `core`, `admin-api`, `infra`, `docs`
- **Feature anchor:** `F-encryption-key-rotation`
- **Captured by:** Marc

## Intent

Replace re-encryption **discovery queries** that scan encrypted TEXT columns with
`LIKE 'ENC:{keyId}:%'` by **indexed `BIGINT` columns** (one per encrypted field) referencing
`ezkey_encryption_key`, maintained on encrypt and re-encrypt. Deliver **before first production
installation**; no production database retrofit from the LIKE-only model.

## Problem and value

- **Problem:** Batch creation, batch fetch, and Admin **Tracked** counts (`KeyUsageVerificationService`,
  `ReencryptionTargetQueryService`) use prefix `LIKE` on large ciphertext columns. At scale this
  forces sequential scans and unnecessary database pressure during rotation and nightly re-encryption.
- **Expected value:** O(log n) keyed lookups via composite indexes; stable performance as auth attempts
  and enrollments grow; same semantics as today with a cleaner SQL design; referential integrity on
  key ids.

## Pre-release constraints (operator)

These constraints **narrow scope** and avoid accidental migration complexity:

1. **No production release yet** — the LIKE-based discovery model will never exist in a production
   database. Implement in the Flyway baseline / pre-release schema; dev environments reset via
   clean-start.
2. **No unencrypted → encrypted upgrade** — disposable test databases without encryption are not
   migration targets. Ezkey does not support in-place promotion from plaintext-at-rest to
   encrypted-at-rest.
3. **No backfill program for “legacy production rows”** — only code + schema for the first
   production-capable install path.

## Scope

### In scope

- **Four derived columns** (naming pattern `{column}_encryption_key_id` or equivalent, settled at TB):

  | Table | Encrypted column | Key id column (proposed) |
  |-------|------------------|--------------------------|
  | `ezkey_enrollment` | `integration_private_key` | `integration_private_key_encryption_key_id` |
  | `ezkey_enrollment` | `enrollment_proof_token` | `enrollment_proof_token_encryption_key_id` |
  | `ezkey_auth_attempt` | `auth_attempt_proof_token` | `auth_attempt_proof_token_encryption_key_id` |
  | `ezkey_api_key` | `secret_key_hash` | `secret_key_hash_encryption_key_id` |
  | ~~`ezkey_auth_attempt`~~ | ~~`device_proof_token`~~ | **Removed from scope** — ADR-0007 / `TB-2026-07-06`: hash-only (`device_proof_token_hash` only, no ciphertext column). |

- **`FOREIGN KEY`** to `ezkey_encryption_key(key_id)` on each column.
- **Composite indexes** `(encryption_key_id, primary_key)` per table/column target used in batch scans.
- **Write path:** set/update key id column whenever `EncryptionService` (or equivalent) writes
  ciphertext with `ENC:{keyId}:` prefix; update on successful re-encrypt row persist.
- **Read path for batches:** refactor `ReencryptionTargetQueryService` and repository native queries
  — replace `*Like` methods with equality on key id column (+ existing cursor, `LIMIT`, shard
  predicates for auth attempts).
- **Tests:** unit/integration coverage for count/fetch parity and write-path maintenance.
- **Docs:** `ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md`, `REENCRYPTION_OPERATIONS.md` — indexed
  discovery as canonical; LIKE examples demoted to historical / ciphertext-format context only.

### Out of scope

- Changing ciphertext format (`ENC:{keyId}:{base64}` stays).
- Hash-based key identification.
- Functional indexes parsing ciphertext prefix.
- Migrating disposable unencrypted test databases.
- Replacing auth-attempt sharding (remains complementary).
- Micrometer / UI changes beyond what existing batch progress already provides.

## Grilling decisions (2026-06-28)

See
[`../grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md`](../grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md).

## Scope amendment (2026-07-26) — fourth target added

During TB promotion, code inspection of `ReencryptionTargetQueryService`,
`KeyUsageVerificationService`, and `ReencryptionBatchCreationService.discoverReencryptableTargets()`
found that `ezkey_api_key.secret_key_hash` is **also** a `LIKE 'ENC:{keyId}:%'` discovery target
(widened to `TEXT` in `V17__api_key_secret_hash_text_for_encryption.sql` precisely to carry the
Tink `ENC:` prefix), tracked through the same `Reencryptable` interface and the same
`EncryptionEntityListener` write path as the enrollment and auth-attempt columns. Neither the
original scope table nor the 2026-06-28 grill session mentioned this column — it was missed when
the idea was first captured.

This does **not** reopen the grill: decisions **G3–G10** (derived `BIGINT` column, FK to
`ezkey_encryption_key`, per-column metadata, composite index, unchanged ciphertext format) apply
uniformly to any encrypted column using the `ENC:{keyId}:` prefix, including `secret_key_hash`. The
scope table above is corrected to list all **four** real targets before TB implementation.

## Requirements (R1 at implementation)

- **R1:** No re-encryption count/fetch uses `LIKE` on ciphertext for key discovery in production
  code paths.
- **R2:** Key id column values match the key id embedded in the ciphertext prefix after every
  encrypt/re-encrypt write.
- **R3:** `KeyUsageVerificationService` / batch creation counts use the same indexed predicate as
  batch fetch.
- **R4:** Flyway schema includes columns, FKs, and indexes before first production install; dev reset
  acceptable, no production backfill migration.

## Promotion notes

- **Promoted:** `2026-07-26` → [`TB-2026-07-26-reencryption-indexed-encryption-key-id-columns.md`](../TB-2026-07-26-reencryption-indexed-encryption-key-id-columns.md), single-pass posture (schema +
  write path + query refactor + tests + docs in one bounded slice, covering all four columns
  including the `secret_key_hash` scope amendment above).
- **Traceability:** linked TB to `F-encryption-key-rotation` spec-test row in Admin API component
  pack ([`product-docs/components/admin-api/spec-test-traceability.md`](../../../components/admin-api/spec-test-traceability.md))
  and the global matrix ([`product-docs/global/spec-test-traceability.md`](../../spec-test-traceability.md)).

## Closeout (2026-07-26)

- **Status:** `done`. All requirements **R1–R4** satisfied; see TB
  [Exit criteria](../TB-2026-07-26-reencryption-indexed-encryption-key-id-columns.md#exit-criteria)
  for the evidence-to-requirement mapping.
- **Evidence:** Maven baseline green (`./scripts/build.sh`, full reactor incl. `ezkey-core` unit
  tests: `EncryptionEntityListenerTest`, `ReencryptionServiceTest` — 452 `ezkey-core` tests, 0
  failures); clean-start functional suite (146 tests, 0 failures, 2 skipped); elective suite (15
  tests, 0 failures, 1 skipped — HA-only `ShedLockDistributedTest` case), including
  `ReencryptionFullTriggerConcurrentActivityElectiveTest` (live key rotation → 4 batches created and
  settled). Post-run DB spot-check on the clean-start stack confirmed **100% population** of all
  four `*_encryption_key_id` columns (176 enrollment rows, 108 auth attempts, 58 API keys) and
  confirmed FK/composite-index presence, including on both `ezkey_auth_attempt` monthly partitions
  (parent-index cascade).
- **Deferred / residual:** none. No production backfill needed (pre-release schema, per operator
  constraint). `NOT NULL` on the new columns intentionally deferred until encryption-required
  becomes mandatory in every supported deployment posture (see TB § Design decision).
- **Docs updated:** `docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md` §4.4.2,
  `docs/REENCRYPTION_OPERATIONS.md` (new §1.2), `docs/ENDPOINT.md` (encryption key object field
  description), `docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md` §5.6.
- **GitHub issue:** none opened (single-pass internal slice, no board visibility need identified).

## Links

- TB: [`TB-2026-07-26-reencryption-indexed-encryption-key-id-columns.md`](../TB-2026-07-26-reencryption-indexed-encryption-key-id-columns.md)
- Grill: [`../grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md`](../grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md)
- Operations: [`../../../docs/REENCRYPTION_OPERATIONS.md`](../../../docs/REENCRYPTION_OPERATIONS.md)
- Rotation design: [`../../../docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md`](../../../docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md)
- Query service: [`../../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java`](../../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java)
- Prior re-encryption UI slice (done): [`I-2026-0002-reencryption-batch-async-button.md`](I-2026-0002-reencryption-batch-async-button.md)
- Release compass: [`../../operational-readiness-prioritization-2026-09.md`](../../operational-readiness-prioritization-2026-09.md)
