# Tracer Bullet Brief — `TB-2026-08-28-admin-first-keyset-bootstrap` Admin-first keyset bootstrap

## Metadata

- **ID:** `TB-2026-08-28-admin-first-keyset-bootstrap`
- **Status:** `implemented`
- **Related idea:** `I-2026-07-17-keyset-blob-admin-first-bootstrap`
- **Created at:** `2026-08-28`
- **Updated at:** `2026-08-28`
- **Captured by:** Marc

## Objective

Prove that Admin API is the sole writer of `ezkey_keyset_blob` and `ezkey_encryption_key` metadata,
that Auth/Integration wait or fail-closed without attempting INSERT, and that grants match that
story — including a deterministic Auth-first repro, not a lucky clean-start.

## Boundaries in scope

- `TinkProperties.Keyset.writer` / `bootstrap-wait` / poll interval
- `TinkKeyManager.initialize()` peripheral wait; no generate/upsert when not writer
- `KeyRotationService.initializeKeysetSync()` skip when not writer
- `apply-grants.sql` + matrix + `verify-grants.sh` SELECT-only blob for peripherals
- Compose Auth `depends_on` Admin healthy as acceleration only
- Layer 1 unit locks + `scripts/repro-auth-keyset-readiness.sh`

## Out of scope

- Crypto API FILE-only path
- Master-key generation
- Multi-tenant keysets
- Audit-chain heartbeat

## First executable slice

Writer gate + DATABASE/HYBRID wait + grant tighten + unit tests + Auth-first script, on one PR.

## Rollback or fallback posture

Revert the Java gate and restore peripheral INSERT/UPDATE on `ezkey_keyset_blob` only together.
Do not restore peripheral writers without the wait path.

## Critical flows

- Nominal: Admin materializes blob + metadata; Auth/Integration load SELECT-only.
- Auth starts first, empty blob, Admin arrives inside `bootstrap-wait`: Auth becomes ready.
- Auth starts first, Admin never arrives, `required=true`: fail-closed, no `permission denied`,
  no `UnexpectedRollbackException`.

## Evidence plan

- Unit: non-writer never `save` on empty-table sync; Admin writer still saves in TX; Tink
  non-writer never upserts/generates; wait-then-load via stub that appears on the second poll.
  Ran 2026-08-28 via `./scripts/build.sh` (`KeyRotationServiceKeysetWriterTest`,
  `TinkKeyManagerAdminFirstBootstrapTest`).
- `./scripts/repro-auth-keyset-readiness.sh` (TB closeout, not CI): negative passed (Auth-first,
  no `permission denied` / `UnexpectedRollbackException`); positive passed after Auth-only
  health wait (`--no-deps` Admin so migration does not burn the wait window).
- `./scripts/db/verify-grants.sh --docker` passed 2026-08-28 after `apply-grants.sh --docker`.
- Clean-start remains smoke only.

## Quality gates

- Grill locks recorded on the idea and ADR-0012
- Implementation matches writer + wait + grants in one slice
- Forced Auth-first proof, not race-hopeful compose up

## Exit criteria

Auth-first empty-blob run does not INSERT and does not die with `UnexpectedRollbackException`;
Admin-first materialization still seeds both representations; grant script denies peripheral
blob writes.
