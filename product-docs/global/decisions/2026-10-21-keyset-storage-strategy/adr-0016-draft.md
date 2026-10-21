# ADR-0016 draft — Keyset blob as single source of truth

> Canonical copy for review. When accepted, this body is appended to
> [`architecture-decisions.md`](../../architecture-decisions.md) and this file remains a pointer.

## Metadata

- **ID:** ADR-0016.
- **Date:** 2026-10-21.
- **Status:** proposed (direction adopted on GitHub [#952](https://github.com/mgagp/ezkey/issues/952); code cutover pending).
- **Scope:** global.
- **Owners:** Platform architecture / security.

## Context

At-rest encryption uses a Tink keyset wrapped by a file-based master key. ADR-0008, ADR-0011, and ADR-0012 retained three storage modes (`FILE`, `DATABASE`, `HYBRID`) and, in `DATABASE`/`HYBRID`, a keyset **file cache** beside `ezkey_keyset_blob`. Multi-instance hardening (#936) repeatedly hit divergence between file and blob (blockers B2, B5, B6). Real deployments already run `DATABASE`; `HYBRID` has no production user. Keeping dual authority is accidental complexity.

## Decision

1. **`ezkey_keyset_blob` is the only runtime source of truth** for the Tink keyset. API processes do not read, write, or delete a keyset file.
2. **Remove `FILE` and `HYBRID`** modes and related flags. Windows and crypto-api align on database-backed load. `master.key` remains a file/secret.
3. **crypto-api** runs **only** in the disposable local clean-start stack (Marc deployment model 1, 2026-10-21). It uses a read-only DataSource and a SELECT-only DB role on the blob; AEAD recovery reloads the blob; split health; never `ezkey.encryption.required=false` ([#942](https://github.com/mgagp/ezkey/issues/942) property purge is adjacent).
4. **Online rotation** remains, **DB-CAS-first** and fail closed. Invariants 1–3 are a **new CAS protocol** inspired by #936 (install only after successful CAS). Invariants 4–8 carry from that line (short tick; unknown kid fail closed; DB errors never generate; no DELETE/TRUNCATE of the blob / no API unlink of key material / disable-kid does not delete the envelope; legacy blob fails startup without overwrite).
5. **Decrypt paths fail closed** (fix `EncryptionService.decrypt` returning ciphertext as plaintext) in the cutover PR. Logs carry **kids** (and versions) only. `audit-hmac.key` is mode `600` on POSIX. Secrets volumes keep **master + audit keys only** (no keyset-file authority).
6. Supersedes storage-mode dual-authority statements in ADR-0008, ADR-0011, and ADR-0012. Encrypted off-host backup, KMS, and production HA remain [#953](https://github.com/mgagp/ezkey/issues/953). [#942](https://github.com/mgagp/ezkey/issues/942) property purge and CLI import/export are adjacent thin PRs.

## Alternatives Considered

- **Keep three modes with stricter file rules (finish #936 as-is).** Rejected — recurring review cost; HYBRID unused; blockers are conceptual.
- **File-only + orchestrator secrets.** Rejected — loses hot rotation and Admin promote without a large ops rewrite.
- **KMS envelope now.** Deferred — extension of blob-only once #953 funds it; self-host needs local fallback anyway.
- **D′ keep exclusive FILE for single-instance.** Rejected unless a real offline-without-Postgres need appears; Windows already needs Postgres for the app.

## Consequences

- **Positive.** Single authority; B2/B5/B6 class bugs disappear by construction; backup mental model is DB + master key; crypto-api shares the same load path.
- **Negative.** No keyset-file cache as informal backup; crypto-api requires Postgres even in lab; community leftover file must be handled deliberately on upgrade.
- **Neutral.** Master key remains on disk/volume until a future KMS ADR.

## Impact

- `ezkey-core-security` (`TinkKeyManager`, `EncryptionService`), Admin/Auth/Integration/crypto-api config, Docker HA volume story, Windows profiles, grants matrix, operational and security docs.
- Decision pack: [`../2026-10-21-keyset-storage-strategy/`](../2026-10-21-keyset-storage-strategy/README.md).
- Issues: [#952](https://github.com/mgagp/ezkey/issues/952), related [#942](https://github.com/mgagp/ezkey/issues/942), [#953](https://github.com/mgagp/ezkey/issues/953); supersedes [#936](https://github.com/mgagp/ezkey/pull/936).

## Validation

- Characterization tests pin pre-refactor behavior (this pack’s PR).
- Implementation PR: mutation-probed tests for all eight invariants; Mac → Windows → community in-place upgrade (`community-upgrade-impact.md`).

## Related Decisions

- Supersedes dual-authority / FILE-cache implications in [ADR-0008](../../architecture-decisions.md#adr-0008-tink-keyset-sync-concurrent-read-path), [ADR-0011](../../architecture-decisions.md#adr-0011-tink-native-database-keyset-envelope), [ADR-0012](../../architecture-decisions.md#adr-0012-admin-owned-keyset-materialization).
- Complements always-on encryption work in [#942](https://github.com/mgagp/ezkey/issues/942).
