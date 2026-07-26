# Tracer Bullet Brief — `TB-2026-07-26-reencryption-indexed-encryption-key-id-columns` Re-encryption: indexed encryption key id columns

## Metadata

- **ID:** `TB-2026-07-26-reencryption-indexed-encryption-key-id-columns`
- **Status:** `done`
- **Related idea:** [`I-2026-0029-reencryption-indexed-encryption-key-id-columns`](ideas/I-2026-0029-reencryption-indexed-encryption-key-id-columns.md)
- **Parent context:** Grill [`2026-06-28-reencryption-key-id-columns-grill-me.md`](grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md) (G1–G14); scope amendment 2026-07-26 (fourth column, `ezkey_api_key.secret_key_hash`)
- **Feature anchor:** `F-encryption-key-rotation`
- **Lane:** `C` (structural hardening, pre-production)
- **Posture:** `single-pass`
- **GitHub issue:** _(none yet — optional, create at implementation start if board visibility helps)_
- **Component tags:** `core`, `admin-api`, `infra`, `docs`
- **Created at:** `2026-07-26`
- **Updated at:** `2026-07-26`
- **Captured by:** Marc (status check + TB promotion session)

## Objective

Replace the four `LIKE 'ENC:{keyId}:%'` re-encryption discovery queries on ciphertext `TEXT`
columns with equality lookups on dedicated, indexed `BIGINT` key-id columns — one per encrypted
field, each with a `FOREIGN KEY` to `ezkey_encryption_key(key_id)` — so batch count, batch fetch,
and `KeyUsageVerificationService` "remaining usage" snapshots stop forcing full-column scans as
`ezkey_enrollment`, `ezkey_auth_attempt`, and `ezkey_api_key` grow. Deliver **before first
production installation**; no production backfill migration for the LIKE-only model (it will never
exist in a production database).

## Why this is safe to promote now

- Grill [`2026-06-28`](grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md) is
  `complete` with no open questions (G1–G14 settled: derived column per field, FK to
  `ezkey_encryption_key`, ciphertext format unchanged, Flyway-baseline delivery, no backfill).
- The `device_proof_token` column already left this scope cleanly via `TB-2026-07-06` (hash-only;
  `ADR-0007`), so that overlap risk is resolved.
- Code audit (2026-07-26) confirms the exact choke points below are small and already isolated
  behind two shared abstractions (`Reencryptable`, `EncryptionEntityListener`) rather than
  scattered across the codebase.

## Evidence summary — current LIKE-based targets (code audit 2026-07-26)

| Table | Column | Native LIKE methods | Widened for `ENC:` prefix |
| --- | --- | --- | --- |
| `ezkey_enrollment` | `integration_private_key` | `EnrollmentRepository.countByEncryptedIntegrationPrivateKeyLike` / `findEncryptedIntegrationPrivateKeyLike` | always `TEXT` |
| `ezkey_enrollment` | `enrollment_proof_token` | `EnrollmentRepository.countByEncryptedEnrollmentProofTokenLike` / `findEncryptedEnrollmentProofTokenLike` | always `TEXT` |
| `ezkey_auth_attempt` | `auth_attempt_proof_token` | `AuthAttemptRepository.countByEncryptedAuthAttemptProofTokenLike` / `findEncryptedAuthAttemptProofTokenLike` (shard-aware) | always `TEXT` |
| `ezkey_api_key` | `secret_key_hash` | `ApiKeyRepository.countByEncryptedSecretKeyHashLike` / `findEncryptedSecretKeyHashLike` | `V17__api_key_secret_hash_text_for_encryption.sql` |

All four are dispatched through **one** switch-based fan-out
([`ReencryptionTargetQueryService`](../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java))
and discovered as a single list via
[`ReencryptionBatchCreationService.discoverReencryptableTargets()`](../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionBatchCreationService.java).
`KeyUsageVerificationService` and `EncryptionKeyMigrationScopeService` both iterate that same
target list and reuse `ReencryptionTargetQueryService.countRecordsEncryptedWithKey`, so refactoring
the query service is sufficient to fix count/fetch/usage-snapshot parity in one place (satisfies
idea requirement **R3**).

**Write path is equally centralized.** Two choke points cover all four columns:

1. **Initial encrypt** — [`EncryptionEntityListener.encrypt()`](../../../ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java)
   (single `@PrePersist`/`@PreUpdate` method, one `encryptField()` call per column across
   `Enrollment`, `AuthAttempt`, `ApiKey`).
2. **Re-encrypt** — [`ReencryptionRecordCipher.reencryptRecord()`](../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionRecordCipher.java),
   which calls `record.setEncryptedField(column, reencrypted)` on the `Reencryptable` entity.

`EncryptionService.parseKeyIdFromPrefix(String)` already exists and is the exact primitive needed
to derive the key id from a written ciphertext value at both choke points — no new parsing logic
required.

## Boundaries in scope

- **Flyway:** add four nullable `BIGINT` columns + `FOREIGN KEY ... REFERENCES ezkey_encryption_key(key_id)`
  + one composite `B-tree` index per column, folded into the existing baseline migrations (edit in
  place per G6 — no new backfill migration):
  - `ezkey_enrollment.integration_private_key_encryption_key_id`
  - `ezkey_enrollment.enrollment_proof_token_encryption_key_id`
  - `ezkey_auth_attempt.auth_attempt_proof_token_encryption_key_id`
  - `ezkey_api_key.secret_key_hash_encryption_key_id`
- **Entity mapping:** add the four `@Column` mappings (`Enrollment`, `AuthAttempt`, `ApiKey`).
- **Write path — initial encrypt:** `EncryptionEntityListener.encryptField()` sets the matching key
  id column immediately after `setFieldValue(entity, persistentFieldName, encrypted)`, using
  `EncryptionOperations.parseKeyIdFromPrefix(encrypted)` (add to the `EncryptionOperations`
  interface if not already exposed there — currently declared on `EncryptionService`).
- **Write path — re-encrypt:** `ReencryptionRecordCipher.reencryptRecord()` sets the key id column
  to `batch.getNewKey().getKeyId()` alongside `record.setEncryptedField(column, reencrypted)` (the
  new key id is already known here — no re-parsing needed).
- **`Reencryptable` interface:** extend the contract so `setEncryptedField` (or a sibling method)
  also receives/derives the key id, so all three entities update both columns atomically in one
  call from the cipher.
- **Read path:** refactor `ReencryptionTargetQueryService` + the four repository `*Like` methods to
  equality predicates on the new column (keep existing cursor/`LIMIT`/shard parameters unchanged
  for `ezkey_auth_attempt`).
- **Tests:** unit coverage for write-path population (initial encrypt + re-encrypt) and
  count/fetch parity on all four targets; keep existing re-encryption integration tests green.
- **Docs:** update `docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md` and
  `docs/REENCRYPTION_OPERATIONS.md` — indexed discovery as canonical; LIKE demoted to historical /
  ciphertext-format context only.

## Out of scope

- Changing the ciphertext format (`ENC:{keyId}:{base64}` stays, per G7).
- `device_proof_token` (already removed from this lane via `TB-2026-07-06` / ADR-0007).
- Hash-based key identification, functional indexes parsing ciphertext, or any SQL-side ciphertext
  transform (G14).
- Replacing auth-attempt sharding (stays complementary per G11).
- Backfilling a production database with legacy LIKE-only rows (does not exist; G1/G2).
- Micrometer or Admin UI changes beyond what current batch progress already exposes.

## Design decision to pin at implementation start

**Nullability of the new key id columns.** The grill (G9) targets `NOT NULL` for the production
path, but `EncryptionEntityListener` can legitimately store **plaintext without an `ENC:` prefix**
when encryption is unavailable and not required (`AtRestEncryptionAccess` fail-open path for
dev/test stacks with `ezkey.encryption.required=false`). A plaintext row has no key id to record.

- **Recommendation:** make the four columns **nullable**. `NULL` means "not currently
  key-id-tracked" (either plaintext-at-rest, or a value written before this column existed —
  N/A here since there is no pre-existing production data). Query semantics are unchanged from
  today: a `LIKE 'ENC:{keyId}:%'` predicate already excludes plaintext rows, and `column_key_id =
  :keyId` does the same. No Flyway seed data hardcodes ciphertext into these tables (verified —
  all rows are written through the JPA/entity-listener path), so every row written with
  encryption **enabled** gets a populated key id column; only encryption-disabled dev stacks would
  ever see `NULL`.
- Revisit `NOT NULL` only if/when encryption-required becomes mandatory in every supported
  deployment posture (out of scope here).

## First executable slice

1. **Flyway:** add the four columns + FKs + composite indexes to the existing baseline (`V1`/`V2`/`V3`
   as appropriate per table — confirm exact migration file per table before editing; this is a
   pre-release schema, no new versioned migration needed per G6).
2. **`EncryptionOperations` interface:** expose `parseKeyIdFromPrefix(String)` (already implemented
   on `EncryptionService`) so `EncryptionEntityListener` can call it without a concrete-class cast.
3. **Entities:** add the four `@Column(name = "..._encryption_key_id")` fields (`Long`) to
   `Enrollment`, `AuthAttempt`, `ApiKey`, with plain getters (no business setters — only the
   listener/cipher write these).
4. **`EncryptionEntityListener.encryptField()`:** after `setFieldValue(entity, persistentFieldName,
   encrypted)` on the success path, parse the key id from `encrypted` and write it to the matching
   key-id field (map `persistentFieldName` → key-id field name per entity, mirroring the existing
   `transientFieldName`/`persistentFieldName` pairs). Leave key id `null` on the plaintext-fallback
   path.
5. **`Reencryptable` / `ReencryptionRecordCipher`:** extend `setEncryptedField` (or add
   `setEncryptionKeyId(String column, Long keyId)`) so `reencryptRecord()` writes
   `batch.getNewKey().getKeyId()` into the key-id column in the same call that updates ciphertext.
6. **Repositories:** replace the eight `*Like` native queries
   (`EnrollmentRepository`, `AuthAttemptRepository` ×1 pair, `ApiKeyRepository`) with equality
   predicates on the new key-id column; keep parameter shapes (`lastId`, `limit`, `shardIndex`,
   `shardCount`) unchanged so `ReencryptionTargetQueryService` call sites need only the SQL swap,
   not signature changes.
7. **`ReencryptionTargetQueryService`:** drop the `"ENC:" + keyId + ":%"` prefix construction;
   pass `keyId` directly to the new equality-based repository methods.
8. **Tests:** update/extend unit tests for `EncryptionEntityListener`, `ReencryptionRecordCipher`,
   `ReencryptionTargetQueryService`, and the four repositories; keep `KeyUsageVerificationService`
   and `EncryptionKeyMigrationScopeService` tests green (they consume the same service, no direct
   change expected).
9. **Docs:** update `ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md` and `REENCRYPTION_OPERATIONS.md`.

## Rollback or fallback posture

- Single Flyway edit + entity/service changes; revert by reverting the commit (pre-release schema,
  no external consumers of the new columns yet).
- No Auth API / Admin API contract change — DTOs and endpoints are unaffected; this is an internal
  discovery-query optimization.

## Critical flows

- **Nominal:** encryption enabled → enrollment/auth-attempt/API-key created → ciphertext + key id
  column both populated → key rotation triggers a batch → batch count/fetch use equality on key id
  → re-encrypt updates ciphertext and key id together → `KeyUsageVerificationService` reports
  `DRAINED` once all four targets report zero rows for the old key.
- **Exception:** encryption disabled (dev/test, `ezkey.encryption.required=false`) → plaintext
  stored, key id column stays `NULL` → discovery queries correctly report zero rows for any key id
  (unchanged from today's `LIKE` behavior on non-`ENC:` values).

## Evidence plan

| Layer | Required |
| --- | --- |
| Unit | `ezkey-core`: `EncryptionEntityListener`, `ReencryptionRecordCipher`, `ReencryptionTargetQueryService`, repository tests for all four columns |
| Integration | Existing re-encryption batch integration tests (count/fetch parity, key rotation end-to-end) stay green with the new predicate |
| Build | Root Maven baseline per `AGENTS.md` (`./scripts/build.sh`) |
| Manual | Clean-start → rotate an encryption key → trigger/observe a re-encryption batch on each of the four targets → confirm `KeyUsageVerificationService` snapshot reaches `DRAINED` |

## Quality gates

- **Analysis gate:** this brief (grill + code audit); no separate design pack needed given the
  single-pass posture and the small, already-isolated choke points.
- **Implementation gate:** Maven baseline + targeted `ezkey-core` tests green.
- **Closeout gate:** `I-2026-0029` marked `done`; docs updated; optional GitHub issue closed if one
  was opened.

## Exit criteria

1. No re-encryption count/fetch/usage-snapshot code path uses `LIKE` on ciphertext for key
   discovery (idea **R1**).
2. Key id column values match the key id embedded in the ciphertext prefix after every
   encrypt/re-encrypt write, across all four targets (idea **R2**).
3. `KeyUsageVerificationService` and batch creation/fetch use the same indexed predicate (idea
   **R3**).
4. Flyway schema includes the four columns, FKs, and indexes before first production install; no
   production backfill migration exists (idea **R4**).
5. Full Maven baseline green; existing re-encryption and key-rotation tests pass unmodified in
   behavior (only predicate mechanism changes).

## Closeout (2026-07-26)

**Exit criteria — evidence:**

1. **No `LIKE` on ciphertext for key discovery (R1):** `ReencryptionTargetQueryService`,
   `EnrollmentRepository`, `AuthAttemptRepository`, `ApiKeyRepository` — all eight `*Like` native
   queries removed; replaced with equality lookups on the four `*_encryption_key_id` columns.
   Verified: no remaining references to the removed method names anywhere in the Java sources.
2. **Key id column matches ciphertext prefix (R2):** `EncryptionEntityListener.encryptField()`
   parses and writes the key id on every initial encrypt; `ReencryptionRecordCipher.reencryptRecord()`
   writes `batch.getNewKey().getKeyId()` on every re-encrypt. Post clean-start DB spot-check: 100%
   of existing rows have a populated key id (176/176 enrollment × 2 columns, 108/108 auth attempts,
   58/58 API keys).
3. **Same indexed predicate for verification and batch ops (R3):** `KeyUsageVerificationService`
   and `EncryptionKeyMigrationScopeService` both consume
   `ReencryptionTargetQueryService.countRecordsEncryptedWithKey`, unchanged call sites — single
   implementation swap satisfies both.
4. **Flyway schema ships before first production install, no backfill (R4):** new migration
   `V19__reencryption_indexed_encryption_key_id_columns.sql` adds all four columns, FKs to
   `ezkey_encryption_key(key_id)`, and composite B-tree indexes. Confirmed via `psql \d` on the
   clean-start stack: columns, FKs, and indexes present on `ezkey_enrollment`, `ezkey_auth_attempt`
   (including local indexes auto-created on both `2026_07`/`2026_08` partitions), and
   `ezkey_api_key`.
5. **Full Maven baseline green; behavior-only predicate change:** `./scripts/build.sh` full reactor
   — `ezkey-core` 452 tests / 0 failures (incl. `EncryptionEntityListenerTest`,
   `ReencryptionServiceTest` — 31 re-encryption unit tests, one new for `secret_key_hash`). Clean-start
   functional suite: 146 tests / 0 failures / 2 skipped. Elective suite: 15 tests / 0 failures / 1
   skipped (HA-only `ShedLockDistributedTest` case), including
   `ReencryptionFullTriggerConcurrentActivityElectiveTest` (live key rotation, 4 batches created and
   settled against the running Postgres stack).

**Deviation from plan:** step 1 said "fold into the existing baseline migrations (edit in place per
G6)". Implementation instead added a new incremental migration (`V19`) after observing the
project's actual Flyway convention (`V16`–`V18` are all new incremental files, not in-place edits
to earlier migrations, even pre-release). Net effect is identical — no production backfill, schema
ready before first install — so this does not reopen any grill decision.

**Docs updated:** `docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md` §4.4.2,
`docs/REENCRYPTION_OPERATIONS.md` (new §1.2 + updated §1, §1.1, §5), `docs/ENDPOINT.md` (encryption
key object field description), `docs/SPEC_ENCRYPTION_KEY_LIFECYCLE_STRATEGY.md` §5.6. Traceability
rows updated in `product-docs/components/admin-api/spec-test-traceability.md` and
`product-docs/global/spec-test-traceability.md` (`F-encryption-key-rotation`).

**Residual risk:** none identified. Nullable key-id columns are an intentional, documented design
choice (not a gap) pending a future decision to make encryption mandatory in every deployment
posture.

**GitHub issue:** none opened — single-pass internal slice, no board visibility need identified.

## Links

- Idea: [`ideas/I-2026-0029-reencryption-indexed-encryption-key-id-columns.md`](ideas/I-2026-0029-reencryption-indexed-encryption-key-id-columns.md)
- Grill: [`grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md`](grill-sessions/2026-06-28-reencryption-key-id-columns-grill-me.md)
- Sibling scope carve-out (done): [`TB-2026-07-06-device-proof-token-hash-only.md`](TB-2026-07-06-device-proof-token-hash-only.md)
- Rotation design: [`../../../docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md`](../../../docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md)
- Operations: [`../../../docs/REENCRYPTION_OPERATIONS.md`](../../../docs/REENCRYPTION_OPERATIONS.md)
- Query service: [`../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java`](../../../ezkey-core/src/main/java/org/ezkey/security/ReencryptionTargetQueryService.java)
- Write path: [`../../../ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java`](../../../ezkey-core/src/main/java/org/ezkey/security/EncryptionEntityListener.java)
