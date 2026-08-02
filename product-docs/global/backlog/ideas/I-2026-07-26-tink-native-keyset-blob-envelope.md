# Backlog Idea — `I-2026-07-26` Tink-native keyset blob envelope

## Metadata

- **ID:** `I-2026-07-26-tink-native-keyset-blob-envelope`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-07-26`
- **Updated at:** `2026-08-02`
- **Last reviewed at:** `2026-08-02`
- **Progression markers:** `crypto-at-rest`, `tink-keyset-storage`, `design-poc`
- **Component tags:** `core-security`, `core`, `admin-api`, `auth-api`, `integration-api`, `infra`, `docs`, `crypto`
- **Lane:** `D` (security / hardening)
- **Captured by:** Marc
- **Related:** [`I-2026-07-22-tink-keyset-serialization-api-migration`](I-2026-07-22-tink-keyset-serialization-api-migration.md), [`I-2026-07-17-keyset-blob-admin-first-bootstrap`](I-2026-07-17-keyset-blob-admin-first-bootstrap.md)
- **Decision:** [`ADR-0011`](../../architecture-decisions.md#adr-0011-tink-native-database-keyset-envelope)
- **Closeout:** [`ML-2026-08-02`](../method-logs/ML-2026-08-02-tink-native-keyset-envelope-closeout.md)

## Outcome

Implemented on 2026-08-02. `ezkey_keyset_blob.keyset_data` now stores Tink's
encrypted-keyset JSON envelope directly, using the existing master AEAD and empty associated data.
The database schema remains `BYTEA`; the runtime keyset, data ciphertext format, algorithms,
rotation owner, and file keyset format are unchanged.

The decision treats Tink envelope metadata such as key IDs and primary-key status as non-secret
operational metadata. This avoids treating the former outer-wrapper opacity as a security control
and avoids adding a second custom envelope that would only move the wrapping-key problem.

Because Ezkey has no production installation to preserve, the cutover is clean-start only for the
database blob format. Legacy outer-encrypted database blobs are intentionally not a compatibility
contract for the new representation; clean deployments should create a fresh `ezkey_keyset_blob`.

Validation evidence:

- `TinkKeyManagerConcurrencyTest` now characterizes the Tink-native DB blob shape, visible envelope
  metadata, Tink parse round-trip, tamper rejection, legacy DB blob cutover posture, and database
  reload after rotation.
- Targeted validation passed:
  `mvn -pl ezkey-core-security -am -Dtest=TinkKeyManagerConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false test`.

## Original Intent

Evaluate whether `ezkey_keyset_blob.keyset_data` should store Tink's encrypted-keyset envelope
directly instead of Ezkey's current outer `masterAead.encrypt(cleartextJsonKeyset)` wrapper, while
preserving the pragmatic goals of the existing design: self-hosted operation, shared master-key
semantics, HA keyset synchronization, and clear Admin-owned key rotation.

This began as a design/PoC idea, not a follow-on implementation requirement for the completed Tink
deprecation migration. The evidence-supported outcome was a bounded clean-start migration to the
Tink-native envelope.

## Problem and value

- **Resolved problem:** After the Tink keyset serialization migration, Ezkey had two behavior-preserving but
  conceptually different persisted keyset envelopes:
  - file storage uses Tink's encrypted-keyset JSON envelope via `TinkJsonProtoKeysetFormat`;
  - database storage kept Ezkey's existing envelope: serialize cleartext keyset JSON, encrypt the
    JSON bytes with `masterAead`, and store the result in `KeysetBlob.keysetData`.
- **Delivered value:** Aligning the database blob to Tink's encrypted-keyset
  envelope improves design quality, future Tink compatibility, and operator/documentation clarity
  before Ezkey reaches a full release.

## Context and historical read

Current evidence suggests this is **not** a brand-new Tink capability. The local Tink 1.23.0 source
for `TinkJsonProtoKeysetFormat` carries a 2022 copyright and exposes public encrypted-keyset
parse/serialize methods. The newer relevance comes from Ezkey's migration away from deprecated
Tink IO APIs and the resulting visibility of Tink's intended keyset-format facade.

Ezkey's historical design appears pragmatic rather than knowingly divergent:

- The original encryption-at-rest design emphasized a file-based master key and encrypted Tink
  keysets for portable, self-hosted operation.
- `KeysetBlob` was introduced later as a centralized database source of truth for distributed / HA
  deployments, with a simple single-row blob encrypted by the same master AEAD.
- The database representation solved the distribution problem before the Tink deprecation pressure
  made the format boundary itself worth revisiting.

The design question is therefore not "fix a weak cryptographic posture". It is whether Ezkey should
now take advantage of its pre-release window to use Tink's keyset concepts more directly and reduce
custom envelope semantics.

## Historical model vs implemented model

| Surface | Historical model | Implemented model |
| --- | --- | --- |
| Keyset file | Tink encrypted-keyset JSON envelope | unchanged |
| Keyset DB blob | `masterAead.encrypt(cleartextJsonKeyset)` | Tink encrypted-keyset JSON envelope bytes |
| Master key | Base64 256-bit file -> `AesGcmJce` master AEAD | unchanged unless a separate design chooses otherwise |
| Data ciphertexts | produced by Tink AEAD from the active `KeysetHandle` | unchanged |
| Rotation owner | Admin-driven keyset mutation | unchanged |
| Peripheral reload | DB version/read path in `DATABASE`/`HYBRID`; Crypto API is currently FILE-only | unchanged unless combined with Admin-first/readiness redesign |

## Scope

### In scope for the future analysis / PoC

1. Compare current DB blob format with Tink-native encrypted-keyset envelope format.
2. Verify what metadata, if any, is visible in Tink's encrypted-keyset envelope and whether that is
   acceptable for Ezkey.
3. Validate compatibility/migration strategies:
   - legacy outer-encrypted DB blob -> candidate reader;
   - candidate encrypted-keyset DB blob -> current and future readers;
   - explicit format version vs auto-detection.
4. Evaluate associated data (`byte[0]` vs contextual AD such as `ezkey:keyset-blob:v1`) and its
   backup/restore implications.
5. Test Admin API rotation, startup, DB reload, and HYBRID behavior with the candidate envelope.
6. Test Auth API and Integration API startup/read-only consumption paths once paired with the
   Admin-first bootstrap/readiness idea.
7. Re-check Crypto API implications. It is currently FILE-only with no database access, so the DB
   envelope change should not alter its daily behavior unless that module later gains DB mode.
8. Identify required updates for configuration docs, operational procedures, Docker/bootstrap
   scripts, database role/grant docs, Postman/demo flows if any operator path changes, and security
   documentation.

### Out of scope for this idea

- Changing data-encryption algorithms (`AES256_GCM`, `CHACHA20_POLY1305`, etc.).
- Changing the root master-key source or introducing an external KMS.
- Multi-tenant or multi-keyset database blobs.
- Changing application ciphertext format or forcing data re-encryption solely because of this
  keyset-blob envelope investigation.
- Implementing the migration inside the completed Tink deprecation PR.

## Key assumptions

- The current outer master-AEAD DB envelope is cryptographically sound if the master key remains
  protected and the AEAD is used correctly.
- The main candidate value is conceptual alignment, future compatibility, and reduced custom format
  semantics, not a presumed emergency security fix.
- A Tink-native encrypted-keyset envelope may expose keyset metadata differently than Ezkey's
  current opaque outer ciphertext. This must be measured before any recommendation.
- Existing clean-start deployments and tests can tolerate a format migration before full release,
  but Ezkey should still design explicit compatibility and rollback behavior.
- Admin-first keyset ownership and peripheral SELECT-only/readiness hardening remain related but
  separable: the envelope format can be evaluated independently, then combined with that design if
  beneficial.

## Hypotheses to validate

| ID | Hypothesis | Disconfirming evidence |
| --- | --- | --- |
| H1 | A Tink-native DB envelope materially simplifies the keyset persistence model. | The candidate adds versioning, metadata exposure, or restore coupling without removing meaningful Ezkey code. |
| H2 | The security posture is at least equivalent to the current outer master-AEAD envelope. | Candidate envelope exposes sensitive metadata, weakens tamper failure behavior, or complicates master-key separation. |
| H3 | Migration can be backward-compatible and bounded. | Legacy blob detection is ambiguous or forces broad startup/ops rewrites. |
| H4 | Runtime behavior for Admin/Auth/Integration remains unchanged after load. | Reload, rotation, or concurrent read behavior changes in `DATABASE` / `HYBRID` mode. |
| H5 | Crypto API is unaffected in its current FILE-only posture. | Any shared configuration or startup path unexpectedly couples Crypto API to DB keyset format. |

## Risks and exceptions

- **Metadata exposure:** Tink encrypted keysets may include visible `keysetInfo` / key IDs / status
  metadata outside the encrypted key material. Ezkey already mirrors some key metadata in
  `ezkey_encryption_key`, but that does not automatically make every envelope-level disclosure
  acceptable.
- **Associated data coupling:** Contextual AD can strengthen binding but can also make backup,
  restore, environment cloning, or HA migration brittle if it includes unstable values.
- **Format ambiguity:** Auto-detecting old vs new blobs must be deterministic. If not, add an
  explicit format/version column or migration marker before changing storage.
- **Bootstrap ownership:** If combined with Admin-first keyset blob materialization, avoid
  reintroducing peripheral writer behavior or silent startup fallback.
- **Operational churn:** Scripts, docs, Docker volumes, DB grants, and diagnostic procedures may need
  alignment if the DB blob stops being described as an Ezkey outer-encrypted cleartext JSON keyset.
- **Over-correction:** A purer Tink model is not valuable if it reduces operator clarity or makes
  recovery harder with little practical gain.

## Candidate PoC shape

1. Add a test-only adapter/prototype that serializes DB keyset blobs as Tink encrypted-keyset JSON
   using the existing `masterAead` and empty associated data.
2. Compare byte/JSON shape against the current DB blob after decrypting the outer envelope in a test
   fixture.
3. Add characterization tests for:
   - legacy blob read;
   - candidate blob read;
   - rotation save + reload;
   - tampered blob;
   - wrong master key;
   - optional associated data mismatch.
4. Run an Admin API rotation path and confirm `ezkey_encryption_key` metadata synchronization remains
   unchanged.
5. Run Auth/Integration startup in `DATABASE` mode against a candidate blob.
6. Confirm Crypto API remains unaffected in FILE mode.
7. Produce a design recommendation: keep status quo, migrate now, or park until Admin-first
   bootstrap/readiness work.

## Promotion notes

- Keep this idea `incubating` until the metadata exposure and compatibility questions are answered
  with a small PoC or source-level analysis.
- Promote to `ready` only when the preferred outcome is clear enough for a bounded tracer bullet.
- If the decision is **statu quo**, close this idea with the evidence and update `KeysetBlob` /
  `CONFIGURATION.md` docs to explain why the Ezkey outer envelope remains intentional.
- If the decision is **migrate**, create a `TB-*` that includes explicit rollback / compatibility
  behavior and coordinates with `I-2026-07-17-keyset-blob-admin-first-bootstrap` where relevant.

## Links

- Completed deprecation migration: [`I-2026-07-22-tink-keyset-serialization-api-migration`](I-2026-07-22-tink-keyset-serialization-api-migration.md)
- Admin-first keyset ownership follow-up: [`I-2026-07-17-keyset-blob-admin-first-bootstrap`](I-2026-07-17-keyset-blob-admin-first-bootstrap.md)
- Current implementation surface: [`../../../../ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java`](../../../../ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java)
- DB entity: [`../../../../ezkey-core/src/main/java/org/ezkey/security/domain/entity/KeysetBlob.java`](../../../../ezkey-core/src/main/java/org/ezkey/security/domain/entity/KeysetBlob.java)
- Configuration docs: [`../../../../ezkey-core/CONFIGURATION.md`](../../../../ezkey-core/CONFIGURATION.md), [`../../../../ezkey-crypto-api/CONFIGURATION.md`](../../../../ezkey-crypto-api/CONFIGURATION.md)