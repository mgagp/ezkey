# Method log — `ML-2026-08-02-tink-native-keyset-envelope-closeout`

## Metadata

- **Date:** `2026-08-02`
- **Slice:** Tink-native database keyset envelope
- **Idea:** [`I-2026-07-26-tink-native-keyset-blob-envelope`](../ideas/I-2026-07-26-tink-native-keyset-blob-envelope.md)
- **ADR:** [`ADR-0011`](../../architecture-decisions.md#adr-0011-tink-native-database-keyset-envelope)
- **Related:** [`I-2026-07-22-tink-keyset-serialization-api-migration`](../ideas/I-2026-07-22-tink-keyset-serialization-api-migration.md), [`ADR-0008`](../../architecture-decisions.md#adr-0008-tink-keyset-sync-concurrent-read-path)

## Summary

Closed the remaining Tink keyset envelope design question. Ezkey now stores
`ezkey_keyset_blob.keyset_data` as Tink's encrypted-keyset JSON envelope directly, using the existing
master AEAD and empty associated data. The prior Ezkey-specific database wrapper
(`masterAead.encrypt(cleartextJsonKeyset)`) is historical only.

The decision is security-aligned because Ezkey treats key material as secret and Tink envelope
metadata such as key IDs and primary-key status as non-secret operational metadata. The former outer
wrapper's extra opacity is not credited as a security control, and no second custom envelope was
added solely to hide non-secret metadata.

## Delivered

1. `TinkKeyManager` database load/save path now uses `TinkJsonProtoKeysetFormat` encrypted-keyset
   parse/serialize APIs with `RegistryConfiguration.get()`.
2. `TinkKeyManagerConcurrencyTest` characterizes the new DB blob shape, visible metadata, Tink
   parse round-trip, tamper rejection, legacy DB blob clean-start posture, and reload after rotation.
3. `KeysetBlob`, configuration docs, SQL comments, and Spring profile docs now describe the Tink
   encrypted-keyset envelope representation.
4. Global design canon records the decision in ADR-0011 and links it from feature and traceability
   surfaces.
5. Older Tink deprecation artifacts and historical implementation plans carry supersession pointers
   so cold agents do not treat obsolete cleartext/outer-wrapper notes as current state.

## Validation

- Targeted: `mvn -pl ezkey-core-security -am -Dtest=TinkKeyManagerConcurrencyTest -Dsurefire.failIfNoSpecifiedTests=false test` — 7 tests passed.
- Baseline: `mvn spotless:apply`, `mvn checkstyle:check`, `mvn clean`, `mvn install -DskipTests` — passed.
- Operational: `ezkey-tests/clean-start.sh --no-proxy` — Docker stack healthy.
- Database inspection: clean-start `ezkey_keyset_blob` contained Tink JSON envelope fields
  `encryptedKeyset` and `keysetInfo`.
- Live smoke: `mvn test -pl ezkey-tests -P smoke-tests` — 12 tests passed.

## Closeout actions

1. `I-2026-07-26` → `done`.
2. `product-docs/global/backlog/index.md` row updated to `done`.
3. `architecture-decisions.md` → ADR-0011 added.
4. `features-and-phases.md` and `spec-test-traceability.md` now point to ADR-0011.
5. `docs/SECURITY_POSTURE.md` states the current Tink keyset security position in plain language.
6. `AGENTS.md` cold-start domain pointer table includes at-rest encryption / Tink keyset storage.

## Residual

- Admin-first keyset blob ownership and peripheral SELECT-only hardening remain a separate follow-up:
  [`I-2026-07-17-keyset-blob-admin-first-bootstrap`](../ideas/I-2026-07-17-keyset-blob-admin-first-bootstrap.md).
- Contextual associated data for the DB blob is deliberately deferred; empty associated data
  preserves existing Tink semantics and avoids backup / restore brittleness.
- No legacy dual-read migration is carried because the cutover was accepted as clean-start before
  production installations exist.