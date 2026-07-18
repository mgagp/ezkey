# Backlog Idea — `I-2026-07-17` Keyset blob: Admin-first bootstrap before peripheral SELECT-only

## Metadata

- **ID:** `I-2026-07-17-keyset-blob-admin-first-bootstrap`
- **Status:** `captured`
- **Priority:** `P3`
- **Created at:** `2026-07-17`
- **Updated at:** `2026-07-17`
- **Last reviewed at:** `2026-07-17`
- **Phase tags:** `P3-future` (post–September-2026 R1 operable release)
- **Component tags:** `core`, `core-security`, `admin-api`, `auth-api`, `integration-api`, `infra`, `docs`, `crypto`
- **Lane:** `C`
- **Captured by:** Marc (operator review of PostgreSQL role grants on `ezkey_keyset_blob`)

## Intent

Establish an **Admin API–owned** bootstrap/upsert of the singleton `ezkey_keyset_blob`, so
peripheral APIs (`ezkey_auth`, `ezkey_integration`) can wait until the blob exists, then run with
**SELECT-only** grants. Tightening permissions is the *goal*; a reliable **ordering and readiness**
contract is the *prerequisite*.

## Problem and value

- **Problem:** After the PostgreSQL application-role split (`I-2026-0021` /
  `TB-2026-07-16`), all three runtime roles receive **SELECT / INSERT / UPDATE** on
  `ezkey_keyset_blob`. Steady-state sync only needs **SELECT** (version check + reload when Admin
  rotates). The write grants exist because the shared `TinkKeyManager` startup path can
  **upsert** the blob from file (`STARTUP_FILE_SYNC`) or create a new keyset
  (`STARTUP_NEW_KEYSET`) on *any* boot API that finds an empty DB. That is accidental shared-writer
  behaviour, not a deliberate trust boundary: Auth/Integration should consume the keyset, not
  become its producer because they started first.
- **Expected value:** Clear ownership (Admin API / migrate+admin path writes; peripherals read);
  DB grants match that story; no race where a peripheral seeds a divergent keyset; operators get a
  predictable “encryption not ready yet” posture instead of silent peripheral upsert.

## Scope

- **In scope (when promoted):**
  - Design an **Admin-first** keyset blob materialization (who writes, when, idempotency).
  - Design a **peripheral readiness** mechanism so Auth/Integration do not enter full operational
    MFA mode until the blob is readable (or until a bounded wait fails with a clear operator
    signal). Candidate approaches to grill (non-exhaustive, decide later):
    - short poll loop (e.g. sleep 1s, capped retries) then fail-closed / degraded;
    - ShedLock or similar coordination only if it actually fits (likely overkill for “wait for
      singleton row”);
    - Docker/compose dependency / health gating so peripherals start after Admin has healthy
      encryption;
    - other readiness probe identified during design.
  - After readiness is solid: tighten `apply-grants.sql` / matrix to **SELECT-only** for
    `ezkey_auth` and `ezkey_integration` on `ezkey_keyset_blob`; keep INSERT/UPDATE on
    `ezkey_admin` (and migrate owner as today).
  - Adjust `TinkKeyManager` (or call sites) so peripheral processes **do not** call
    `saveKeysetToDatabase` on empty DB; Admin remains the upsert authority.
  - Docs: `DATABASE_ROLE_PERMISSIONS_MATRIX.md`, encryption/CONFIGURATION notes, Docker start
    ordering if used.
- **Out of scope (for this idea):**
  - Changing master-key file handling or Tink algorithms.
  - Multi-keyset / multi-tenant keyset blobs.
  - Pulling this into the September 2026 R1 critical path.
  - Treating current INSERT/UPDATE on peripherals as a production incident — grants match today’s
    shared startup code; this idea is deliberate hardening.

## Key assumptions

- Steady-state rotation remains Admin-driven; peripherals already reload via version/SELECT.
- Empty-DB upsert from Auth/Integration is a **bootstrap race**, not a required production path
  once Admin has initialized once.
- Readiness failure must be **loud** (fail start or fail-closed encryption), not a quiet
  “encryption disabled” continuation that masks mis-ordering.

## Risks and exceptions

- Compose/start-order only is fragile for HA or manual restarts of a single peripheral — need an
  in-process wait or health contract, not only Docker `depends_on`.
- Over-engineering (ShedLock for a singleton wait) vs under-engineering (infinite wait, silent
  disable).
- HYBRID/FILE storage modes must remain coherent; grant tightening applies to DATABASE/HYBRID
  paths that touch the blob table.
- If Admin is down and peripherals must keep decrypting with an already-loaded in-memory keyset,
  distinguish **first boot** (no blob yet) from **runtime** (blob already loaded).

## Observed companion startup path (validation anchor)

During role-split validation on 2026-07-17, Auth API started with `ezkey_auth` and the shared
`KeyRotationService.initializeKeysetSync()` observed an empty `ezkey_encryption_key` metadata
table. It then attempted `STARTUP_SYNC` inserts and PostgreSQL correctly rejected them:
`permission denied for table ezkey_encryption_key`. Auth API continued running, and this event was
**not** the cause of the separately observed mobile ECDSA high-S pending/respond failures.

This is nevertheless a concrete example of the same bootstrap-ownership issue: peripheral APIs
should not try to materialize either the keyset blob **or its relational key metadata**. The future
design and tests must therefore cover both:

- cold start with empty `ezkey_keyset_blob` and empty `ezkey_encryption_key`;
- Admin API becomes the sole bootstrap/upsert authority for both representations;
- Auth/Integration wait or fail readiness clearly, without attempted INSERT/UPDATE;
- after Admin readiness, peripherals load the keyset and serve cryptographic flows with read-only
  access;
- restart and HA ordering do not reintroduce a peripheral writer race;
- logs contain no expected-but-ignored `permission denied` during a healthy startup.

## Promotion notes

Keep `captured` / `P3`. Move to `incubating` when encryption/ops ordering becomes a real
deployment friction or when a grant-hardening pass is scheduled. Promote to `ready` + `TB-*` only
after grill of: readiness mechanism, failure semantics, Admin upsert authority, and exact grant
diff. Do **not** remove peripheral INSERT/UPDATE before the readiness path exists.

## Links

- Surfaced during: operator review of `ezkey_auth` grants on `ezkey_keyset_blob` after role split
- Role matrix: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)
- Related TB: [`../TB-2026-07-16-postgresql-application-role-split.md`](../TB-2026-07-16-postgresql-application-role-split.md)
- Related idea (DB roles): [`I-2026-0021-postgresql-application-role-permissions-matrix.md`](I-2026-0021-postgresql-application-role-permissions-matrix.md)
- Sibling open follow-up on same TB: audit-log HMAC INSERT-only redesign (preferred sequence allocate)
- Code: `org.ezkey.security.TinkKeyManager` (`saveKeysetToDatabase`, startup `STARTUP_FILE_SYNC` /
  `STARTUP_NEW_KEYSET`); entity `KeysetBlob` (singleton id=1)
