# Backlog Idea — `I-2026-07-22` Tink keyset serialization API migration

## Metadata

- **ID:** `I-2026-07-22-tink-keyset-serialization-api-migration`
- **Status:** `done`
- **Priority:** `P2`
- **Created at:** `2026-07-22`
- **Updated at:** `2026-07-26`
- **Last reviewed at:** `2026-07-26`
- **Progression markers:** `tink-deprecation-migration`, `crypto-at-rest`
- **Component tags:** `core-security`, `crypto`, `docs`
- **Lane:** `D` (security / hardening)
- **Captured by:** Marc
- **Completed at:** `2026-07-26`
- **Assessment trace:** [`docs/security/java-tink-deprecated-api-assessment-2026-07.md`](../../../../docs/security/java-tink-deprecated-api-assessment-2026-07.md)

## Intent

Migrate the encrypted keyset serialization/deserialization path in `TinkKeyManager` away from
Tink's deprecated public `read(...)` / `write(...)` keyset IO surface while preserving the current
Ezkey at-rest encryption model and operational behavior.

This backlog item exists because the remaining deprecated calls are **not** mere formatting debt:

they sit on the platform keyset persistence path and are coupled to the current master-AEAD
encapsulation model.

## Problem and value

- **Problem:** `TinkKeyManager` still uses deprecated Tink keyset IO methods to load and persist the
  encrypted keyset blob on disk. The code currently preserves the intended at-rest encryption model,
  but the public API surface is deprecated and may force future migration pressure.
- **Expected value:**
  - Reduce long-term dependency risk on deprecated Tink APIs.
  - Preserve the encrypted-at-rest keyset model used by Ezkey.
  - Keep the migration explicit, reviewed, and characterized instead of ad hoc.
  - Avoid surprise breakage if future Tink releases tighten or remove the deprecated overloads.

## Scope

### In scope

1. Evaluate the **public** non-deprecated migration path for keyset IO in Tink 1.23+.
2. Preserve encrypted-at-rest keyset handling and the existing master key file semantics.
3. Add or adjust characterization tests around:
   - encrypted keyset load at startup,
   - keyset save after rotation,
   - keyset reload behavior,
   - any failure mode introduced by the new IO path.
4. Update `TinkKeyManager` and any adjacent tests/docs once the replacement path is validated.

### Out of scope

- Algorithm changes (`AES256_GCM`, `CHACHA20_POLY1305`, etc.).
- Key rotation policy redesign.
- Broader crypto-at-rest rearchitecture.
- Any change that would silently move keysets to cleartext persistence.

## Context

The assessment work already confirmed that the remaining deprecated calls are the keyset IO calls:

- `KeysetHandle.read(KeysetReader, Aead)`
- `KeysetHandle.write(KeysetWriter, Aead)`

The same investigation also showed that the obvious `associated data` / `noSecret` overloads in the
public surface are deprecated as well, and the overloads that accept `Configuration` are not public
from our code path. That means this item is a **real migration problem**, not a cosmetic cleanup.

Follow-up inspection on 2026-07-26 found the public configuration-aware replacement surface:

- `TinkJsonProtoKeysetFormat.parseEncryptedKeyset(String, Aead, byte[], Configuration)`
- `TinkJsonProtoKeysetFormat.serializeEncryptedKeyset(KeysetHandle, Aead, byte[], Configuration)`

This path preserves the JSON encrypted-keyset file representation and keeps the current master-AEAD
encryption-at-rest model. The no-`Configuration` variants are also deprecated; Ezkey must use the
explicit `RegistryConfiguration.get()` overloads.

## Questions to resolve before implementation

1. ~~What is the intended public migration path in Tink for encrypted keyset persistence from our
  module boundary?~~ Use `TinkJsonProtoKeysetFormat` encrypted-keyset APIs with explicit
  `RegistryConfiguration.get()`.
2. ~~Can Ezkey keep the current encryption-at-rest model without introducing a new public API leak
  or a semantically weaker storage mode?~~ Yes for the file path: the keyset remains a JSON
  encrypted keyset protected by the existing master AEAD and empty associated data.
3. ~~Should the database `KeysetBlob` codec also move away from deprecated JSON reader/writer
  classes?~~ Yes. The DB blob keeps its current outer `masterAead.encrypt(cleartextJson)` envelope,
  but the cleartext JSON keyset serialization now uses `TinkJsonProtoKeysetFormat`.
4. ~~Should the database `KeysetBlob` representation eventually store Tink's encrypted-keyset
  envelope directly instead of the current outer `masterAead.encrypt(cleartextJson)` wrapper? This
  remains a possible future PoC/design question, not required for the deprecation migration.~~
  Resolved by `I-2026-07-26`: the database blob now stores Tink's encrypted-keyset envelope directly
  as a clean-start pre-production cutover.

## Implementation note (2026-07-26)

- `TinkKeyManager` file load/save now uses the non-deprecated, configuration-aware
  `TinkJsonProtoKeysetFormat` encrypted-keyset APIs.
- The associated data remains empty to preserve the old `KeysetHandle.read/write(..., masterAead)`
  semantics.
- Database `KeysetBlob` load/save keeps the existing outer master-AEAD envelope, but replaces
  deprecated `JsonKeysetReader` / `JsonKeysetWriter` usage with `TinkJsonProtoKeysetFormat` cleartext
  keyset APIs.
- `TinkKeyManagerConcurrencyTest` includes compatibility characterization tests for:
  - legacy encrypted JSON keyset files written with the old API;
  - legacy DB blobs containing outer-encrypted cleartext JSON keysets written with the old API.
- Targeted validation passed with 5 tests:
  `mvn -pl ezkey-core-security -am -Dtest=TinkKeyManagerConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false test`.
- Production deprecation check passed for `ezkey-core-security`:
  `mvn -pl ezkey-core-security -am -DskipTests -Dmaven.compiler.showDeprecation=true clean compile`.
- Formatting and Checkstyle passed (`mvn spotless:apply`, `mvn checkstyle:check`).
- `ApiKeyControllerTest` Mockito/JPA Criteria stubbing was corrected so test compilation stays
  compatible with the full reactor install path.
- Full reactor install passed: `mvn install -DskipTests`.

## Supersession note (2026-08-02)

`I-2026-07-26-tink-native-keyset-blob-envelope` later migrated the database `KeysetBlob` format to
Tink's encrypted-keyset JSON envelope directly. The outer-encrypted DB blob references above are
historical evidence for the 2026-07-26 deprecation migration, not the current storage model.

## Promotion notes

- Closed on this `I-*` because the migration stayed bounded and behavior-preserving. No `TB-*` was
  required.
- A separate PoC/design slice may be opened later if Ezkey wants to change the database keyset blob
  envelope itself, rather than only the deprecated Tink serialization APIs.

## Links

- Assessment: [`docs/security/java-tink-deprecated-api-assessment-2026-07.md`](../../../../docs/security/java-tink-deprecated-api-assessment-2026-07.md)
- Campaign note: [`product-docs/global/hygiene/java-tink-deprecations/2026-07-21-pass-1.md`](../../hygiene/java-tink-deprecations/2026-07-21-pass-1.md)
- Implementation surface: [`ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java`](../../../../ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java)
