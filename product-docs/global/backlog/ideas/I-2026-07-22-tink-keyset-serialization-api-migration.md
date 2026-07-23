# Backlog Idea — `I-2026-07-22` Tink keyset serialization API migration

## Metadata

- **ID:** `I-2026-07-22-tink-keyset-serialization-api-migration`
- **Status:** `incubating`
- **Priority:** `P2`
- **Created at:** `2026-07-22`
- **Updated at:** `2026-07-22`
- **Last reviewed at:** `2026-07-22`
- **Progression markers:** `tink-deprecation-migration`, `crypto-at-rest`
- **Component tags:** `core-security`, `crypto`, `docs`
- **Lane:** `D` (security / hardening)
- **Captured by:** Marc
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

## Questions to resolve before implementation

1. What is the intended public migration path in Tink for encrypted keyset persistence from our
   module boundary?
2. Can Ezkey keep the current encryption-at-rest model without introducing a new public API leak or
   a semantically weaker storage mode?
3. If the answer is “not cleanly”, do we pin the current Tink version and document the exception, or
   do we design a wrapper/adapter layer in Ezkey for the persistence path?

## Promotion notes

- This item should stay `incubating` until a non-deprecated, public, and behavior-preserving path is
  identified.
- Promote to `ready` when the replacement mechanism is chosen and the change slice is small enough
  for a bounded PR.
- If the investigation reveals a genuine design gap in Tink's public surface, this item may need a
  short design-pack / tracer-bullet step before implementation.

## Links

- Assessment: [`docs/security/java-tink-deprecated-api-assessment-2026-07.md`](../../../../docs/security/java-tink-deprecated-api-assessment-2026-07.md)
- Campaign note: [`product-docs/global/hygiene/java-tink-deprecations/2026-07-21-pass-1.md`](../../hygiene/java-tink-deprecations/2026-07-21-pass-1.md)
- Implementation surface: [`ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java`](../../../../ezkey-core-security/src/main/java/org/ezkey/security/TinkKeyManager.java)
