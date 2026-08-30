# Backlog Idea — `I-2026-07-17` Keyset blob: Admin-first bootstrap before peripheral SELECT-only

## Metadata

- **ID:** `I-2026-07-17-keyset-blob-admin-first-bootstrap`
- **Status:** `ready`
- **Priority:** `P1`
- **Created at:** `2026-07-17`
- **Updated at:** `2026-08-28`
- **Last reviewed at:** `2026-08-28`
- **Phase tags:** `crypto-at-rest`, `ops-ordering`
- **Component tags:** `core`, `core-security`, `admin-api`, `auth-api`, `integration-api`, `infra`, `docs`, `crypto`
- **Lane:** `C`
- **Captured by:** Marc (operator review of PostgreSQL role grants on `ezkey_keyset_blob`)
- **Decision:** [`ADR-0012`](../../architecture-decisions.md#adr-0012-admin-owned-keyset-materialization)
- **Tracer bullet:** [`../TB-2026-08-28-admin-first-keyset-bootstrap.md`](../TB-2026-08-28-admin-first-keyset-bootstrap.md)

## Intent

Establish an **Admin API–owned** bootstrap/upsert of the singleton `ezkey_keyset_blob` and of
`ezkey_encryption_key` metadata, so peripheral APIs (`ezkey_auth`, `ezkey_integration`) wait until
the blob exists, then run with **SELECT-only** grants. Tightening permissions is the *goal*; a
reliable **ordering and readiness** contract is the *prerequisite*.

## Problem and value

- **Problem:** After the PostgreSQL application-role split (`I-2026-0021` /
  `TB-2026-07-16`), `ezkey_encryption_key` is already SELECT-only for Auth/Integration, but shared
  `KeyRotationService.initializeKeysetSync()` still tries `STARTUP_SYNC` inserts on every boot API.
  `ezkey_keyset_blob` still had INSERT/UPDATE on peripherals because `TinkKeyManager` could upsert
  from any process. After TX-002 (2026-08-25) made empty-table sync `@Transactional`, a denied
  INSERT marks the transaction rollback-only and Spring kills Auth with
  `UnexpectedRollbackException`. Observed on clean-start 2026-08-28 when Auth won the empty-table
  race. Before TX-002 the same deny was logged and Auth stayed up (July 17 validation).
- **Expected value:** Clear ownership (Admin writes; peripherals read); DB grants match that story;
  no peripheral writer race; operators get a loud “encryption not ready yet” posture instead of a
  crash or a silent upsert.

## Grill locks (2026-08-28)

Closed during promotion. Do not reopen without a new signal.

1. **Writer authority.** New property `ezkey.encryption.keyset.writer` (default `false`). `true`
   only on Admin API. Do not overload `rotation.enabled`. Admin remains the sole materializer of
   both `ezkey_keyset_blob` and `ezkey_encryption_key`.
2. **Peripheral readiness.** DATABASE/HYBRID + `writer=false`: poll `loadKeysetFromDatabase()` up
   to `ezkey.encryption.keyset.bootstrap-wait` (default PT90S). Readiness is **blob readable + Tink
   initialized**, not `ezkey_encryption_key` count. Timeout + `required=true` → fail-closed.
   First boot (empty blob) waits; runtime restart with blob present loads immediately.
3. **Compose `depends_on: admin-api: service_healthy`** is clean-start **acceleration** only. HA /
   Auth-only restart must still wait in-process. Forced repro uses `docker compose up --no-deps`.
4. **Grants.** After the Java gate: Auth/Integration SELECT-only on `ezkey_keyset_blob`.
5. **Rejected:** ShedLock for a singleton wait; audit-chain heartbeat as the wait signal; infer
   writer from JDBC username; compose-only gating; lucky clean-start as the regression test.

## Scope

- **In scope:**
  - Admin-first keyset blob and encryption-key metadata materialization.
  - Peripheral in-process wait then fail-closed when `required`.
  - SELECT-only grants on `ezkey_keyset_blob` for `ezkey_auth` / `ezkey_integration`.
  - `TinkKeyManager` / `KeyRotationService` writer gate; peripherals never `saveKeysetToDatabase`
    or generate a new keyset on empty DB.
  - Docs, CONFIGURATION, matrix, `verify-grants.sh`, Layer 1 unit locks, Layer 2 Auth-first script.
- **Out of scope:**
  - Master-key file handling or Tink algorithms.
  - Multi-keyset / multi-tenant keyset blobs.
  - Crypto API (FILE, no DB).
  - Heartbeat redesign.

## Key assumptions

- Steady-state rotation remains Admin-driven; peripherals reload via version/SELECT (ADR-0008).
- Empty-DB upsert from Auth/Integration is a bootstrap race, not a required production path.
- Readiness failure must be loud (fail start when `required`), not a quiet encryption-disabled
  continuation that masks mis-ordering.

## Risks and exceptions

- Compose start-order only is fragile for HA or manual peripheral restart — in-process wait is the
  contract.
- FILE mode: peripherals must not generate a missing keyset; shared-volume “first process wins”
  stays a FILE-only local risk.
- Distinguish first boot (no blob) from runtime (blob already loaded / readable).

## Observed companion startup path (validation anchor)

**2026-07-17:** Auth attempted `STARTUP_SYNC`; Postgres denied INSERT on `ezkey_encryption_key`;
Auth continued. Not the cause of the separate mobile ECDSA high-S failures.

**2026-08-28:** Same deny after TX-002 killed Auth (`UnexpectedRollbackException`). Migration
OOM / delayed Admin made the empty-table race more likely. `restart: on-failure:3` can hide a
first death after Admin later seeds rows.

A normal clean-start is **not** a regression test. Forced proof is Auth-first (`--no-deps`) plus
CI unit locks. See the TB evidence plan.

## Promotion notes

Promoted 2026-08-28 after grill of readiness, failure semantics, Admin upsert authority, and grant
diff. Implementation rides [`TB-2026-08-28-admin-first-keyset-bootstrap`](../TB-2026-08-28-admin-first-keyset-bootstrap.md).

## Links

- Role matrix: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)
- Related TB: [`../TB-2026-07-16-postgresql-application-role-split.md`](../TB-2026-07-16-postgresql-application-role-split.md)
- Related idea (DB roles): [`I-2026-0021-postgresql-application-role-permissions-matrix.md`](I-2026-0021-postgresql-application-role-permissions-matrix.md)
- Related idea (keyset blob format): [`I-2026-07-26-tink-native-keyset-blob-envelope.md`](I-2026-07-26-tink-native-keyset-blob-envelope.md)
- Code: `TinkKeyManager`, `KeyRotationService.initializeKeysetSync()`, `TinkProperties.Keyset`
