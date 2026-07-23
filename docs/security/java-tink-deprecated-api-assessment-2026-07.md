# Java Tink Deprecated API Assessment (2026-07)

## Mandate

- Surface: Java key management subsystem using Google Tink, primarily `ezkey-core-security`.
- Attention axes: identify deprecated Tink methods currently invoked; map replacement APIs/mechanisms.
- Non-goals: behavior changes, broad refactors, risky crypto-flow rewrites, release destabilization.

## Scope and method

- Static white-box review of Tink integration code.
- Verification of deprecation status against the actual dependency in use (`com.google.crypto.tink:tink:1.23.0`).
- Compiler-based deprecation confirmation with `javac -Xlint:deprecation` snippets.

## Surface map

Primary implementation file:

- `ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java`

Tink version declarations:

- `ezkey-core/pom.xml` -> `com.google.crypto.tink:tink:1.23.0`
- `ezkey-core-security/pom.xml` -> `com.google.crypto.tink:tink:1.23.0`

## Findings register

| ID | Title | Severity | Confidence | Deprecated API in use | Deprecated usage count | Replacement direction | Quick-win potential |
| --- | --- | --- | --- | --- | ---: | --- | --- |
| TINK-DEP-001 | Deprecated primitive acquisition path | P1 | High | `KeysetHandle.getPrimitive(Class<P>)` | 2 | `KeysetHandle.getPrimitive(RegistryConfiguration.get(), Class<P>)` | High |
| TINK-DEP-002 | Deprecated keyset metadata access path | P1 | High | `KeysetHandle.getKeysetInfo()` | 14+ | `getPrimary().getId()`, `size()`, `getAt(i).getId()`, `getAt(i).isPrimary()` | Medium |
| TINK-DEP-003 | Deprecated keyset IO path still used by `TinkKeyManager` | P2 | High | `KeysetHandle.read(KeysetReader, Aead)` / `KeysetHandle.write(KeysetWriter, Aead)` | 2 call sites | Defer to backlog item `I-2026-07-22-tink-keyset-serialization-api-migration` | Low (analysis complete; implementation blocked on Tink public surface) |

## Evidence

### TINK-DEP-001

Current usage:

- `getAeadPrimitive()` uses `keysetHandle.getPrimitive(Aead.class)`.
- `verifyKeyset()` uses `keysetHandle.getPrimitive(Aead.class)`.

Deprecation verification:

- `javac -Xlint:deprecation` warns: `getPrimitive(Class<P>) in KeysetHandle has been deprecated`.

Replacement validated:

- `keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead.class)` compiles without deprecation warning.

### TINK-DEP-002

Current usage:

- Primary key reads through `keysetHandle.getKeysetInfo().getPrimaryKeyId()`.
- Key enumeration and lookup through `getKeysetInfo().getKeyInfoList()` + `getKeyId()`.
- Several sections currently carry `@SuppressWarnings("deprecation")` around this flow.

Deprecation verification:

- `javac -Xlint:deprecation` warns: `getKeysetInfo() in KeysetHandle has been deprecated`.

Replacement validated:

- `keysetHandle.getPrimary().getId()` for primary key.
- Iteration via `for (int i = 0; i < handle.size(); i++) { handle.getAt(i).getId(); }` compiles without deprecation warning.

### TINK-DEP-003

Current usage:

- `TinkKeyManager` still calls the deprecated public keyset IO surface for encrypted keyset
  persistence: `KeysetHandle.read(KeysetReader, Aead)` and `KeysetHandle.write(KeysetWriter, Aead)`.

Analysis result:

- The obvious public alternatives in `KeysetHandle` are also deprecated in 1.23.0.
- The `Configuration`-accepting overloads exist but are not public from our module boundary.
- The remaining work is therefore a **real migration problem**, not a mechanical quick win.

Disposition:

- Deferred to backlog item `I-2026-07-22-tink-keyset-serialization-api-migration`.

## Not deprecated in this pass (checked)

- `AeadConfig.register()`
- `KeysetManager.withKeysetHandle(...).add(...).setPrimary(...)`
- `CleartextKeysetHandle.read(...)` / `CleartextKeysetHandle.write(...)`

## Risk and stability posture

- Crypto behavior must remain unchanged.
- Target only call-shape migration to supported APIs.
- No change in algorithm, key material format, storage mode, or rotation semantics.

## Proposed corrective plan (quick wins first)

1. QW-1 (low risk): replace `getPrimitive(Aead.class)` with explicit configuration variant.
2. QW-2 (medium risk): replace `getKeysetInfo()`-based key-id reads with Entry-based APIs.
3. QW-3 (medium risk): add a new non-deprecated keyset snapshot method in `TinkKeyManager`; keep old method as transitional shim.

## Characterization and regression shield

Recommended minimum before/with fixes:

- Characterization tests around:
  - `getCurrentPrimaryKeyId()` equality before/after migration.
  - key rotation (`rotateKey`) still returns expected new primary key semantics.
  - add/promotion paths (`addKeyWithoutPromotion`, `promoteToPrimary`) preserve behavior.
- Keep existing concurrency and required-encryption tests green.

## Priority lot for HITL

1. TINK-DEP-001 (P1) - primitive acquisition migration.
2. TINK-DEP-002 (P1) - keyset info migration in rotation and lookup paths.
3. TINK-DEP-003 (P2) - keyset IO migration in `TinkKeyManager` (deferred to backlog).

## Open questions for decision

1. What is the intended public migration path for encrypted keyset persistence in Tink?
2. Can Ezkey preserve the current at-rest encryption model without introducing a weaker storage mode?
3. If no clean public replacement exists, do we pin Tink, wrap the persistence path, or take a design-pack slice first?
