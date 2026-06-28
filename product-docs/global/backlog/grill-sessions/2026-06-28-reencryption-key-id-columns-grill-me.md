# Grill Me — 2026-06-28 (Re-encryption: indexed encryption key id columns)

## Session control

| Field | Value |
|-------|--------|
| **Backlog** | `I-2026-0029-reencryption-indexed-encryption-key-id-columns` |
| **Feature anchor** | `F-encryption-key-rotation` |
| **Lane** | `C` (structural hardening, pre-production) |
| **Status** | `complete` |
| **Date** | `2026-06-28` |
| **Captured by** | Marc |

## Context (observed)

- Re-encryption batch **count** and **fetch** use native SQL `LIKE 'ENC:{keyId}:%'` on four TEXT
  columns (`ezkey_enrollment`: `integration_private_key`, `enrollment_proof_token`;
  `ezkey_auth_attempt`: `auth_attempt_proof_token`, `device_proof_token`).
- No index supports prefix lookup on ciphertext blobs → full-table scans as row counts grow.
- Mitigations already shipped or planned (cursor + `LIMIT`, throttling, auth-attempt **sharding**,
  async Admin UI triggers) improve throughput but do **not** remove the scan cost.
- The ciphertext prefix `ENC:{keyId}:` remains useful for **decryption routing** and audit; the
  problem is using it as the **query predicate** for batch discovery.
- Ezkey has **no production release** today; first production install is still ahead.

## Settled decisions (operator-confirmed 2026-06-28)

| ID | Decision |
|----|----------|
| G1 | **Pre-release only:** implement before first production installation. **No** migration path from a production DB on the current LIKE-based discovery model — that state will never exist in production. |
| G2 | **No unencrypted → encrypted upgrade path:** databases created or run without field encryption (disposable test DBs) are out of scope. Ezkey does not support promoting a non-encrypted database to encrypted-at-rest in place. |
| G3 | **Solution direction:** add an explicit **derived column per encrypted field** holding the Tink key id (`BIGINT`), maintained on encrypt and re-encrypt. Replace `LIKE` predicates with **equality** on that column. |
| G4 | **Referential integrity:** use **`FOREIGN KEY` to `ezkey_encryption_key(key_id)`**, not a hash and not a free-floating integer. Keys are lifecycle-managed in the same database and are not deleted. |
| G5 | **Per-column metadata:** each of the four encrypted columns gets its own `*_encryption_key_id` column (same row may hold different key ids per column after partial migration). |
| G6 | **Schema delivery:** fold columns + composite indexes into the **existing Flyway baseline** (edit/create in place for pre-release). **No** one-shot backfill migration from legacy LIKE-only rows for production — only dev DB reset / clean-start. |
| G7 | **Ciphertext format unchanged:** keep storing `ENC:{keyId}:{base64}` in TEXT; the new column is **query metadata**, not a replacement for the prefix inside ciphertext. |
| G8 | **Legacy `ENC:` without key id:** not a production concern; pre-release code paths assume encryption-at-rest with key id in prefix. No dual-mode read path required for plaintext or legacy-only ciphertext in production posture. |
| G9 | **NOT NULL on production path:** once implemented, encrypted columns always have a populated key id column on write; nullable column acceptable only if needed transiently during Flyway ordering — target state is NOT NULL for encrypted fields. |
| G10 | **Indexing:** composite B-tree per target, e.g. `(integration_private_key_encryption_key_id, enrollment_id)` and `(auth_attempt_proof_token_encryption_key_id, auth_attempt_id)` (and shard-friendly predicates unchanged). |
| G11 | **Complementary work:** auth-attempt **sharding** (`mod(auth_attempt_id, N)`) stays; it parallelizes work — indexed key id removes scan cost within each shard. |
| G12 | **Priority:** `P2` / `P2-hardening`. Not on the September 2026 integrity-cluster critical path, but **must land before first production install** (operator intent). |
| G13 | **Docs:** update `docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md` and `docs/REENCRYPTION_OPERATIONS.md` to describe indexed columns as the canonical discovery mechanism (deprecate LIKE as the recommended query pattern). |
| G14 | **Out of scope:** changing Tink/keyset design; SQL-side ciphertext transform; hash-based key identification; retrofit of disposable unencrypted test databases. |

## Rejected alternatives (brief)

| Alternative | Why rejected |
|-------------|--------------|
| Keep `LIKE` + DB tuning only | Latent full-table scan debt; sharding mitigates parallelism, not predicate cost. |
| Functional index on `substring(ciphertext …)` | Parses large TEXT on every write; fragile for format edge cases; accidental complexity vs explicit column. |
| Hash of key id | No benefit over indexed `BIGINT`; hash columns in Ezkey serve **secret equality**, not key routing. |

## Open questions

_None — grill complete for backlog instantiation._

## Outcome

Grill complete. Idea **`I-2026-0029`** instantiated at status **`ready`** (design direction and
pre-release constraints settled; promotion to **`TB-*`** when implementation slot is chosen).

## Links

- [`../ideas/I-2026-0029-reencryption-indexed-encryption-key-id-columns.md`](../ideas/I-2026-0029-reencryption-indexed-encryption-key-id-columns.md)
- [`../../../docs/REENCRYPTION_OPERATIONS.md`](../../../docs/REENCRYPTION_OPERATIONS.md)
- [`../../../docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md`](../../../docs/ENCRYPTION_KEY_ROTATION_IMPLEMENTATION.md)
- Feature: [`../../features-and-phases.md`](../../features-and-phases.md) (`F-encryption-key-rotation`)
