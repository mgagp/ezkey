# Backlog Idea — `I-2026-07-18` Audit-log peripheral INSERT-only via sequence pre-allocation for the HMAC seal

## Metadata

- **ID:** `I-2026-07-18-audit-log-peripheral-insert-only-hmac-sequence`
- **Status:** `ready`
- **Priority:** `P2`
- **Created at:** `2026-07-18`
- **Updated at:** `2026-07-18`
- **Last reviewed at:** `2026-07-18`
- **Progression markers:** `P2-hardening`
- **Component tags:** `core`, `audit`, `admin-api`, `auth-api`, `integration-api`, `infra`, `docs`
- **Lane:** `D`
- **Captured by:** Marc (post-delivery hardening extracted from `TB-2026-07-16` follow-up analysis)

## Intent

Remove the `UPDATE` grant on `ezkey_audit_log` from the peripheral runtime roles (`ezkey_auth`,
`ezkey_integration`) by replacing the current two-step HMAC seal (INSERT to obtain the
database-assigned `audit_log_id`, then UPDATE `entry_hmac`) with a **single INSERT** that already
carries the identity and the computed HMAC. The `audit_log_id` is obtained from its sequence
**before** the insert, so the identity can be included in the signed canonical form without a
post-insert mutation. This restores the original least-privilege intent of the role split
(peripherals can append audit rows but never mutate them) while also removing accidental complexity
from the write path.

## Problem and value

- **Problem:** During `TB-2026-07-16` (PostgreSQL application-role split), the audit-log grant for
  `ezkey_auth` / `ezkey_integration` had to be widened from the intended **INSERT-only** to
  **INSERT + UPDATE**. The reason is the seal in
  [`AuditLogService.log()`](../../../../ezkey-core/src/main/java/org/ezkey/audit/service/AuditLogService.java):
  1. `save()` — INSERT so the database assigns `audit_log_id` (`GENERATED ALWAYS AS IDENTITY`);
  2. `entityManager.refresh(saved)` — re-read `created_at` at PostgreSQL microsecond precision;
  3. `computeHmac(saved)` — build the canonical form (field 1 = `audit_log_id`, field 13 =
     `created_at`);
  4. `save()` — UPDATE to persist `entry_hmac`.
  Because the identity is only known after the first INSERT, and because `created_at` must match the
  stored value exactly, the design currently needs a mutating second write. This weakens the
  "peripherals only append, never mutate audit rows" story at the database boundary.
- **Expected value (double pay-off):**
  1. **Tighter grants:** peripheral roles drop to **SELECT + INSERT** on `ezkey_audit_log`
     (DELETE already denied; only `ezkey_admin` retains DELETE for lifecycle purge). The audit
     immutability narrative for operators and reviewers matches the enforced grants.
  2. **Less accidental complexity:** a single INSERT replaces INSERT + refresh + UPDATE, which is
     simpler and cheaper on a hot, partitioned write path.

## Scope

- **In scope (when promoted):**
  - Pre-allocate `audit_log_id` from its sequence before building the canonical form.
  - Make the application **own** `created_at` (truncated to microseconds) so the signed value equals
    the stored value without a post-insert `refresh` — see design note below.
  - Compute the HMAC, then perform a **single INSERT** with `audit_log_id`, `created_at`, and
    `entry_hmac` already set, inside the existing `REQUIRES_NEW` transaction (atomicity preserved).
  - Whatever migration is required so the identity column accepts an application-supplied value
    (see "Design analysis" — `GENERATED ALWAYS` nuance). Greenfield-friendly (dev drops tables).
  - Tighten `scripts/db/apply-grants.sql` and
    [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md)
    to **SELECT + INSERT** for `ezkey_auth` / `ezkey_integration` on `ezkey_audit_log`.
  - Update `scripts/db/verify-grants.sh` to assert peripheral **UPDATE is now forbidden** on
    `ezkey_audit_log`.
  - Preserve the exact HMAC canonical form and existing integrity verification (no change to
    `AuditHmacService.buildCanonicalForm`).
- **Out of scope:**
  - Changing the HMAC algorithm, key handling, or the canonical field order.
  - Moving HMAC computation into the database (trigger / PL) — explicitly rejected direction.
  - Delegating the seal to a batch or a separate admin-api pass (introduces a detection-gap window).
  - `ezkey_keyset_blob` Admin-first bootstrap (separate idea
    [`I-2026-07-17-keyset-blob-admin-first-bootstrap`](I-2026-07-17-keyset-blob-admin-first-bootstrap.md)).
  - Alert retention / purge (separate idea
    [`I-2026-07-17-alert-resolved-retention-purge`](I-2026-07-17-alert-resolved-retention-purge.md)).
  - `ezkey_admin` grants on `ezkey_audit_log` (it keeps INSERT/UPDATE/DELETE for lifecycle purge
    and any admin-side sealing path).

## Design analysis (2026-07-18, to be confirmed by grill)

**Operator observation to validate:** "the sequence already exists — maybe only the way the SQL is
expressed in the service is the problem" (à la MyBatis `<selectKey>` fetching `nextval` before the
insert). Verified against the code and migrations:

1. **The sequence does exist.** `ezkey_audit_log.audit_log_id` is
   `BIGINT GENERATED ALWAYS AS IDENTITY` (migration V4, formerly "V24"). PostgreSQL backs that with
   an implicit sequence in schema `public`, and `apply-grants.sql` already grants
   `USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public` to all three runtime roles. So the operator is
   right: the sequence is present and (probably) already grantable to peripherals.
2. **But `GENERATED ALWAYS` blocks explicit inserts.** Reusing the sequence "the MyBatis way"
   (fetch `nextval`, then INSERT the value) is not purely a service-layer rewrite: a
   `GENERATED ALWAYS AS IDENTITY` column **rejects** an application-supplied id unless the insert
   uses `OVERRIDING SYSTEM VALUE`, or the column is redefined as `GENERATED BY DEFAULT AS IDENTITY`
   (or a plain named sequence with `DEFAULT nextval(...)`). This is the honest delta versus "just
   change the SQL." It is a small greenfield migration, not a business-logic change.
3. **JPA strategy.** `GenerationType.IDENTITY` never pre-fetches the id (it relies on the DB at
   insert and reads it back), so it cannot support the single-INSERT-with-known-id pattern. The
   candidate is `GenerationType.SEQUENCE` with an explicit `@SequenceGenerator` (Hibernate obtains
   the id via `nextval` and populates it in memory **before** the INSERT), or a fully manual
   `SELECT nextval(...)` + assigned-id insert. Either way the column must accept explicit values
   (point 2).
4. **`created_at` precision is the second reason for the current second write.** The existing code
   does `entityManager.refresh(saved)` before signing because Java `OffsetDateTime.now()` carries
   sub-microsecond nanoseconds while PostgreSQL stores microseconds; the two can differ (Java
   truncates, PostgreSQL rounds), which previously failed ~50% of verifications. For a single INSERT
   with no refresh, the application must **set `created_at` itself, already truncated to
   microseconds** (`AuditHmacService.formatTimestamp` already truncates to `MICROS`), and insert
   that exact value instead of relying on the DB `DEFAULT CURRENT_TIMESTAMP`. Then stored == signed
   with no read-back.
5. **Atomicity is non-negotiable.** The current two-step save is atomic (single
   `PROPAGATION_REQUIRES_NEW` transaction): a row is either fully present and sealed, or absent. The
   redesign must keep allocate-id → compute-HMAC → single-INSERT inside the same `REQUIRES_NEW`
   transaction. Unused sequence values on rollback are normal and acceptable for audit ids.
6. **Partitioned table.** `ezkey_audit_log` is composite-partitioned (RANGE `created_at` by month,
   LIST `api_name`). The identity/sequence lives on the parent and works across partitions; confirm
   the sequence name resolution and that peripheral `USAGE, SELECT` covers it after any migration.

**Preliminary conclusion:** the operator's hypothesis holds — the same DB sequence can be reused,
simply obtained upstream and combined with an app-owned `created_at` so that INSERT replaces
INSERT + UPDATE — provided we also (a) allow the identity column to accept an explicit value and
(b) own the timestamp precision in the application. Both are bounded and greenfield-safe.

## Key assumptions

- Greenfield migration is acceptable (clean-start / EXP1 recreate the volume; no in-place data
  migration required for R1).
- The HMAC canonical form and verification stay byte-for-byte identical; only the *when/how* the id
  and timestamp are obtained changes, not *what* is signed.
- `ezkey_admin` keeps its current audit-log grants; only peripheral roles are tightened.
- Removing the second write does not regress nightly/retroactive integrity validation, chain
  checkpoints, or entry conciliation (they read `entry_hmac` and the canonical fields, which are
  unchanged).

## Risks and exceptions

- **Silent verification regression:** a mismatch between app-owned `created_at` and the stored value
  would fail HMAC verification. Mitigation: explicit micros truncation + a round-trip
  characterization test (sign in app, read back from DB, verify) before removing the refresh.
- **Sequence/grant edge cases** on the partitioned parent (implicit vs named sequence, `USAGE`
  coverage). Mitigation: `verify-grants.sh` positive (INSERT works) and negative (UPDATE denied)
  assertions for peripheral roles.
- **`GENERATED ALWAYS` migration scope creep:** keep the migration to the identity definition only;
  do not restructure the table.
- **Over-engineering guardrail** (from `TB-2026-07-16`): if validation shows the single-INSERT path
  compromises atomicity, needs DB-side crypto, or opens a detection-gap window, prefer the current
  atomic two-step seal as the pragmatic baseline rather than forcing INSERT-only as dogma.

## Settled design (grill 2026-07-18)

Grill session
[`../grill-sessions/2026-07-18-audit-log-insert-only-hmac-sequence-grill-me.md`](../grill-sessions/2026-07-18-audit-log-insert-only-hmac-sequence-grill-me.md)
resolved all open questions (G1–G10):

- **Identity:** `audit_log_id` → `GENERATED BY DEFAULT AS IDENTITY`, JPA `GenerationType.SEQUENCE`.
- **Sequence:** explicit **named** sequence in the Flyway baseline (owned by `ezkey_migrate`),
  `allocationSize = 1`.
- **Timestamp:** application owns `created_at` (truncated to microseconds, inserted explicitly); no
  `refresh`, no `RETURNING`.
- **Seal:** single INSERT with id + `created_at` + `entry_hmac` inside the existing `REQUIRES_NEW`
  transaction; canonical form unchanged; atomicity preserved.
- **Scope:** uniform sealing path across all three APIs; `ezkey_admin` keeps INSERT/UPDATE/DELETE
  (lifecycle purge), peripherals drop to SELECT + INSERT.
- **Guard:** mandatory characterization test (sign via new path → read back → `verifyHmac`).
- **Single-writer confirmed:** `AuditLogService.log()` is the only INSERT/UPDATE into
  `ezkey_audit_log`.

## Test posture (settled 2026-07-18)

Canonical HMAC form is unchanged, so existing crypto unit tests (`AuditHmacServiceTest`, incl. the
sub-microsecond truncation/rounding cases) stay valid. The only new risks are the write-path
refactor invariant and the grant boundary. **Critical nuance:** `AuditLogService.log()` swallows
exceptions silently, so a seal/grant regression does not fail an API call — it only appears as
missing or unsigned audit rows. Posture:

- **New unit test** (Mockito) on `AuditLogService.log()`: exactly one `save()`, no
  `entityManager.refresh(...)`, id + micros-truncated `created_at` set before HMAC compute.
- **Real-DB round trip** via the existing functional elective `AuditIntegrityElectiveTest`
  (Docker, real PostgreSQL) — **no Testcontainers** added to `ezkey-core` (stay within the stack).
- **Elevated gate:** functional suite + `AuditIntegrityElectiveTest` on a clean-start stack under
  tightened grants is a **required** closeout gate for this slice (not on-demand only), because of
  the silent-swallow behavior.
- **Grant guard:** `verify-grants.sh` negative assertion (peripheral UPDATE on `ezkey_audit_log`
  denied) + positive INSERT.
- **Integrity regression:** nightly/retroactive/chain/conciliation suites unchanged.

## Promotion notes

Promoted to `ready` on 2026-07-18. Execution slice:
[`../TB-2026-07-18-audit-log-insert-only-hmac-seal.md`](../TB-2026-07-18-audit-log-insert-only-hmac-seal.md).

## Links

- Parent TB (source of this follow-up): [`../TB-2026-07-16-postgresql-application-role-split.md`](../TB-2026-07-16-postgresql-application-role-split.md) § Follow-up analysis
- Parent idea (role/permission matrix): [`I-2026-0021-postgresql-application-role-permissions-matrix.md`](I-2026-0021-postgresql-application-role-permissions-matrix.md)
- Role matrix: [`docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md`](../../../../docs/DATABASE_ROLE_PERMISSIONS_MATRIX.md) (`ezkey_audit_log` row + Explicit exceptions #1)
- Sibling follow-up on same TB: [`I-2026-07-17-keyset-blob-admin-first-bootstrap.md`](I-2026-07-17-keyset-blob-admin-first-bootstrap.md)
- Sibling (orthogonal — delivery, not seal/grants): [`I-2026-07-18-audit-log-fail-open-exception-swallow.md`](I-2026-07-18-audit-log-fail-open-exception-swallow.md) — `log()` fail-open / exception swallow; out of scope for the INSERT-only TB
- Related integrity vision: `V-2026-0004` (integrity validation strategy)
- Code: `org.ezkey.audit.service.AuditLogService` (`log()` two-step seal), `org.ezkey.audit.integrity.AuditHmacService` (`buildCanonicalForm`, `formatTimestamp`), entity `org.ezkey.audit.domain.entity.AuditLog` (`@GeneratedValue(strategy = IDENTITY)`)
- Migration: `ezkey-core/src/main/resources/db/migration/V4__partitioning_auth_audit_and_function.sql` (`audit_log_id BIGINT GENERATED ALWAYS AS IDENTITY`)
- Grants: `scripts/db/apply-grants.sql`, `scripts/db/verify-grants.sh`
