# Decision pack — Keyset storage: DB blob as single source of truth

## Metadata

- **Date:** 2026-10-21
- **Status:** implementation in progress / cutover PR ([#952](https://github.com/mgagp/ezkey/issues/952)); ADR-0016 **accepted**; open questions 1–7 **DECIDED** 2026-10-21
- **Scope:** At-rest encryption keyset lifecycle (`TinkKeyManager`, crypto-api, Windows/dev profiles, community host)
- **Related ADR:** [ADR-0016](../../architecture-decisions.md#adr-0016-keyset-blob-single-source-of-truth) (accepted; draft file is a pointer)
- **GitHub:** [#952](https://github.com/mgagp/ezkey/issues/952) (supersedes paused [#936](https://github.com/mgagp/ezkey/pull/936))
- **Inputs:** Patrick strategy brief; Christophe security Q1/Q2; Marc decision comment on #952
- **Non-goals of this pack:** encrypted off-host backup / KMS / HA production ([#953](https://github.com/mgagp/ezkey/issues/953)); `#942` property purge and CLI import/export (adjacent thin PRs)

## Why this pack exists

[#936](https://github.com/mgagp/ezkey/pull/936) (keyset HA) paused after repeated file↔blob divergence blockers (B2, B5, B6). Marc adopted **option A**: the `ezkey_keyset_blob` row is the only runtime source of truth; FILE and HYBRID modes and flags are removed; crypto-api (local lab only) gets a read-only DataSource on the blob. Invariants **4–8** carry from the #936 line; invariants **1–3** are a **new CAS protocol** inspired by #936 (not a literal port), each with mutation-probed tests.

This pack is the **analysis + detailed design + characterization gate**. The cutover PR lands CAS + decrypt fail-closed + mutation-probed invariants + documentation honesty.

## Artefacts

| File | Role |
|------|------|
| [`design.md`](design.md) | Current behavior (path:line), target design, eight invariants → planned tests, removals, doc plan, validation plan, decisions (§8) |
| [`community-upgrade-impact.md`](community-upgrade-impact.md) | Dedicated in-place upgrade assessment for the community instance (`<static-ip ezkey-online-community>`) |
| [`adr-0016-draft.md`](adr-0016-draft.md) | Pointer to the accepted ADR-0016 body in `architecture-decisions.md` |

## Process (from #952 decision)

1. Pack + characterization tests (landed).
2. Adversarial critique of design choices and proportional extra testing (landed).
3. Implementation cutover (CAS, fail-closed decrypt, invariants, docs) — **in progress**; validate Mac → Windows → community instance.

## Provenance

- Issue [#952](https://github.com/mgagp/ezkey/issues/952) decision comment (Marc, 2026-10-21)
- ADR-0008, ADR-0011, ADR-0012 (storage-mode dual-authority sentences superseded by ADR-0016)
- `ezkey-core/CONFIGURATION.md` § Encryption at Rest
- `docs/SECURITY_POSTURE.md`, `docs/OPERATIONAL.md`, `docs/KEY_ROTATION_PROCEDURES.md`
