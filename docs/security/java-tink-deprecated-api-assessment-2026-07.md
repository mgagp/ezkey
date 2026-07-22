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
| TINK-DEP-003 | Deprecated API leak in manager public contract | P2 | High | `TinkKeyManager.getKeysetInfo()` returns deprecated proto-backed info | 1 method contract | Introduce non-deprecated DTO/snapshot API; keep compatibility shim temporarily | Medium |

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

- Public method `TinkKeyManager.getKeysetInfo()` exposes a deprecated API shape upstream.

Risk:

- Keeps callers coupled to deprecated/proto-oriented contract.
- Increases future migration cost and may spread additional deprecated usages.

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
3. TINK-DEP-003 (P2) - contract cleanup for outward-facing `getKeysetInfo()`.

## Open questions for decision

1. Should `TINK-DEP-003` be done now (same pass) or deferred to avoid API ripple?
2. For `TINK-DEP-002`, do we accept a small internal helper abstraction to keep the code readable?
3. Do we gate all three items behind one PR, or split by risk (P1 first, P2 later)?
