# Decision pack — Keyset storage: DB blob as single source of truth

## Metadata

- **Date:** 2026-10-21
- **Status:** proposed (direction adopted on [#952](https://github.com/mgagp/ezkey/issues/952); implementation not started)
- **Scope:** At-rest encryption keyset lifecycle (`TinkKeyManager`, crypto-api, Windows/dev profiles, community host)
- **Related ADR (draft):** [ADR-0016](../../architecture-decisions.md#adr-0016-keyset-blob-single-source-of-truth)
- **GitHub:** [#952](https://github.com/mgagp/ezkey/issues/952) (supersedes paused [#936](https://github.com/mgagp/ezkey/pull/936))
- **Inputs:** Patrick strategy brief; Christophe security Q1/Q2; Marc decision comment on #952
- **Non-goals of this pack:** production Java refactor in this PR; encrypted off-host backup / KMS / HA production ([#953](https://github.com/mgagp/ezkey/issues/953))

## Why this pack exists

[#936](https://github.com/mgagp/ezkey/pull/936) (keyset HA) paused after repeated file↔blob divergence blockers (B2, B5, B6). Marc adopted **option A**: the `ezkey_keyset_blob` row is the only runtime source of truth; FILE and HYBRID modes and flags are removed; crypto-api (local lab only) gets a read-only DataSource on the blob; the eight #936 invariants are ported with mutation-probed tests.

This pack is the **analysis + detailed design + characterization gate** before any production refactor. Characterization tests in `ezkey-core-security` pin current `main` behavior and mark paths expected to change.

## Artefacts

| File | Role |
|------|------|
| [`design.md`](design.md) | Current behavior (path:line), target design, eight invariants → planned tests, removals, doc plan, validation plan, open questions |
| [`community-upgrade-impact.md`](community-upgrade-impact.md) | Dedicated in-place upgrade assessment for the community instance (ezkey.online) |
| [`adr-0016-draft.md`](adr-0016-draft.md) | Short superseding ADR draft (also mirrored as proposed in `architecture-decisions.md`) |

## Process (from #952 decision)

1. This pack + characterization tests (this PR).
2. Adversarial critique of design choices and proportional extra testing.
3. Implementation, validated on Mac → Windows → community instance.

## Provenance

- Issue [#952](https://github.com/mgagp/ezkey/issues/952) decision comment (Marc, 2026-10-21)
- ADR-0008, ADR-0011, ADR-0012 (storage-mode mentions to be superseded)
- `ezkey-core/CONFIGURATION.md` § Encryption at Rest
- `docs/SECURITY_POSTURE.md`, `docs/OPERATIONAL.md`, `docs/KEY_ROTATION_PROCEDURES.md`
